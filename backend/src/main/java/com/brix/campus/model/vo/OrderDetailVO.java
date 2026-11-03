package com.brix.campus.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单详情出参：订单快照 + 时间线 + 买卖双方联系方式 + 面交信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderDetailVO extends OrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品快照描述（订单详情不返回完整描述，仅标题/首图/单价） */
    private String buyerNote;

    /** 发货时间 */
    private LocalDateTime shipTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 关闭原因码 */
    private String closeReason;

    /** 关闭原因文案 */
    private String closeReasonDesc;

    /** 买家用户名 */
    private String buyerUsername;

    /** 买家手机号 */
    private String buyerPhone;

    /** 买家校区 */
    private String buyerCampus;

    /** 卖家用户名 */
    private String sellerUsername;

    /** 卖家手机号 */
    private String sellerPhone;

    /** 卖家校区 */
    private String sellerCampus;

    /** 剩余支付秒数（仅 UNPAID 有值，前端倒计时兜底） */
    private Long payRemainSeconds;
}
