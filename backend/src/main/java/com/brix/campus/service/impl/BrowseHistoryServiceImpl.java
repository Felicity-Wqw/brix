package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.brix.campus.mapper.BrowseHistoryMapper;
import com.brix.campus.mapper.ItemMapper;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.BrowseHistory;
import com.brix.campus.model.Item;
import com.brix.campus.model.User;
import com.brix.campus.model.enums.ItemStatusEnum;
import com.brix.campus.model.query.BaseQuery;
import com.brix.campus.model.vo.BrowseHistoryVO;
import com.brix.campus.service.BrowseHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 浏览历史服务实现。
 *
 * <p>写入采用「先更新，零行命中再插入」的 upsert 策略，
 * 并发首次浏览触发唯一键冲突时捕获 {@link DuplicateKeyException} 降级为更新。</p>
 */
@Slf4j
@Service
public class BrowseHistoryServiceImpl extends ServiceImpl<BrowseHistoryMapper, BrowseHistory>
        implements BrowseHistoryService {

    private final ItemMapper itemMapper;
    private final UserMapper userMapper;

    public BrowseHistoryServiceImpl(ItemMapper itemMapper, UserMapper userMapper) {
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
    }

    @Override
    public void record(Long userId, Long itemId) {
        if (userId == null || itemId == null) {
            return;
        }
        try {
            int updated = baseMapper.update(null, new LambdaUpdateWrapper<BrowseHistory>()
                    .eq(BrowseHistory::getUserId, userId)
                    .eq(BrowseHistory::getItemId, itemId)
                    .set(BrowseHistory::getUpdatedAt, LocalDateTime.now()));
            if (updated == 0) {
                BrowseHistory history = new BrowseHistory();
                history.setUserId(userId);
                history.setItemId(itemId);
                try {
                    baseMapper.insert(history);
                } catch (DuplicateKeyException e) {
                    // 并发首次浏览：唯一键已存在，刷新时间即可
                    baseMapper.update(null, new LambdaUpdateWrapper<BrowseHistory>()
                            .eq(BrowseHistory::getUserId, userId)
                            .eq(BrowseHistory::getItemId, itemId)
                            .set(BrowseHistory::getUpdatedAt, LocalDateTime.now()));
                }
            }
        } catch (Exception e) {
            // 浏览记录属增强功能，失败静默，不影响详情主流程
            log.warn("记录浏览历史失败 userId={} itemId={}: {}", userId, itemId, e.getMessage());
        }
    }

    @Override
    public IPage<BrowseHistoryVO> page(Long userId, BaseQuery query) {
        // 个人浏览历史量级小：先取全量按时间倒序，过滤已删除商品后内存分页，
        // 保证 total 与实际展示一致（join 分页会因商品删除导致 total 虚高）。
        List<BrowseHistory> all = baseMapper.selectList(Wrappers.<BrowseHistory>lambdaQuery()
                .eq(BrowseHistory::getUserId, userId)
                .orderByDesc(BrowseHistory::getUpdatedAt)
                .orderByDesc(BrowseHistory::getId));
        if (all.isEmpty()) {
            return new Page<>(query.getCurrentPage(), query.getPageSize(), 0);
        }

        // 批量查商品（逻辑删除自动过滤）
        Set<Long> itemIds = all.stream().map(BrowseHistory::getItemId).collect(Collectors.toSet());
        Map<Long, Item> itemMap = itemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        Set<Long> sellerIds = itemMap.values().stream()
                .map(Item::getSellerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> userMap = sellerIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(sellerIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        List<BrowseHistoryVO> vos = new ArrayList<>(all.size());
        for (BrowseHistory history : all) {
            Item item = itemMap.get(history.getItemId());
            if (item == null) {
                continue; // 商品已删除，不展示且不计入 total
            }
            BrowseHistoryVO vo = new BrowseHistoryVO();
            vo.setId(history.getId());
            vo.setItemId(item.getId());
            vo.setItemTitle(item.getTitle());
            vo.setItemCover(item.getCoverImage());
            vo.setItemPrice(item.getPrice());
            vo.setItemStatus(item.getStatus());
            vo.setItemStatusDesc(ItemStatusEnum.descOf(item.getStatus()));
            User seller = userMap.get(item.getSellerId());
            vo.setSellerNickname(seller == null ? null : seller.getNickname());
            vo.setBrowseTime(history.getUpdatedAt());
            vos.add(vo);
        }

        int page = query.getCurrentPage() == null ? 1 : query.getCurrentPage();
        int size = query.getPageSize() == null ? 10 : query.getPageSize();
        int from = Math.min((page - 1) * size, vos.size());
        int to = Math.min(from + size, vos.size());
        Page<BrowseHistoryVO> result = new Page<>(page, size, vos.size());
        result.setRecords(vos.subList(from, to));
        return result;
    }
}
