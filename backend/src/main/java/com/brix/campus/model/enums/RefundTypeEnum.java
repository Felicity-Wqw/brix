package com.brix.campus.model.enums;

/**
 * 售后类型。
 *
 * <p>面交单当面验货、无物流环节，只允许「仅退款」（线下当面退）；
 * 退货退款仅邮寄单可申请。</p>
 */
public enum RefundTypeEnum {

    /** 仅退款（不退货）：面交/邮寄单均可申请 */
    REFUND_ONLY("REFUND_ONLY", "仅退款"),
    /** 退货退款：需寄回商品，仅邮寄单可申请 */
    RETURN_REFUND("RETURN_REFUND", "退货退款");

    private final String code;
    private final String desc;

    RefundTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static RefundTypeEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (RefundTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
