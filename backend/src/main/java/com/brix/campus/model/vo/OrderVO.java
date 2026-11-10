package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表项出参（含前端可用操作标记）。
 */
@Data
public class OrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 商品 ID */
    private Long itemId;

    /** 商品快照标题 */
    private String itemTitle;

    /** 商品快照首图 */
    private String itemCover;

    /** 商品快照单价 */
    private BigDecimal itemPrice;

    /** 成交金额 */
    private BigDecimal amount;

    /** 交易方式码 */
    private String tradeType;

    /** 交易方式文案 */
    private String tradeTypeDesc;

    /** 状态码 */
    private String status;

    /** 状态文案 */
    private String statusDesc;

    /** 支付方式码 */
    private String payMethod;

    /** 支付方式文案 */
    private String payMethodDesc;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 下单时间 */
    private LocalDateTime createdAt;

    /** 买家 ID */
    private Long buyerId;

    /** 买家昵称 */
    private String buyerNickname;

    /** 卖家 ID */
    private Long sellerId;

    /** 卖家昵称 */
    private String sellerNickname;

    /** 面交地点 */
    private String meetPlace;

    /** 面交时间 */
    private LocalDateTime meetTime;

    /** 收货人姓名（邮寄单） */
    private String receiverName;

    /** 收货人手机号（邮寄单） */
    private String receiverPhone;

    /** 收货地址（邮寄单） */
    private String receiverAddress;

    /** 托管状态文案：托管中 / 已结算给卖家（未支付为 null） */
    private String escrowDesc;

    /** 预付款结算给卖家的时间 */
    private LocalDateTime settleTime;

    /** 退款时间（取消已支付订单或售后退款完成时写入） */
    private LocalDateTime refundTime;

    /** 当前用户在该订单中的身份：BUYER / SELLER / ADMIN */
    private String viewerRole;

    /** 是否可支付 */
    private Boolean canPay;

    /** 是否可发货 */
    private Boolean canShip;

    /** 是否可确认收货 */
    private Boolean canConfirm;

    /** 是否可取消 */
    private Boolean canCancel;

    /** 是否可评价 */
    private Boolean canReview;

    /** 是否已评价 */
    private Boolean reviewed;
}
