package com.brix.campus.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.model.dto.RefundApplyDTO;
import com.brix.campus.model.dto.RefundHandleDTO;
import com.brix.campus.model.dto.RefundLogisticsDTO;
import com.brix.campus.model.vo.RefundVO;
import org.springframework.transaction.annotation.Transactional;

/**
 * 售后退款服务。
 *
 * <p>状态流转规则对齐主流电商现行规则（2025-2026 口径）：
 * 确认收货 15 天内可申请；卖家 36 小时超时默认同意并自动退款；
 * 拒绝后 5 日内可修改重提或申请平台介入；退货环节寄回 7 日超时关单、
 * 卖家确认收货超时自动退款。面交单仅支持「仅退款」。</p>
 */
public interface RefundService {

    /**
     * 买家申请售后。
     *
     * @param buyerId 买家 ID
     * @param dto     申请入参
     * @return 售后 VO
     */
    RefundVO apply(Long buyerId, RefundApplyDTO dto);

    /**
     * 买家撤销售后（仅「卖家处理中」可撤，撤销后可重新申请）。
     *
     * @param userId    当前用户（须为买家本人）
     * @param refundId  售后 ID
     */
    void cancel(Long userId, Long refundId);

    /**
     * 买家修改并重新提交（被拒绝后 5 日内）。
     *
     * @param buyerId  买家 ID
     * @param refundId 售后 ID
     * @param dto      新的申请内容
     * @return 售后 VO
     */
    RefundVO revise(Long buyerId, Long refundId, RefundApplyDTO dto);

    /**
     * 卖家处理（同意 / 拒绝）。
     *
     * @param sellerId 卖家 ID
     * @param refundId 售后 ID
     * @param dto      处理动作
     * @return 售后 VO
     */
    RefundVO handle(Long sellerId, Long refundId, RefundHandleDTO dto);

    /**
     * 买家填写退货运单号（WAIT_RETURN → WAIT_RECEIVE）。
     *
     * @param buyerId  买家 ID
     * @param refundId 售后 ID
     * @param dto      运单号
     * @return 售后 VO
     */
    RefundVO submitLogistics(Long buyerId, Long refundId, RefundLogisticsDTO dto);

    /**
     * 卖家确认收到退货（WAIT_RECEIVE → REFUNDED）。
     *
     * @param sellerId 卖家 ID
     * @param refundId 售后 ID
     * @return 售后 VO
     */
    RefundVO confirmReceive(Long sellerId, Long refundId);

    /**
     * 我申请的售后（买家视角，分页）。
     */
    IPage<RefundVO> pageMyApplied(Long buyerId, long page, long size);

    /**
     * 我收到的售后（卖家视角，分页）。
     */
    IPage<RefundVO> pageMyReceived(Long sellerId, long page, long size);

    /**
     * 售后详情（买卖双方本人可见）。
     */
    RefundVO detail(Long userId, Long refundId);

    /**
     * 【定时任务】卖家 36 小时超时未处理 → 自动同意退款。
     *
     * @return 处理笔数
     */
    int autoAgreeOnSellerTimeout();

    /**
     * 【定时任务】买家 7 日超时未寄回 → 自动关闭售后。
     *
     * @return 处理笔数
     */
    int autoCloseReturnTimeout();

    /**
     * 【定时任务】卖家 7 日超时未确认收货 → 自动退款。
     *
     * @return 处理笔数
     */
    int autoRefundOnReceiveTimeout();

    /**
     * 【定时任务】被拒绝后 5 日买家未重提也未申请介入 → 自动失效关闭。
     *
     * @return 处理笔数
     */
    int autoInvalidateRejected();

    /**
     * 事务性退款收尾：售后单置 REFUNDED + 订单写 refund_time。
     * 供本服务与小法庭裁决复用。
     *
     * @param refundId 售后 ID
     */
    @Transactional(rollbackFor = Exception.class)
    void markRefunded(Long refundId);
}
