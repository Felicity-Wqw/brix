package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后退款单（物理表 {@code t_refund}）。
 *
 * <p>同一订单同时最多一张进行中的售后单（Service 层保证）；
 * 金额为下单时订单金额快照，退款为沙箱语义（写 refund_time，不做真实资金流转）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Refund extends BaseEntity {

    /** 订单 ID */
    @TableField("order_id")
    private Long orderId;

    /** 买家 ID */
    @TableField("buyer_id")
    private Long buyerId;

    /** 卖家 ID */
    @TableField("seller_id")
    private Long sellerId;

    /** 退款金额（下单时订单金额快照） */
    private BigDecimal amount;

    /** 类型：REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款 */
    private String type;

    /** 状态（见 {@link com.brix.campus.model.enums.RefundStatusEnum}） */
    private String status;

    /** 买家申请原因 */
    private String reason;

    /** 买家凭证图 URL 列表（JSON 数组） */
    private String images;

    /** 卖家回复（同意说明或拒绝理由） */
    @TableField("seller_reply")
    private String sellerReply;

    /** 买家退货运单号（退货退款流程） */
    @TableField("tracking_no")
    private String trackingNo;

    /** 最近一次拒绝时间（重提 5 日窗口起点） */
    @TableField("reject_at")
    private LocalDateTime rejectAt;

    /** 买家修改重提次数 */
    @TableField("revise_count")
    private Integer reviseCount;

    /** 退款完成时间 */
    @TableField("refund_time")
    private LocalDateTime refundTime;

    /** 关闭原因（超时关单 / 裁决支持卖家等） */
    @TableField("close_reason")
    private String closeReason;
}
