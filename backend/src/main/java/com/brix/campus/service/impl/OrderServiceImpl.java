package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brix.campus.common.BusinessException;
import com.brix.campus.mapper.ItemMapper;
import com.brix.campus.mapper.OrderMapper;
import com.brix.campus.mapper.ReviewMapper;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.Item;
import com.brix.campus.model.Order;
import com.brix.campus.model.Review;
import com.brix.campus.model.User;
import com.brix.campus.model.dto.CancelOrderDTO;
import com.brix.campus.model.dto.OrderCreateDTO;
import com.brix.campus.model.dto.PayDTO;
import com.brix.campus.model.enums.CloseReasonEnum;
import com.brix.campus.model.enums.ItemStatusEnum;
import com.brix.campus.model.enums.OrderStatusEnum;
import com.brix.campus.model.enums.PayMethodEnum;
import com.brix.campus.model.enums.TradeTypeEnum;
import com.brix.campus.model.query.OrderQuery;
import com.brix.campus.model.vo.OrderDetailVO;
import com.brix.campus.model.vo.OrderVO;
import com.brix.campus.service.OrderService;
import com.brix.campus.util.OrderNoGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 订单服务实现。
 *
 * <p>三个事务边界之一：{@link #pay} / {@link #cancel}（架构 2.2）。抢占商品用「条件更新影响行数」判 CAS，
 * 不用 {@code SELECT ... FOR UPDATE}（架构 3.13）。</p>
 */
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ItemMapper itemMapper;
    private final UserMapper userMapper;
    private final ReviewMapper reviewMapper;
    private final int unpaidTimeoutMinutes;

    public OrderServiceImpl(OrderMapper orderMapper,
                            ItemMapper itemMapper,
                            UserMapper userMapper,
                            ReviewMapper reviewMapper,
                            @Value("${brix.order.unpaid-timeout-minutes:30}") int unpaidTimeoutMinutes) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
        this.reviewMapper = reviewMapper;
        this.unpaidTimeoutMinutes = unpaidTimeoutMinutes;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(Long buyerId, OrderCreateDTO dto) {
        Item item = itemMapper.selectById(dto.getItemId());
        if (item == null) {
            throw BusinessException.param("商品不可购买");
        }
        if (!ItemStatusEnum.ON_SALE.getCode().equals(item.getStatus())) {
            throw BusinessException.param("商品不可购买");
        }
        if (item.getSellerId().equals(buyerId)) {
            throw BusinessException.param("不能购买自己发布的商品");
        }
        Long unpaidExists = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getItemId, item.getId())
                .eq(Order::getBuyerId, buyerId)
                .eq(Order::getStatus, OrderStatusEnum.UNPAID.getCode()));
        if (unpaidExists != null && unpaidExists > 0) {
            throw BusinessException.param("请勿重复下单");
        }

        String tradeType = resolveTradeType(item, dto.getTradeType());

        // 按成交方式做交叉必填校验：面交=时间+地点；邮寄=收货三件套
        boolean isMeet = TradeTypeEnum.MEET.getCode().equals(tradeType);
        if (isMeet) {
            if (dto.getMeetTime() == null) {
                throw BusinessException.param("请选择面交时间");
            }
            if (dto.getMeetTime().isBefore(LocalDateTime.now())) {
                throw BusinessException.param("面交时间不能早于当前时间");
            }
            if (dto.getMeetPlace() == null || dto.getMeetPlace().isBlank()) {
                throw BusinessException.param("请填写面交地点");
            }
        } else {
            if (dto.getReceiverName() == null || dto.getReceiverName().isBlank()) {
                throw BusinessException.param("请填写收货人姓名");
            }
            if (dto.getReceiverPhone() == null || dto.getReceiverPhone().isBlank()) {
                throw BusinessException.param("请填写收货人手机号");
            }
            if (dto.getReceiverAddress() == null || dto.getReceiverAddress().isBlank()) {
                throw BusinessException.param("请填写收货地址");
            }
        }

        Order order = new Order();
        order.setOrderNo(OrderNoGenerator.generate());
        order.setItemId(item.getId());
        order.setBuyerId(buyerId);
        order.setSellerId(item.getSellerId());
        order.setAmount(item.getPrice());
        order.setItemTitle(item.getTitle());
        order.setItemCover(item.getCoverImage() == null ? "" : item.getCoverImage());
        order.setItemPrice(item.getPrice());
        order.setTradeType(tradeType);
        order.setStatus(OrderStatusEnum.UNPAID.getCode());
        if (isMeet) {
            order.setMeetTime(dto.getMeetTime());
            order.setMeetPlace(dto.getMeetPlace().trim());
        } else {
            order.setReceiverName(dto.getReceiverName().trim());
            order.setReceiverPhone(dto.getReceiverPhone().trim());
            order.setReceiverAddress(dto.getReceiverAddress().trim());
        }
        order.setBuyerNote(dto.getBuyerNote());
        orderMapper.insert(order);

        return toVO(order, buyerId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO pay(Long userId, Long orderId, PayDTO dto) {
        if (!PayMethodEnum.isValid(dto.getPayMethod())) {
            throw BusinessException.param("不支持的支付方式");
        }
        Order order = getEntity(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!order.getBuyerId().equals(userId)) {
            throw BusinessException.forbidden("无权操作该订单");
        }
        if (!OrderStatusEnum.UNPAID.getCode().equals(order.getStatus())) {
            throw BusinessException.param("订单状态已变更，请刷新");
        }

        // ① 抢占商品：条件更新，影响行数为 0 说明已被抢先购买（E10 防超卖）
        int itemRows = itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, order.getItemId())
                .eq(Item::getStatus, ItemStatusEnum.ON_SALE.getCode())
                .set(Item::getStatus, ItemStatusEnum.SOLD.getCode())
                .set(Item::getUpdatedAt, LocalDateTime.now()));
        if (itemRows == 0) {
            throw BusinessException.param("商品已被抢先购买");
        }

        // ② 订单流转：面交 → TO_MEET，邮寄 → TO_SHIP
        String targetStatus = TradeTypeEnum.MEET.getCode().equals(order.getTradeType())
                ? OrderStatusEnum.TO_MEET.getCode()
                : OrderStatusEnum.TO_SHIP.getCode();
        LocalDateTime now = LocalDateTime.now();
        int orderRows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatusEnum.UNPAID.getCode())
                .set(Order::getStatus, targetStatus)
                .set(Order::getPayMethod, dto.getPayMethod())
                .set(Order::getPayTime, now));
        if (orderRows == 0) {
            throw BusinessException.param("订单状态已变更，请刷新");
        }

        // ③ 关闭同一商品其它待付款订单
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getItemId, order.getItemId())
                .eq(Order::getStatus, OrderStatusEnum.UNPAID.getCode())
                .ne(Order::getId, orderId)
                .set(Order::getStatus, OrderStatusEnum.CANCELED.getCode())
                .set(Order::getCloseReason, CloseReasonEnum.SYSTEM.getCode())
                .set(Order::getCancelTime, now));

        Order latest = getEntity(orderId);
        return toVO(latest, userId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO ship(Long userId, Long orderId) {
        Order order = getEntity(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!order.getSellerId().equals(userId)) {
            throw BusinessException.forbidden("无权操作该订单");
        }
        if (!OrderStatusEnum.TO_SHIP.getCode().equals(order.getStatus())) {
            throw BusinessException.param("当前订单状态不支持该操作");
        }
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatusEnum.TO_SHIP.getCode())
                .set(Order::getStatus, OrderStatusEnum.TO_RECEIVE.getCode())
                .set(Order::getShipTime, LocalDateTime.now()));
        if (rows == 0) {
            throw BusinessException.param("当前订单状态不支持该操作");
        }
        return toVO(getEntity(orderId), userId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO confirm(Long userId, Long orderId) {
        Order order = getEntity(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!order.getBuyerId().equals(userId)) {
            throw BusinessException.forbidden("无权操作该订单");
        }
        // 邮寄单确认收货、面交单确认完成交易
        boolean confirmable = OrderStatusEnum.TO_RECEIVE.getCode().equals(order.getStatus())
                || OrderStatusEnum.TO_MEET.getCode().equals(order.getStatus());
        if (!confirmable) {
            throw BusinessException.param("当前订单状态不支持该操作");
        }
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, order.getStatus())
                .set(Order::getStatus, OrderStatusEnum.DONE.getCode())
                .set(Order::getFinishTime, LocalDateTime.now())
                // 托管释放：买家确认收货，预付款同一事务内结算给卖家
                .set(Order::getSettleTime, LocalDateTime.now()));
        if (rows == 0) {
            throw BusinessException.param("当前订单状态不支持该操作");
        }
        return toVO(getEntity(orderId), userId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO cancel(Long userId, Long orderId, CancelOrderDTO dto) {
        Order order = getEntity(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw BusinessException.forbidden("无权操作该订单");
        }
        String status = order.getStatus();
        if (!OrderStatusEnum.isOngoing(status)) {
            throw BusinessException.param("当前订单状态不可取消");
        }
        // 待付款仅买家可取消；待发货与待面交买卖双方均可取消（PRD 8-#1 当前处理）
        if (OrderStatusEnum.UNPAID.getCode().equals(status) && !isBuyer) {
            throw BusinessException.forbidden("无权操作该订单");
        }

        String closeReason = isBuyer
                ? CloseReasonEnum.BUYER_CANCEL.getCode()
                : CloseReasonEnum.SELLER_CANCEL.getCode();
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, status)
                .set(Order::getStatus, OrderStatusEnum.CANCELED.getCode())
                .set(Order::getCloseReason, closeReason)
                .set(Order::getCancelTime, LocalDateTime.now()));
        if (rows == 0) {
            throw BusinessException.param("当前订单状态不可取消");
        }

        // 已售出商品回滚为在售（条件更新，避免误回滚他人已成交商品）
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, order.getItemId())
                .eq(Item::getStatus, ItemStatusEnum.SOLD.getCode())
                .set(Item::getStatus, ItemStatusEnum.ON_SALE.getCode())
                .set(Item::getUpdatedAt, LocalDateTime.now()));

        return toVO(getEntity(orderId), userId, null);
    }

    @Override
    public IPage<OrderVO> pageBought(Long buyerId, OrderQuery query) {
        Page<Order> page = new Page<>(pageOf(query), sizeOf(query));
        var wrapper = Wrappers.<Order>lambdaQuery().eq(Order::getBuyerId, buyerId);
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(Order::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Order::getCreatedAt).orderByDesc(Order::getId);
        return convertPage(orderMapper.selectPage(page, wrapper), buyerId);
    }

    @Override
    public IPage<OrderVO> pageSold(Long sellerId, OrderQuery query) {
        Page<Order> page = new Page<>(pageOf(query), sizeOf(query));
        var wrapper = Wrappers.<Order>lambdaQuery().eq(Order::getSellerId, sellerId);
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(Order::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Order::getCreatedAt).orderByDesc(Order::getId);
        return convertPage(orderMapper.selectPage(page, wrapper), sellerId);
    }

    @Override
    public OrderDetailVO detail(Long userId, String role, Long orderId) {
        Order order = getEntity(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw BusinessException.forbidden("无权查看该订单");
        }

        OrderDetailVO vo = new OrderDetailVO();
        OrderVO base = toVO(order, userId, role);
        copyOrder(base, vo);
        vo.setBuyerNote(order.getBuyerNote());
        vo.setShipTime(order.getShipTime());
        vo.setFinishTime(order.getFinishTime());
        vo.setCancelTime(order.getCancelTime());
        vo.setCloseReason(order.getCloseReason());
        vo.setCloseReasonDesc(order.getCloseReason() == null
                ? null : CloseReasonEnum.descOf(order.getCloseReason()));

        User buyer = userMapper.selectById(order.getBuyerId());
        if (buyer != null) {
            vo.setBuyerUsername(buyer.getUsername());
            vo.setBuyerPhone(buyer.getPhone());
            vo.setBuyerCampus(buyer.getCampus());
        }
        User seller = userMapper.selectById(order.getSellerId());
        if (seller != null) {
            vo.setSellerUsername(seller.getUsername());
            vo.setSellerPhone(seller.getPhone());
            vo.setSellerCampus(seller.getCampus());
        }

        if (OrderStatusEnum.UNPAID.getCode().equals(order.getStatus())) {
            long elapsed = Duration.between(order.getCreatedAt(), LocalDateTime.now()).getSeconds();
            vo.setPayRemainSeconds(Math.max(0L, unpaidTimeoutMinutes * 60L - elapsed));
        } else {
            vo.setPayRemainSeconds(0L);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int closeTimeoutOrders(int timeoutMinutes) {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        return orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getStatus, OrderStatusEnum.UNPAID.getCode())
                .lt(Order::getCreatedAt, deadline)
                .set(Order::getStatus, OrderStatusEnum.CANCELED.getCode())
                .set(Order::getCloseReason, CloseReasonEnum.TIMEOUT.getCode())
                .set(Order::getCancelTime, LocalDateTime.now()));
    }

    @Override
    public Order getEntity(Long orderId) {
        if (orderId == null) {
            return null;
        }
        return orderMapper.selectById(orderId);
    }

    /* --------------------------- 内部工具方法 --------------------------- */

    /**
     * 解析实际成交方式：商品为 {@code BOTH} 时由买家二选一。
     *
     * @param item          商品
     * @param chosenTradeType 买家选择
     * @return MEET 或 SHIP
     */
    private String resolveTradeType(Item item, String chosenTradeType) {
        if (!TradeTypeEnum.BOTH.getCode().equals(item.getTradeType())) {
            return item.getTradeType();
        }
        if (TradeTypeEnum.MEET.getCode().equals(chosenTradeType)) {
            return TradeTypeEnum.MEET.getCode();
        }
        if (TradeTypeEnum.SHIP.getCode().equals(chosenTradeType)) {
            return TradeTypeEnum.SHIP.getCode();
        }
        throw BusinessException.param("请选择面交或邮寄");
    }

    /**
     * 分页结果转 VO，并批量补齐买卖双方昵称。
     *
     * @param orderPage 实体分页
     * @param viewerId  当前用户 ID（用于计算可执行操作）
     * @return VO 分页
     */
    private IPage<OrderVO> convertPage(IPage<Order> orderPage, Long viewerId) {
        List<Order> records = orderPage.getRecords();
        Map<Long, String> nicknames = new HashMap<>();
        if (!records.isEmpty()) {
            Set<Long> userIds = new HashSet<>();
            records.forEach(order -> {
                userIds.add(order.getBuyerId());
                userIds.add(order.getSellerId());
            });
            for (User user : userMapper.selectByIds(userIds)) {
                nicknames.put(user.getId(), user.getNickname());
            }
        }

        Page<OrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        List<OrderVO> voList = new ArrayList<>(records.size());
        for (Order order : records) {
            voList.add(toVO(order, viewerId, null, nicknames));
        }
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 实体转 VO。
     *
     * @param order 实体
     * @param viewerId 当前用户 ID
     * @param role 当前用户角色（可空）
     * @return VO
     */
    private OrderVO toVO(Order order, Long viewerId, String role) {
        Map<Long, String> nicknames = new HashMap<>();
        User buyer = userMapper.selectById(order.getBuyerId());
        if (buyer != null) {
            nicknames.put(buyer.getId(), buyer.getNickname());
        }
        User seller = userMapper.selectById(order.getSellerId());
        if (seller != null) {
            nicknames.put(seller.getId(), seller.getNickname());
        }
        return toVO(order, viewerId, role, nicknames);
    }

    /**
     * 实体转 VO（昵称映射可复用，避免列表 N+1）。
     *
     * @param order     实体
     * @param viewerId  当前用户 ID
     * @param role      当前用户角色（可空）
     * @param nicknames 昵称映射
     * @return VO
     */
    private OrderVO toVO(Order order, Long viewerId, String role, Map<Long, String> nicknames) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setItemId(order.getItemId());
        vo.setItemTitle(order.getItemTitle());
        vo.setItemCover(order.getItemCover());
        vo.setItemPrice(order.getItemPrice());
        vo.setAmount(order.getAmount());
        vo.setTradeType(order.getTradeType());
        vo.setTradeTypeDesc(order.getTradeType() == null ? null : TradeTypeEnum.descOf(order.getTradeType()));
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(OrderStatusEnum.descOf(order.getStatus()));
        vo.setPayMethod(order.getPayMethod());
        vo.setPayMethodDesc(order.getPayMethod() == null ? null : PayMethodEnum.descOf(order.getPayMethod()));
        vo.setPayTime(order.getPayTime());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setBuyerId(order.getBuyerId());
        vo.setSellerId(order.getSellerId());
        vo.setBuyerNickname(nicknames.get(order.getBuyerId()));
        vo.setSellerNickname(nicknames.get(order.getSellerId()));
        vo.setMeetPlace(order.getMeetPlace());
        vo.setMeetTime(order.getMeetTime());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setSettleTime(order.getSettleTime());
        // 托管状态：已付款未完成 = 托管中；完成 = 已结算给卖家
        if (order.getSettleTime() != null) {
            vo.setEscrowDesc("已结算给卖家");
        } else if (order.getPayTime() != null) {
            vo.setEscrowDesc("预付款托管中");
        }

        boolean isBuyer = viewerId != null && viewerId.equals(order.getBuyerId());
        boolean isSeller = viewerId != null && viewerId.equals(order.getSellerId());
        String viewerRole = isBuyer ? "BUYER" : (isSeller ? "SELLER" : "GUEST");
        vo.setViewerRole(viewerRole);

        String status = order.getStatus();

        vo.setCanPay(isBuyer && OrderStatusEnum.UNPAID.getCode().equals(status));
        vo.setCanShip(isSeller && OrderStatusEnum.TO_SHIP.getCode().equals(status));
        vo.setCanConfirm(isBuyer && (OrderStatusEnum.TO_RECEIVE.getCode().equals(status)
                || OrderStatusEnum.TO_MEET.getCode().equals(status)));
        boolean cancellable = OrderStatusEnum.isOngoing(status)
                && !(OrderStatusEnum.UNPAID.getCode().equals(status) && !isBuyer)
                && (isBuyer || isSeller);
        vo.setCanCancel(cancellable);

        boolean reviewed = false;
        if (isBuyer && OrderStatusEnum.DONE.getCode().equals(status)) {
            Long reviewCount = reviewMapper.selectCount(Wrappers.<Review>lambdaQuery()
                    .eq(Review::getOrderId, order.getId()));
            reviewed = reviewCount != null && reviewCount > 0;
        }
        vo.setReviewed(reviewed);
        vo.setCanReview(isBuyer && OrderStatusEnum.DONE.getCode().equals(status) && !reviewed);
        return vo;
    }

    /**
     * 把列表 VO 的公共字段复制到详情 VO（详情继承列表）。
     *
     * @param source 列表 VO
     * @param target 详情 VO
     */
    private void copyOrder(OrderVO source, OrderDetailVO target) {
        target.setId(source.getId());
        target.setOrderNo(source.getOrderNo());
        target.setItemId(source.getItemId());
        target.setItemTitle(source.getItemTitle());
        target.setItemCover(source.getItemCover());
        target.setItemPrice(source.getItemPrice());
        target.setAmount(source.getAmount());
        target.setTradeType(source.getTradeType());
        target.setTradeTypeDesc(source.getTradeTypeDesc());
        target.setStatus(source.getStatus());
        target.setStatusDesc(source.getStatusDesc());
        target.setPayMethod(source.getPayMethod());
        target.setPayMethodDesc(source.getPayMethodDesc());
        target.setPayTime(source.getPayTime());
        target.setCreatedAt(source.getCreatedAt());
        target.setBuyerId(source.getBuyerId());
        target.setSellerId(source.getSellerId());
        target.setBuyerNickname(source.getBuyerNickname());
        target.setSellerNickname(source.getSellerNickname());
        target.setMeetPlace(source.getMeetPlace());
        target.setMeetTime(source.getMeetTime());
        target.setReceiverName(source.getReceiverName());
        target.setReceiverPhone(source.getReceiverPhone());
        target.setReceiverAddress(source.getReceiverAddress());
        target.setEscrowDesc(source.getEscrowDesc());
        target.setSettleTime(source.getSettleTime());
        target.setViewerRole(source.getViewerRole());
        target.setCanPay(source.getCanPay());
        target.setCanShip(source.getCanShip());
        target.setCanConfirm(source.getCanConfirm());
        target.setCanCancel(source.getCanCancel());
        target.setCanReview(source.getCanReview());
        target.setReviewed(source.getReviewed());
    }

    /**
     * 取页码。
     *
     * @param query 查询条件
     * @return 页码
     */
    private long pageOf(OrderQuery query) {
        return query.getCurrentPage() == null ? 1L : query.getCurrentPage();
    }

    /**
     * 取每页条数。
     *
     * @param query 查询条件
     * @return 条数
     */
    private long sizeOf(OrderQuery query) {
        return query.getPageSize() == null ? 10L : query.getPageSize();
    }
}
