package com.brix.campus.model.enums;

/**
 * 订单状态（见 PRD 订单状态机）。
 */
public enum OrderStatusEnum {

    /** 待付款 */
    UNPAID("UNPAID", "待付款"),
    /** 待发货（邮寄单已支付） */
    TO_SHIP("TO_SHIP", "待发货"),
    /** 待收货（邮寄单已发货） */
    TO_RECEIVE("TO_RECEIVE", "待收货"),
    /** 待面交（面交单已支付，等待线下见面完成交易） */
    TO_MEET("TO_MEET", "待面交"),
    /** 已完成 */
    DONE("DONE", "已完成"),
    /** 已取消 */
    CANCELED("CANCELED", "已取消");

    private final String code;
    private final String desc;

    OrderStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static OrderStatusEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (OrderStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return of(code) != null;
    }

    public static String descOf(String code) {
        OrderStatusEnum value = of(code);
        return value == null ? code : value.desc;
    }

    /** 是否属于「已发生支付」的状态（统计成交额口径，PRD 8-#6） */
    public static boolean isPaid(String code) {
        return TO_SHIP.code.equals(code) || TO_RECEIVE.code.equals(code)
                || TO_MEET.code.equals(code) || DONE.code.equals(code);
    }

    /** 是否属于进行中（未完成、未取消）状态 */
    public static boolean isOngoing(String code) {
        return UNPAID.code.equals(code) || TO_SHIP.code.equals(code)
                || TO_RECEIVE.code.equals(code) || TO_MEET.code.equals(code);
    }
}
