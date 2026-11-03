package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.brix.campus.common.BusinessException;
import com.brix.campus.service.BrowseHistoryService;
import com.brix.campus.mapper.CategoryMapper;
import com.brix.campus.mapper.FavoriteMapper;
import com.brix.campus.mapper.ItemImageMapper;
import com.brix.campus.mapper.ItemMapper;
import com.brix.campus.mapper.OrderMapper;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.Category;
import com.brix.campus.model.Favorite;
import com.brix.campus.model.Item;
import com.brix.campus.model.ItemImage;
import com.brix.campus.model.Order;
import com.brix.campus.model.User;
import com.brix.campus.model.dto.ItemSaveDTO;
import com.brix.campus.model.dto.ItemStatusDTO;
import com.brix.campus.model.enums.ConditionEnum;
import com.brix.campus.model.enums.ItemStatusEnum;
import com.brix.campus.model.enums.OrderStatusEnum;
import com.brix.campus.model.enums.TradeTypeEnum;
import com.brix.campus.model.query.BaseQuery;
import com.brix.campus.model.query.ItemQuery;
import com.brix.campus.model.vo.ItemDetailVO;
import com.brix.campus.model.vo.ItemImageVO;
import com.brix.campus.model.vo.ItemVO;
import com.brix.campus.model.vo.SellerVO;
import com.brix.campus.service.ItemService;
import com.brix.campus.service.PublishAuditService;import com.brix.campus.util.ViewCountDedupCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品服务实现。
 *
 * <p>状态机约束：{@code ON_SALE → OFF_SHELF}（主动下架）、
 * {@code OFF_SHELF → ON_SALE}（重新上架）、编辑保持上架、{@code SOLD} 不可编辑/上下架。</p>
 */
