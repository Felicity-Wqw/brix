package com.brix.campus.model.enums;

/**
 * 售后单状态机（对齐主流电商现行规则）。
 *
 * <p>流转：APPLYING → (同意) WAIT_RETURN → WAIT_RECEIVE → REFUNDED；
 * APPLYING → (拒绝) REJECTED → (重提) APPLYING / (介入) JURY_EVIDENCE → JURY_VOTING
 * → PENDING_RULING → (裁决支持买家) REFUNDED / (裁决支持卖家) CLOSED。
 * APPLYING 期间买家可撤销 → CANCELED。</p>
 */
public enum RefundStatusEnum {

    /** 申请中，等待卖家处理（36h 超时自动同意） */
    APPLYING("APPLYING", "卖家处理中"),
    /** 退货退款已同意，待买家寄回（7 日超时关单） */
    WAIT_RETURN("WAIT_RETURN", "待买家寄回"),
    /** 买家已寄回，待卖家确认收货（超时自动退款） */
    WAIT_RECEIVE("WAIT_RECEIVE", "待卖家确认收货"),
    /** 已退款（终态） */
    REFUNDED("REFUNDED", "已退款"),
    /** 卖家拒绝，买家 5 日内可重提或申请介入 */
    REJECTED("REJECTED", "卖家已拒绝"),
    /** 小法庭举证期（24h） */
    JURY_EVIDENCE("JURY_EVIDENCE", "小法庭举证中"),
    /** 小法庭陪审期（48h） */
    JURY_VOTING("JURY_VOTING", "小法庭陪审中"),
    /** 陪审期满，待平台管理员终审 */
    PENDING_RULING("PENDING_RULING", "待平台裁决"),
    /** 已关闭（终态：超时未寄回 / 裁决支持卖家 / 重提超时失效） */
    CLOSED("CLOSED", "已关闭"),
    /** 买家撤销（退款完成前可撤销，之后可重新申请） */
    CANCELED("CANCELED", "买家已撤销");

    private final String code;
    private final String desc;

    RefundStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static RefundStatusEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (RefundStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    public static String descOf(String code) {
        RefundStatusEnum value = of(code);
        return value == null ? code : value.desc;
    }

    /** 未走到终态（同一订单存在该集合状态的售后单时，不允许重复申请） */
    public static boolean isOngoing(String code) {
        return !isFinished(code);
    }

    /** 终态（退款完成 / 关闭 / 撤销），此后可重新发起售后 */
    public static boolean isFinished(String code) {
        return REFUNDED.code.equals(code) || CLOSED.code.equals(code)
                || CANCELED.code.equals(code);
    }
}
