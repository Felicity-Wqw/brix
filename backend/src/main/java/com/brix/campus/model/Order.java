package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体（物理表 {@code t_order}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Order extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 业务单号：yyyyMMddHHmmss + 6 位随机 */
    @TableField("order_no")
    private String orderNo;

    /** 商品 ID */
    @TableField("item_id")
    private Long itemId;

    /** 买家 ID */
    @TableField("buyer_id")
    private Long buyerId;

    /** 卖家 ID（冗余，省连表判权） */
    @TableField("seller_id")
    private Long sellerId;

    /** 成交金额 */
    private BigDecimal amount;

    /** 商品快照-标题 */
    @TableField("item_title")
    private String itemTitle;

    /** 商品快照-首图 */
    @TableField("item_cover")
    private String itemCover;

    /** 商品快照-单价 */
    @TableField("item_price")
    private BigDecimal itemPrice;

    /** 下单时商品所属学校（统计快照，商品后续删除或改归属也不影响历史统计） */
    @TableField("school_id")
    private Long schoolId;

    /** 下单时商品所属校区（统计快照） */
    @TableField("campus_id")
    private Long campusId;

    /** 实际成交方式：MEET / SHIP */
    @TableField("trade_type")
    private String tradeType;

    /** 状态：UNPAID / TO_SHIP / TO_RECEIVE / TO_MEET / DONE / CANCELED */
    private String status;

    /** 支付方式：WECHAT / ALIPAY / BANK */
    @TableField("pay_method")
    private String payMethod;

    /** 支付时间 */
    @TableField("pay_time")
    private LocalDateTime payTime;

    /** 发货时间 */
    @TableField("ship_time")
    private LocalDateTime shipTime;

    /** 完成时间 */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /** 取消时间 */
    @TableField("cancel_time")
    private LocalDateTime cancelTime;

    /** 关闭原因：TIMEOUT / BUYER_CANCEL / SELLER_CANCEL / SYSTEM */
    @TableField("close_reason")
    private String closeReason;

    /** 面交地点 */
    @TableField("meet_place")
    private String meetPlace;

    /** 面交时间（买卖双方约定） */
    @TableField("meet_time")
    private LocalDateTime meetTime;

    /** 收货人姓名（邮寄单） */
    @TableField("receiver_name")
    private String receiverName;

    /** 收货人手机号（邮寄单） */
    @TableField("receiver_phone")
    private String receiverPhone;

    /** 收货地址（邮寄单） */
    @TableField("receiver_address")
    private String receiverAddress;

    /** 买家备注 */
    @TableField("buyer_note")
    private String buyerNote;

    /** 预付款结算给卖家的时间（确认收货即托管释放；NULL = 尚未结算） */
    @TableField("settle_time")
    private LocalDateTime settleTime;

    /** 退款时间（取消已支付订单或售后退款完成时写入） */
    @TableField("refund_time")
    private LocalDateTime refundTime;
}