@Service
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements ItemService {

    /** 商品图片上限 */
    private static final int MAX_IMAGES = 9;
    /** 进行中的订单状态 */
    private static final List<String> ONGOING_ORDER_STATUS = List.of(
            OrderStatusEnum.UNPAID.getCode(),
            OrderStatusEnum.TO_SHIP.getCode(),
            OrderStatusEnum.TO_RECEIVE.getCode(),
            OrderStatusEnum.TO_MEET.getCode());

    private final ItemImageMapper itemImageMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final FavoriteMapper favoriteMapper;
    private final OrderMapper orderMapper;
    private final ViewCountDedupCache viewCountDedupCache;
    private final PublishAuditService publishAuditService;
    private final BrowseHistoryService browseHistoryService;

    public ItemServiceImpl(ItemImageMapper itemImageMapper,
                           CategoryMapper categoryMapper,
                           UserMapper userMapper,
                           FavoriteMapper favoriteMapper,
                           OrderMapper orderMapper,
                           ViewCountDedupCache viewCountDedupCache,
                           PublishAuditService publishAuditService,
                           BrowseHistoryService browseHistoryService) {
        this.itemImageMapper = itemImageMapper;
        this.categoryMapper = categoryMapper;
        this.userMapper = userMapper;
        this.favoriteMapper = favoriteMapper;
        this.orderMapper = orderMapper;
        this.viewCountDedupCache = viewCountDedupCache;
        this.publishAuditService = publishAuditService;
        this.browseHistoryService = browseHistoryService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ItemVO create(Long sellerId, ItemSaveDTO dto) {
        validateSaveDTO(dto);
        // 发布合规两道闸：违禁词黑名单 + AI 审核员复审（不合规直接抛异常，商品不落库）
        publishAuditService.check(dto.getTitle(), dto.getDescription(), categoryNameOf(dto.getCategoryId()));

        Item item = new Item();
        item.setTitle(dto.getTitle().trim());
        item.setDescription(dto.getDescription().trim());
        item.setCategoryId(dto.getCategoryId());
        item.setPrice(dto.getPrice());
        item.setOriginalPrice(dto.getOriginalPrice());
        item.setConditionLevel(dto.getConditionLevel());
        item.setCampus(dto.getCampus().trim());
        item.setTradeType(dto.getTradeType());
        item.setContact(dto.getContact() == null ? null : dto.getContact().trim());
        item.setBrandModel(dto.getBrandModel() == null ? null : dto.getBrandModel().trim());
        item.setStatus(ItemStatusEnum.ON_SALE.getCode());
        item.setCoverImage("");
        item.setViewCount(0);
        item.setFavoriteCount(0);
        item.setSellerId(sellerId);
        save(item);

        List<String> images = dto.getImages() == null ? Collections.emptyList() : dto.getImages();
        replaceImages(item.getId(), images);
        item.setCoverImage(images.isEmpty() ? "" : images.get(0));
        updateById(item);

        return toVO(item);
    }

    @Override
    public IPage<ItemVO> pageForFront(ItemQuery query) {
        Page<Item> page = new Page<>(pageOf(query), sizeOf(query));
        LambdaQueryWrapper<Item> wrapper = Wrappers.<Item>lambdaQuery()
                .eq(Item::getStatus, ItemStatusEnum.ON_SALE.getCode());
        applyFilters(wrapper, query);
        applySort(wrapper, query.getSort());
        IPage<Item> itemPage = baseMapper.selectPage(page, wrapper);
        return convertPage(itemPage);
    }

    @Override
    public ItemDetailVO detail(Long itemId, Long viewerId, String viewerKey) {
        Item item = getEntity(itemId);
        if (item == null) {
            throw BusinessException.notFound("商品不存在");
        }
        boolean owner = viewerId != null && viewerId.equals(item.getSellerId());
        boolean hidden = ItemStatusEnum.PENDING.getCode().equals(item.getStatus())
                || ItemStatusEnum.REJECTED.getCode().equals(item.getStatus());
        if (hidden && !owner) {
            throw BusinessException.notFound("商品不存在或尚未上架");
        }

        // 浏览量 +1（同一访问者 10 分钟内不重复计数）
        if (viewCountDedupCache.tryAcquire(viewerKey, itemId)) {
            baseMapper.update(null, new LambdaUpdateWrapper<Item>()
                    .eq(Item::getId, itemId)
                    .setSql("view_count = view_count + 1"));
            item.setViewCount(item.getViewCount() == null ? 1 : item.getViewCount() + 1);
        }

        // 登录用户记录浏览历史（同一商品仅保留一条，重复浏览刷新时间；失败不影响主流程）
        if (viewerId != null) {
            browseHistoryService.record(viewerId, itemId);
        }

        ItemDetailVO vo = new ItemDetailVO();
        copyBase(item, vo);
        vo.setDescription(item.getDescription());
        vo.setContact(item.getContact());
        vo.setImages(listImages(itemId));

        User seller = userMapper.selectById(item.getSellerId());
        if (seller != null) {
            SellerVO sellerVO = new SellerVO();
            sellerVO.setId(seller.getId());
            sellerVO.setNickname(seller.getNickname());
            sellerVO.setAvatar(seller.getAvatar());
            sellerVO.setCampus(seller.getCampus());
            sellerVO.setCreditScore(seller.getCreditScore());
            vo.setSeller(sellerVO);
            vo.setSellerNickname(seller.getNickname());
        }
        Category category = categoryMapper.selectById(item.getCategoryId());
        if (category != null) {
            vo.setCategoryName(displayCategoryName(category, null));
        }
        vo.setIsOwner(owner);
        if (viewerId != null) {
            Long favoriteCount = favoriteMapper.selectCount(Wrappers.<Favorite>lambdaQuery()
                    .eq(Favorite::getUserId, viewerId)
                    .eq(Favorite::getItemId, itemId));
            vo.setIsFavorite(favoriteCount != null && favoriteCount > 0);
        }
        return vo;
    }

    @Override
    public IPage<ItemVO> pageMine(Long sellerId, ItemQuery query) {
        Page<Item> page = new Page<>(pageOf(query), sizeOf(query));
        LambdaQueryWrapper<Item> wrapper = Wrappers.<Item>lambdaQuery()
                .eq(Item::getSellerId, sellerId);
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(Item::getStatus, query.getStatus());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Item::getTitle, keyword).or().like(Item::getDescription, keyword));
        }
        wrapper.orderByDesc(Item::getCreatedAt).orderByDesc(Item::getId);
        return convertPage(baseMapper.selectPage(page, wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long sellerId, Long itemId, ItemSaveDTO dto) {
        Item item = assertOwner(itemId, sellerId);
        if (ItemStatusEnum.SOLD.getCode().equals(item.getStatus())) {
            throw BusinessException.param("已售出商品不可编辑");
        }
        validateSaveDTO(dto);
        // 编辑同样过合规闸（防止改内容夹带违规信息）
        publishAuditService.check(dto.getTitle(), dto.getDescription(), categoryNameOf(dto.getCategoryId()));

        item.setTitle(dto.getTitle().trim());
        item.setDescription(dto.getDescription().trim());
        item.setCategoryId(dto.getCategoryId());
        item.setPrice(dto.getPrice());
        item.setOriginalPrice(dto.getOriginalPrice());
        item.setConditionLevel(dto.getConditionLevel());
        item.setCampus(dto.getCampus().trim());
        item.setTradeType(dto.getTradeType());
        item.setContact(dto.getContact() == null ? null : dto.getContact().trim());
        item.setBrandModel(dto.getBrandModel() == null ? null : dto.getBrandModel().trim());
        // 编辑后保持上架状态
        item.setStatus(ItemStatusEnum.ON_SALE.getCode());

        List<String> images = dto.getImages() == null ? Collections.emptyList() : dto.getImages();
        item.setCoverImage(images.isEmpty() ? "" : images.get(0));
        updateById(item);
        replaceImages(item.getId(), images);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long sellerId, Long itemId) {
        Item item = assertOwner(itemId, sellerId);
        Long ongoing = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getItemId, item.getId())
                .in(Order::getStatus, ONGOING_ORDER_STATUS));
        if (ongoing != null && ongoing > 0) {
            throw BusinessException.param("有进行中的订单，无法删除");
        }
        removeById(itemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ItemVO updateStatus(Long sellerId, Long itemId, ItemStatusDTO dto) {
        Item item = assertOwner(itemId, sellerId);
        String action = dto.getAction() == null ? "" : dto.getAction().trim();

        if (ItemStatusEnum.SOLD.getCode().equals(item.getStatus())) {
            throw BusinessException.param("当前商品状态不支持该操作");
        }
        if (ItemStatusEnum.ON_SALE.getCode().equals(action)) {
            if (!ItemStatusEnum.OFF_SHELF.getCode().equals(item.getStatus())) {
                throw BusinessException.param("当前商品状态不支持该操作");
            }
            // 重新上架
            item.setStatus(ItemStatusEnum.ON_SALE.getCode());
        } else if (ItemStatusEnum.OFF_SHELF.getCode().equals(action)) {
            if (!ItemStatusEnum.ON_SALE.getCode().equals(item.getStatus())) {
                throw BusinessException.param("当前商品状态不支持该操作");
            }
            item.setStatus(ItemStatusEnum.OFF_SHELF.getCode());
        } else {
            throw BusinessException.param("当前商品状态不支持该操作");
        }
        updateById(item);
        return toVO(item);
    }

    @Override
    public Item getEntity(Long itemId) {
        if (itemId == null) {
            return null;
        }
        return getById(itemId);
    }

    @Override
    public List<ItemVO> listSimilar(Long itemId, int limit) {
        Item item = getEntity(itemId);
        if (item == null) {
            return Collections.emptyList();
        }
        Category category = categoryMapper.selectById(item.getCategoryId());
        Long parentId = category == null ? null : category.getParentId();

        // 1) 同子类优先（按浏览量倒序）
        List<Item> picked = new ArrayList<>(baseMapper.selectList(Wrappers.<Item>lambdaQuery()
                .eq(Item::getStatus, ItemStatusEnum.ON_SALE.getCode())
                .eq(Item::getCategoryId, item.getCategoryId())
                .ne(Item::getId, itemId)
                .orderByDesc(Item::getViewCount)
                .orderByDesc(Item::getId)
                .last("LIMIT " + limit)));

        // 2) 不足 limit 件时，用同父类其他子类（含父类直属商品）补足
        if (picked.size() < limit && parentId != null) {
            Set<Long> excludeIds = picked.stream().map(Item::getId).collect(Collectors.toSet());
            excludeIds.add(itemId);
            List<Long> categoryIds = new ArrayList<>();
            if (parentId != 0L) {
                // 当前是子类 → 补充父类下所有子类 + 父类直属 + 父类自身
                categoryIds = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                                .eq(Category::getParentId, parentId))
                        .stream().map(Category::getId).collect(Collectors.toList());
                categoryIds.add(parentId);
            } else {
                // 当前是一级分类 → 补充其子类
                categoryIds = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                                .eq(Category::getParentId, item.getCategoryId()))
                        .stream().map(Category::getId).collect(Collectors.toList());
            }
            categoryIds.add(item.getCategoryId());
            List<Item> fill = baseMapper.selectList(Wrappers.<Item>lambdaQuery()
                    .eq(Item::getStatus, ItemStatusEnum.ON_SALE.getCode())
                    .in(Item::getCategoryId, categoryIds)
                    .notIn(Item::getId, excludeIds)
                    .orderByDesc(Item::getViewCount)
                    .orderByDesc(Item::getId)
                    .last("LIMIT " + (limit - picked.size())));
            picked.addAll(fill);
        }

        return convertList(picked);
    }

    /**
     * 实体列表转 VO 列表。
     *
     * @param items 实体列表
     * @return VO 列表
     */
    private List<ItemVO> convertList(List<Item> items) {
        List<ItemVO> vos = new ArrayList<>(items.size());
        for (Item entity : items) {
            vos.add(toVO(entity));
        }
        return vos;
    }

    @Override
    public ItemVO toVO(Item item) {
        if (item == null) {
            return null;
        }
        ItemVO vo = new ItemVO();
        copyBase(item, vo);
        Category category = categoryMapper.selectById(item.getCategoryId());
        if (category != null) {
            vo.setCategoryName(displayCategoryName(category, null));
        }
        User seller = userMapper.selectById(item.getSellerId());
        if (seller != null) {
            vo.setSellerNickname(seller.getNickname());
        }
        return vo;
    }

    /* --------------------------- 内部工具方法 --------------------------- */

    /**
     * 校验归属并返回商品实体。
     *
     * @param itemId   商品 ID
     * @param sellerId 卖家 ID
     * @return 商品实体
     */
    private Item assertOwner(Long itemId, Long sellerId) {
        Item item = getEntity(itemId);
        if (item == null) {
            throw BusinessException.notFound("商品不存在");
        }
        if (!item.getSellerId().equals(sellerId)) {
            throw BusinessException.forbidden("无权操作该商品");
        }
        return item;
    }

    /**
     * 查分类名称（合规审核提示词用，查不到给占位文案）。
     *
     * @param categoryId 分类 ID
     * @return 分类名称
     */
    private String categoryNameOf(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        return category == null ? "未知分类" : category.getName();
    }

    /**
     * 校验发布 / 编辑入参的业务规则（枚举、价格、图片数量）。
     *
     * @param dto 入参
     */
    private void validateSaveDTO(ItemSaveDTO dto) {
        if (!ConditionEnum.isValid(dto.getConditionLevel())) {
            throw BusinessException.param("成色不合法");
        }
        if (!TradeTypeEnum.isValid(dto.getTradeType())) {
            throw BusinessException.param("交易方式不合法");
        }
        if (categoryMapper.selectById(dto.getCategoryId()) == null) {
            throw BusinessException.param("分类不存在");
        }
        BigDecimal price = dto.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0
                || price.compareTo(new BigDecimal("99999.99")) > 0) {
            throw BusinessException.param("价格必须大于 0 且不超过 99999.99");
        }
        if (dto.getOriginalPrice() != null && dto.getOriginalPrice().compareTo(price) < 0) {
            throw BusinessException.param("原价不能低于现价");
        }
        if (dto.getImages() != null && dto.getImages().size() > MAX_IMAGES) {
            throw BusinessException.param("商品图片最多 9 张");
        }
    }

    /**
     * 应用筛选条件。
     *
     * @param wrapper 查询条件
     * @param query   入参
     */
    private void applyFilters(LambdaQueryWrapper<Item> wrapper, ItemQuery query) {
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Item::getTitle, keyword).or().like(Item::getDescription, keyword));
        }
        if (query.getCategoryId() != null) {
            // 级联分类：选一级分类时需包含其全部子类下的商品
            List<Long> categoryIds = new ArrayList<>();
            categoryIds.add(query.getCategoryId());
            List<Category> children = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                    .eq(Category::getParentId, query.getCategoryId()));
            for (Category child : children) {
                categoryIds.add(child.getId());
            }
            wrapper.in(Item::getCategoryId, categoryIds);
        }
        if (query.getCampus() != null && !query.getCampus().isBlank()) {
            wrapper.eq(Item::getCampus, query.getCampus().trim());
        }
        if (query.getMinPrice() != null) {
            wrapper.ge(Item::getPrice, query.getMinPrice());
        }
        if (query.getMaxPrice() != null) {
            wrapper.le(Item::getPrice, query.getMaxPrice());
        }
    }

    /**
     * 应用排序（必须带唯一列兜底，避免翻页重复/丢数据）。
     *
     * @param wrapper 查询条件
     * @param sort    排序方式
     */
    private void applySort(LambdaQueryWrapper<Item> wrapper, String sort) {
        String mode = sort == null ? "latest" : sort.trim();
        switch (mode) {
            case "price_asc" -> wrapper.orderByAsc(Item::getPrice).orderByDesc(Item::getId);
            case "price_desc" -> wrapper.orderByDesc(Item::getPrice).orderByDesc(Item::getId);
            case "hot" -> wrapper.orderByDesc(Item::getViewCount)
                    .orderByDesc(Item::getFavoriteCount)
                    .orderByDesc(Item::getId);
            default -> wrapper.orderByDesc(Item::getCreatedAt).orderByDesc(Item::getId);
        }
    }

    /**
     * 分页结果转 VO 并批量补齐分类名与卖家昵称。
     *
     * @param itemPage 实体分页
     * @return VO 分页
     */
    private IPage<ItemVO> convertPage(IPage<Item> itemPage) {
        List<Item> records = itemPage.getRecords();
        Map<Long, String> categoryNames = new HashMap<>();
        Map<Long, String> sellerNames = new HashMap<>();
        if (!records.isEmpty()) {
            Set<Long> categoryIds = records.stream()
                    .map(Item::getCategoryId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            Set<Long> sellerIds = records.stream()
                    .map(Item::getSellerId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            if (!categoryIds.isEmpty()) {
                List<Category> categories = categoryMapper.selectByIds(categoryIds);
                // 子类的展示名带父类前缀（如「数码电子 / 手机/平板」），先批量取父类
                Set<Long> parentIds = categories.stream()
                        .map(Category::getParentId).filter(pid -> pid != null && pid > 0).collect(Collectors.toSet());
                Map<Long, String> parentNames = new HashMap<>();
                if (!parentIds.isEmpty()) {
                    for (Category parent : categoryMapper.selectByIds(parentIds)) {
                        parentNames.put(parent.getId(), parent.getName());
                    }
                }
                for (Category category : categories) {
                    categoryNames.put(category.getId(), displayCategoryName(category, parentNames));
                }
            }
            if (!sellerIds.isEmpty()) {
                for (User user : userMapper.selectByIds(sellerIds)) {
                    sellerNames.put(user.getId(), user.getNickname());
                }
            }
        }

        Page<ItemVO> voPage = new Page<>(itemPage.getCurrent(), itemPage.getSize(), itemPage.getTotal());
        List<ItemVO> voList = new ArrayList<>(records.size());
        for (Item item : records) {
            ItemVO vo = new ItemVO();
            copyBase(item, vo);
            vo.setCategoryName(categoryNames.get(item.getCategoryId()));
            vo.setSellerNickname(sellerNames.get(item.getSellerId()));
            voList.add(vo);
        }
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 实体基础字段复制到 VO。
     *
     * @param item 实体
     * @param vo   目标 VO
     */
    /**
     * 子类展示名带父类前缀：parentNames 为 null 时按需单查（详情页用）。
     *
     * @param category     分类
     * @param parentNames  父类名缓存（可 null）
     * @return 如「数码电子 / 手机/平板」；一级分类返回自身名称
     */
    private String displayCategoryName(Category category, Map<Long, String> parentNames) {
        if (category.getParentId() == null || category.getParentId() == 0L) {
            return category.getName();
        }
        String parentName = null;
        if (parentNames != null) {
            parentName = parentNames.get(category.getParentId());
        } else {
            Category parent = categoryMapper.selectById(category.getParentId());
            parentName = parent == null ? null : parent.getName();
        }
        return parentName == null ? category.getName() : parentName + " / " + category.getName();
    }

    private void copyBase(Item item, ItemVO vo) {
        vo.setId(item.getId());
        vo.setTitle(item.getTitle());
        vo.setCoverImage(item.getCoverImage());
        vo.setPrice(item.getPrice());
        vo.setOriginalPrice(item.getOriginalPrice());
        vo.setConditionLevel(item.getConditionLevel());
        vo.setConditionDesc(ConditionEnum.descOf(item.getConditionLevel()));
        vo.setCampus(item.getCampus());
        vo.setTradeType(item.getTradeType());
        vo.setTradeTypeDesc(TradeTypeEnum.descOf(item.getTradeType()));
        vo.setStatus(item.getStatus());
        vo.setStatusDesc(ItemStatusEnum.descOf(item.getStatus()));
        vo.setViewCount(item.getViewCount());
        vo.setFavoriteCount(item.getFavoriteCount());
        vo.setCategoryId(item.getCategoryId());
        vo.setSellerId(item.getSellerId());
        vo.setBrandModel(item.getBrandModel());
        vo.setCreatedAt(item.getCreatedAt());
    }

    /**
     * 查询商品的图片列表（按 sort 升序）。
     *
     * @param itemId 商品 ID
     * @return 图片 VO 列表
     */
    private List<ItemImageVO> listImages(Long itemId) {
        List<ItemImage> images = itemImageMapper.selectList(Wrappers.<ItemImage>lambdaQuery()
                .eq(ItemImage::getItemId, itemId)
                .orderByAsc(ItemImage::getSort)
                .orderByAsc(ItemImage::getId));
        List<ItemImageVO> result = new ArrayList<>(images.size());
        for (ItemImage image : images) {
            result.add(new ItemImageVO(image.getId(), image.getUrl(), image.getSort()));
        }
        return result;
    }

    /**
     * 整组物理替换商品图片。
     *
     * @param itemId 商品 ID
     * @param urls   图片 URL 列表
     */
    private void replaceImages(Long itemId, List<String> urls) {
        itemImageMapper.delete(Wrappers.<ItemImage>lambdaQuery().eq(ItemImage::getItemId, itemId));
        if (urls == null || urls.isEmpty()) {
            return;
        }
        int sort = 0;
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            ItemImage image = new ItemImage();
            image.setItemId(itemId);
            image.setUrl(url.trim());
            image.setSort(sort++);
            itemImageMapper.insert(image);
        }
    }

    /**
     * 取页码，空值兜底为 1。
     *
     * @param query 查询条件
     * @return 页码
     */
    private long pageOf(BaseQuery query) {
        return query.getCurrentPage() == null ? 1L : query.getCurrentPage();
    }

    /**
     * 取每页条数，空值兜底为 10。
     *
     * @param query 查询条件
     * @return 条数
     */
    private long sizeOf(BaseQuery query) {
        return query.getPageSize() == null ? 10L : query.getPageSize();
    }
}
