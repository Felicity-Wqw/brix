package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brix.campus.common.BusinessException;
import com.brix.campus.mapper.ChatMessageMapper;
import com.brix.campus.mapper.JuryCaseMapper;
import com.brix.campus.mapper.OrderMapper;
import com.brix.campus.mapper.RefundMapper;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.ChatMessage;
import com.brix.campus.model.JuryCase;
import com.brix.campus.model.Order;
import com.brix.campus.model.Refund;
import com.brix.campus.model.User;
import com.brix.campus.model.dto.RefundApplyDTO;
import com.brix.campus.model.dto.RefundHandleDTO;
import com.brix.campus.model.dto.RefundLogisticsDTO;
import com.brix.campus.model.enums.OrderStatusEnum;
import com.brix.campus.model.enums.RefundStatusEnum;
import com.brix.campus.model.enums.RefundTypeEnum;
import com.brix.campus.model.enums.TradeTypeEnum;
import com.brix.campus.model.vo.RefundVO;
import com.brix.campus.service.RefundService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 售后退款服务实现。
 *
 * <p>所有状态变更走条件更新（CAS），并发场景下抢不到就抛「状态已变化」；
 * 通知一律发聊天（platform 系统账号），发送失败不阻断主流程。</p>
 */
@Service
public class RefundServiceImpl implements RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundServiceImpl.class);

    /** 售后有效期：确认收货后 15 天内可申请（淘宝/京东/拼多多一致口径） */
    private static final int APPLY_WINDOW_DAYS = 15;
    /** 卖家处理时限：36 小时，超时自动同意（拼多多现行值，严于淘宝 72h） */
    private static final int SELLER_HANDLE_HOURS = 36;
    /** 被拒绝后买家响应窗口：5 日 */
    private static final int REVISE_WINDOW_DAYS = 5;
    /** 退货环节：买家寄回时限 7 日 */
    private static final int RETURN_SHIP_DAYS = 7;
    /** 退货环节：卖家确认收货时限 7 日 */
    private static final int RECEIVE_CONFIRM_DAYS = 7;
    /** 平台系统账号用户名 */
    private static final String PLATFORM_USERNAME = "platform";

    private final RefundMapper refundMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final JuryCaseMapper juryCaseMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;

    /** 平台系统账号 ID（懒加载缓存，迁移脚本保证存在） */
    private volatile Long platformUserId;

    public RefundServiceImpl(RefundMapper refundMapper, OrderMapper orderMapper,
                             UserMapper userMapper, JuryCaseMapper juryCaseMapper,
                             ChatMessageMapper chatMessageMapper, ObjectMapper objectMapper) {
        this.refundMapper = refundMapper;
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.juryCaseMapper = juryCaseMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.objectMapper = objectMapper;
    }

    // ================================ 买家 ================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundVO apply(Long buyerId, RefundApplyDTO dto) {
        Order order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw BusinessException.forbidden("只能对自己的订单申请售后");
        }
        if (!OrderStatusEnum.DONE.getCode().equals(order.getStatus())) {
            throw BusinessException.param("订单完成后才能申请售后");
        }
        if (order.getFinishTime() != null
                && order.getFinishTime().plusDays(APPLY_WINDOW_DAYS).isBefore(LocalDateTime.now())) {
            throw BusinessException.param("已超过售后有效期（确认收货后 15 天）");
        }
        RefundTypeEnum type = RefundTypeEnum.of(dto.getType());
        if (type == null) {
            throw BusinessException.param("售后类型不合法");
        }
        // 面交单当面验货、无物流环节，只允许「仅退款」
        if (TradeTypeEnum.MEET.getCode().equals(order.getTradeType())
                && type != RefundTypeEnum.REFUND_ONLY) {
            throw BusinessException.param("面交订单仅支持「仅退款」（线下当面退）");
        }
        Refund ongoing = findOngoingByOrder(order.getId());
        if (ongoing != null) {
            throw BusinessException.param("该订单已有进行中的售后，请先等待其处理完毕");
        }
        // 已完成过退款的订单禁止再次售后（退款完成时订单会写入 refund_time，防止二次退款）
        if (order.getRefundTime() != null) {
            throw BusinessException.param("该订单已完成退款，不可再次申请售后");
        }
        // 兜底：历史数据可能只有售后单为 REFUNDED、订单 refund_time 未回写，按售后单再拦一次
        Long refundedCount = refundMapper.selectCount(Wrappers.<Refund>lambdaQuery()
                .eq(Refund::getOrderId, order.getId())
                .eq(Refund::getStatus, RefundStatusEnum.REFUNDED.getCode()));
        if (refundedCount != null && refundedCount > 0) {
            throw BusinessException.param("该订单已完成退款，不可再次申请售后");
        }

        Refund refund = new Refund();
        refund.setOrderId(order.getId());
        refund.setBuyerId(order.getBuyerId());
        refund.setSellerId(order.getSellerId());
        refund.setAmount(order.getAmount());
        refund.setType(type.getCode());
        refund.setStatus(RefundStatusEnum.APPLYING.getCode());
        refund.setReason(dto.getReason().trim());
        refund.setImages(writeImages(dto.getImages()));
        refund.setReviseCount(0);
        refundMapper.insert(refund);

        notifyBoth(refund, "买家发起「" + type.getDesc() + "」申请，原因：" + refund.getReason()
                + "。卖家请在 36 小时内处理，超时将默认同意退款。");
        return toVO(refund, buyerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long refundId) {
        Refund refund = getEntity(refundId);
        if (!refund.getBuyerId().equals(userId)) {
            throw BusinessException.forbidden("只有申请人可以撤销售后");
        }
        if (!RefundStatusEnum.APPLYING.getCode().equals(refund.getStatus())) {
            throw BusinessException.param("当前状态不可撤销");
        }
        int rows = refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                .eq(Refund::getId, refundId)
                .eq(Refund::getStatus, RefundStatusEnum.APPLYING.getCode())
                .set(Refund::getStatus, RefundStatusEnum.CANCELED.getCode()));
        if (rows == 0) {
            throw BusinessException.param("当前状态不可撤销");
        }
        notifyBoth(refund, "买家已撤销售后申请，订单恢复无售后状态（保障期内可重新申请）。");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundVO revise(Long buyerId, Long refundId, RefundApplyDTO dto) {
        Refund refund = getEntity(refundId);
        if (!refund.getBuyerId().equals(buyerId)) {
            throw BusinessException.forbidden("只有申请人可以修改售后");
        }
        if (!RefundStatusEnum.REJECTED.getCode().equals(refund.getStatus())) {
            throw BusinessException.param("仅被拒绝的售后可以修改重提");
        }
        ensureReviseWindowOpen(refund);
        RefundTypeEnum type = RefundTypeEnum.of(dto.getType());
        if (type == null) {
            throw BusinessException.param("售后类型不合法");
        }
        if (TradeTypeEnum.MEET.getCode().equals(orderTradeType(refund.getOrderId()))
                && type != RefundTypeEnum.REFUND_ONLY) {
            throw BusinessException.param("面交订单仅支持「仅退款」");
        }
        int rows = refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                .eq(Refund::getId, refundId)
                .eq(Refund::getStatus, RefundStatusEnum.REJECTED.getCode())
                .set(Refund::getType, type.getCode())
                .set(Refund::getReason, dto.getReason().trim())
                .set(Refund::getImages, writeImages(dto.getImages()))
                .set(Refund::getStatus, RefundStatusEnum.APPLYING.getCode())
                .set(Refund::getRejectAt, null)
                .set(Refund::getSellerReply, null)
                .set(Refund::getReviseCount, (refund.getReviseCount() == null ? 0 : refund.getReviseCount()) + 1));
        if (rows == 0) {
            throw BusinessException.param("售后状态已变化，请刷新后重试");
        }
        Refund updated = getEntity(refundId);
        notifyBoth(updated, "买家已修改售后申请（第 "
                + updated.getReviseCount() + " 次重提），重新进入 36 小时处理期。");
        return toVO(updated, buyerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundVO submitLogistics(Long buyerId, Long refundId, RefundLogisticsDTO dto) {
        Refund refund = getEntity(refundId);
        if (!refund.getBuyerId().equals(buyerId)) {
            throw BusinessException.forbidden("只有申请人可以填写退货物流");
        }
        if (!RefundStatusEnum.WAIT_RETURN.getCode().equals(refund.getStatus())) {
            throw BusinessException.param("当前状态无需填写退货物流");
        }
        int rows = refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                .eq(Refund::getId, refundId)
                .eq(Refund::getStatus, RefundStatusEnum.WAIT_RETURN.getCode())
                .set(Refund::getTrackingNo, dto.getTrackingNo().trim())
                .set(Refund::getStatus, RefundStatusEnum.WAIT_RECEIVE.getCode()));
        if (rows == 0) {
            throw BusinessException.param("售后状态已变化，请刷新后重试");
        }
        Refund updated = getEntity(refundId);
        notifyBoth(updated, "买家已寄回商品（运单号：" + dto.getTrackingNo().trim()
                + "），请卖家在 7 日内确认收货，超时将自动退款。");
        return toVO(updated, buyerId);
    }

    // ================================ 卖家 ================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundVO handle(Long sellerId, Long refundId, RefundHandleDTO dto) {
        Refund refund = getEntity(refundId);
        if (!refund.getSellerId().equals(sellerId)) {
            throw BusinessException.forbidden("只有卖家可以处理该售后");
        }
        if (!RefundStatusEnum.APPLYING.getCode().equals(refund.getStatus())) {
            throw BusinessException.param("当前状态无需处理");
        }
        boolean agree = "AGREE".equalsIgnoreCase(dto.getAction());
        if (!agree && !"REJECT".equalsIgnoreCase(dto.getAction())) {
            throw BusinessException.param("处理动作不合法");
        }
        String reply = dto.getReply() == null ? "" : dto.getReply().trim();
        if (agree) {
            String next = nextAfterAgree(refund.getType());
            boolean directRefund = RefundStatusEnum.REFUNDED.getCode().equals(next);
            int rows = refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                    .eq(Refund::getId, refundId)
                    .eq(Refund::getStatus, RefundStatusEnum.APPLYING.getCode())
                    .set(Refund::getSellerReply, reply)
                    .set(Refund::getStatus, next)
                    // 仅退款在同意时即完成退款；退货退款要等寄回+确认
                    .set(directRefund, Refund::getRefundTime,
                            directRefund ? LocalDateTime.now() : null));
            if (rows == 0) {
                throw BusinessException.param("售后状态已变化，请刷新后重试");
            }
            Refund updated = getEntity(refundId);
            if (directRefund) {
                markOrderRefunded(refund.getOrderId());
                notifyBoth(updated, "卖家已同意退款，退款已完成（金额 ¥" + updated.getAmount() + "）。");
            } else {
                notifyBoth(updated, "卖家已同意退货退款，请买家在 7 日内寄回商品并填写运单号。"
                        + (reply.isEmpty() ? "" : "卖家备注：" + reply));
            }
            return toVO(updated, sellerId);
        }
        int rows = refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                .eq(Refund::getId, refundId)
                .eq(Refund::getStatus, RefundStatusEnum.APPLYING.getCode())
                .set(Refund::getSellerReply, reply)
                .set(Refund::getStatus, RefundStatusEnum.REJECTED.getCode())
                .set(Refund::getRejectAt, LocalDateTime.now()));
        if (rows == 0) {
            throw BusinessException.param("售后状态已变化，请刷新后重试");
        }
        Refund updated = getEntity(refundId);
        notifyBoth(updated, "卖家拒绝了售后申请" + (reply.isEmpty() ? "。" : "，理由：" + reply)
                + "。买家可在 5 日内修改后重新提交，或申请平台介入（小法庭）。");
        return toVO(updated, sellerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundVO confirmReceive(Long sellerId, Long refundId) {
        Refund refund = getEntity(refundId);
        if (!refund.getSellerId().equals(sellerId)) {
            throw BusinessException.forbidden("只有卖家可以确认收货");
        }
        if (!RefundStatusEnum.WAIT_RECEIVE.getCode().equals(refund.getStatus())) {
            throw BusinessException.param("当前状态无需确认收货");
        }
        // 条件更新必须带 SET：之前只写 where 会生成 `UPDATE t_refund WHERE ...` 的非法 SQL，
        // 导致卖家点「确认收到退货」必然 500，退货退款卡在 WAIT_RECEIVE 无法收口
        int rows = refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                .eq(Refund::getId, refundId)
                .eq(Refund::getStatus, RefundStatusEnum.WAIT_RECEIVE.getCode())
                .set(Refund::getStatus, RefundStatusEnum.REFUNDED.getCode())
                .set(Refund::getRefundTime, LocalDateTime.now()));
        if (rows == 0) {
            throw BusinessException.param("售后状态已变化，请刷新后重试");
        }
        markOrderRefunded(refund.getOrderId());
        Refund updated = getEntity(refundId);
        notifyBoth(updated, "卖家已确认收到退货，退款已完成（金额 ¥" + updated.getAmount() + "）。");
        return toVO(updated, sellerId);
    }

    // ================================ 查询 ================================

    @Override
    public IPage<RefundVO> pageMyApplied(Long buyerId, long page, long size) {
        return pageByRole(buyerId, true, page, size);
    }

    @Override
    public IPage<RefundVO> pageMyReceived(Long sellerId, long page, long size) {
        return pageByRole(sellerId, false, page, size);
    }

    @Override
    public RefundVO detail(Long userId, Long refundId) {
        Refund refund = getEntity(refundId);
        if (!refund.getBuyerId().equals(userId) && !refund.getSellerId().equals(userId)) {
            throw BusinessException.forbidden("无权查看该售后");
        }
        return toVO(refund, userId);
    }

    // ================================ 定时任务 ================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoAgreeOnSellerTimeout() {
        List<Refund> overdue = refundMapper.selectList(Wrappers.<Refund>lambdaQuery()
                .eq(Refund::getStatus, RefundStatusEnum.APPLYING.getCode())
                .apply("created_at < {0}", LocalDateTime.now().minusHours(SELLER_HANDLE_HOURS))
                .last("LIMIT 100"));
        for (Refund refund : overdue) {
            String next = nextAfterAgree(refund.getType());
            boolean directRefund = RefundStatusEnum.REFUNDED.getCode().equals(next);
            int rows = refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                    .eq(Refund::getId, refund.getId())
                    .eq(Refund::getStatus, RefundStatusEnum.APPLYING.getCode())
                    .set(Refund::getSellerReply, "卖家超时未处理，系统按平台规则自动同意退款")
                    .set(Refund::getStatus, next)
                    .set(directRefund, Refund::getRefundTime,
                            directRefund ? LocalDateTime.now() : null));
            if (rows == 0) {
                continue;
            }
            if (directRefund) {
                markOrderRefunded(refund.getOrderId());
            }
            notifyBoth(getEntity(refund.getId()),
                    "卖家 36 小时内未处理售后，系统已按平台规则自动同意退款。");
        }
        return overdue.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoCloseReturnTimeout() {
        List<Refund> overdue = refundMapper.selectList(Wrappers.<Refund>lambdaQuery()
                .eq(Refund::getStatus, RefundStatusEnum.WAIT_RETURN.getCode())
                .isNull(Refund::getTrackingNo)
                .apply("updated_at < {0}", LocalDateTime.now().minusDays(RETURN_SHIP_DAYS))
                .last("LIMIT 100"));
        for (Refund refund : overdue) {
            int rows = refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                    .eq(Refund::getId, refund.getId())
                    .eq(Refund::getStatus, RefundStatusEnum.WAIT_RETURN.getCode())
                    .isNull(Refund::getTrackingNo)
                    .set(Refund::getStatus, RefundStatusEnum.CLOSED.getCode())
                    .set(Refund::getCloseReason, "买家 7 日内未寄回商品，售后自动关闭"));
            if (rows > 0) {
                notifyBoth(refund, "买家 7 日内未寄回商品，本售后已自动关闭。");
            }
        }
        return overdue.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoRefundOnReceiveTimeout() {
        List<Refund> overdue = refundMapper.selectList(Wrappers.<Refund>lambdaQuery()
                .eq(Refund::getStatus, RefundStatusEnum.WAIT_RECEIVE.getCode())
                .apply("updated_at < {0}", LocalDateTime.now().minusDays(RECEIVE_CONFIRM_DAYS))
                .last("LIMIT 100"));
        for (Refund refund : overdue) {
            // 同样必须带 SET，否则自动退款任务会因非法 SQL 整体失败
            boolean ok = refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                    .eq(Refund::getId, refund.getId())
                    .eq(Refund::getStatus, RefundStatusEnum.WAIT_RECEIVE.getCode())
                    .set(Refund::getStatus, RefundStatusEnum.REFUNDED.getCode())
                    .set(Refund::getRefundTime, LocalDateTime.now())) > 0;
            if (ok) {
                markOrderRefunded(refund.getOrderId());
                notifyBoth(getEntity(refund.getId()),
                        "卖家 7 日内未确认收货，系统已自动完成退款。");
            }
        }
        return overdue.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoInvalidateRejected() {
        List<Refund> overdue = refundMapper.selectList(Wrappers.<Refund>lambdaQuery()
                .eq(Refund::getStatus, RefundStatusEnum.REJECTED.getCode())
                .apply("reject_at < {0}", LocalDateTime.now().minusDays(REVISE_WINDOW_DAYS))
                .last("LIMIT 100"));
        for (Refund refund : overdue) {
            int rows = refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                    .eq(Refund::getId, refund.getId())
                    .eq(Refund::getStatus, RefundStatusEnum.REJECTED.getCode())
                    .set(Refund::getStatus, RefundStatusEnum.CLOSED.getCode())
                    .set(Refund::getCloseReason, "买家 5 日内未重提也未申请平台介入，售后自动失效"));
            if (rows > 0) {
                notifyBoth(refund, "被拒绝后 5 日内未响应，本售后已自动失效关闭。");
            }
        }
        return overdue.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRefunded(Long refundId) {
        int rows = refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                .eq(Refund::getId, refundId)
                .in(Refund::getStatus, RefundStatusEnum.WAIT_RECEIVE.getCode(),
                        RefundStatusEnum.APPLYING.getCode(),
                        RefundStatusEnum.PENDING_RULING.getCode())
                .set(Refund::getStatus, RefundStatusEnum.REFUNDED.getCode())
                .set(Refund::getRefundTime, LocalDateTime.now()));
        if (rows == 0) {
            throw BusinessException.param("当前状态不可退款");
        }
        markOrderRefunded(getEntity(refundId).getOrderId());
    }

    // ================================ 供小法庭调用 ================================

    /**
     * 按订单找进行中的售后单（小法庭裁决需要回写）。
     */
    public Refund findOngoingByOrder(Long orderId) {
        List<Refund> list = refundMapper.selectList(Wrappers.<Refund>lambdaQuery()
                .eq(Refund::getOrderId, orderId)
                .orderByDesc(Refund::getId)
                .last("LIMIT 20"));
        for (Refund refund : list) {
            if (RefundStatusEnum.isOngoing(refund.getStatus())) {
                return refund;
            }
        }
        return null;
    }

    public Refund getEntity(Long refundId) {
        Refund refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw BusinessException.notFound("售后单不存在");
        }
        return refund;
    }

    public void notifyBoth(Refund refund, String content) {
        notify(refund.getBuyerId(), refund.getOrderId(), content);
        notify(refund.getSellerId(), refund.getOrderId(), content);
    }

    // ================================ 私有工具 ================================

    /**
     * 同意后的去向：仅退款 → 直接退款完成；退货退款 → 待买家寄回。
     */
    private String nextAfterAgree(String type) {
        return RefundTypeEnum.RETURN_REFUND.getCode().equals(type)
                ? RefundStatusEnum.WAIT_RETURN.getCode()
                : RefundStatusEnum.REFUNDED.getCode();
    }

    /**
     * 退款收尾时同步回写订单 refund_time（资金链路闭环：托管语义上已原路退回）。
     */
    private void markOrderRefunded(Long orderId) {
        orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, orderId)
                .set(Order::getRefundTime, LocalDateTime.now()));
    }

    private void ensureReviseWindowOpen(Refund refund) {
        if (refund.getRejectAt() == null
                || refund.getRejectAt().plusDays(REVISE_WINDOW_DAYS).isBefore(LocalDateTime.now())) {
            throw BusinessException.param("已超过 5 日响应期，无法修改重提");
        }
    }

    private String orderTradeType(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        return order == null ? "" : order.getTradeType();
    }

    private IPage<RefundVO> pageByRole(Long userId, boolean asBuyer, long page, long size) {
        Page<Refund> p = new Page<>(page, size);
        LambdaQueryWrapper<Refund> qw = Wrappers.<Refund>lambdaQuery()
                .eq(asBuyer ? Refund::getBuyerId : Refund::getSellerId, userId)
                .orderByDesc(Refund::getId);
        IPage<Refund> result = refundMapper.selectPage(p, qw);
        List<RefundVO> vos = new ArrayList<>(result.getRecords().size());
        for (Refund refund : result.getRecords()) {
            vos.add(toVO(refund, userId));
        }
        Page<RefundVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    private RefundVO toVO(Refund refund, Long viewerId) {
        RefundVO vo = new RefundVO();
        vo.setId(refund.getId());
        vo.setOrderId(refund.getOrderId());
        vo.setAmount(refund.getAmount());
        vo.setType(refund.getType());
        RefundTypeEnum type = RefundTypeEnum.of(refund.getType());
        vo.setTypeDesc(type == null ? refund.getType() : type.getDesc());
        vo.setStatus(refund.getStatus());
        vo.setStatusDesc(RefundStatusEnum.descOf(refund.getStatus()));
        vo.setReason(refund.getReason());
        vo.setImages(readImages(refund.getImages()));
        vo.setSellerReply(refund.getSellerReply());
        vo.setTrackingNo(refund.getTrackingNo());
        vo.setRejectAt(refund.getRejectAt());
        vo.setReviseCount(refund.getReviseCount());
        vo.setRefundTime(refund.getRefundTime());
        vo.setCloseReason(refund.getCloseReason());
        vo.setCreatedAt(refund.getCreatedAt());

        Order order = orderMapper.selectById(refund.getOrderId());
        if (order != null) {
            vo.setItemTitle(order.getItemTitle());
            vo.setItemCover(order.getItemCover());
        }
        boolean isBuyerView = refund.getBuyerId().equals(viewerId);
        Long counterpartId = isBuyerView ? refund.getSellerId() : refund.getBuyerId();
        vo.setCounterpartRole(isBuyerView ? "卖家" : "买家");
        User counterpart = userMapper.selectById(counterpartId);
        vo.setCounterpartNickname(maskNickname(counterpart == null ? "" : counterpart.getNickname()));

        vo.setActions(new ArrayList<>());
        switch (refund.getStatus()) {
            case "APPLYING" -> {
                if (isBuyerView) {
                    vo.getActions().add("CANCEL");
                } else {
                    vo.getActions().add("AGREE");
                    vo.getActions().add("REJECT");
                }
            }
            case "WAIT_RETURN" -> {
                if (isBuyerView) {
                    vo.getActions().add("FILL_LOGISTICS");
                    vo.getActions().add("CANCEL");
                }
            }
            case "WAIT_RECEIVE" -> {
                if (!isBuyerView) {
                    vo.getActions().add("CONFIRM_RECEIVE");
                }
            }
            case "JURY_EVIDENCE" -> {
                // 举证期：买卖双方都可提交证据
                vo.getActions().add("EVIDENCE");
            }
            case "REJECTED" -> {
                if (isBuyerView && refund.getRejectAt() != null
                        && refund.getRejectAt().plusDays(REVISE_WINDOW_DAYS).isAfter(LocalDateTime.now())) {
                    vo.getActions().add("REVISE");
                    vo.getActions().add("JURY");
                }
            }
            default -> {
                // 终态无动作
            }
        }

        JuryCase juryCase = juryCaseMapper.selectOne(Wrappers.<JuryCase>lambdaQuery()
                .eq(JuryCase::getRefundId, refund.getId())
                .last("LIMIT 1"));
        if (juryCase != null) {
            vo.setJuryCaseId(juryCase.getId());
        }
        return vo;
    }

    /**
     * 聊天通知：以 platform 系统账号名义落一条站内消息（发送失败仅记日志，不阻断业务）。
     */
    private void notify(Long toUserId, Long itemId, String content) {
        try {
            ChatMessage message = new ChatMessage();
            message.setFromUserId(resolvePlatformUserId());
            message.setToUserId(toUserId);
            message.setItemId(itemId);
            message.setContent(content);
            chatMessageMapper.insert(message);
        } catch (Exception e) {
            log.warn("售后通知发送失败（to={}）：{}", toUserId, e.getMessage());
        }
    }

    private Long resolvePlatformUserId() {
        if (platformUserId == null) {
            User platform = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                    .eq(User::getUsername, PLATFORM_USERNAME)
                    .last("LIMIT 1"));
            if (platform == null) {
                throw BusinessException.param("平台系统账号缺失，请先执行 db/p2-refund.sql");
            }
            platformUserId = platform.getId();
        }
        return platformUserId;
    }

    private String maskNickname(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return "匿名用户";
        }
        int length = nickname.length();
        if (length == 1) {
            return nickname + "**";
        }
        return nickname.charAt(0) + "***" + nickname.charAt(length - 1);
    }

    private String writeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            log.warn("售后图片序列化失败", e);
            return "";
        }
    }

    private List<String> readImages(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("售后图片反序列化失败：{}", json);
            return new ArrayList<>();
        }
    }
}
