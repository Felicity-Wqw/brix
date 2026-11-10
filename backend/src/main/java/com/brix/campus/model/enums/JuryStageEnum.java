package com.brix.campus.model.enums;

/**
 * 小法庭案件阶段。
 *
 * <p>开庭（EVIDENCE 举证 24h）→ 陪审（VOTING 48h）→ 期满推送管理端
 * （PENDING_RULING）→ 管理员终审（RULED）。全部由定时任务自动流转。</p>
 */
public enum JuryStageEnum {

    /** 举证期：买卖双方提交文字+图片证据，24 小时 */
    EVIDENCE("EVIDENCE", "举证期"),
    /** 陪审期：平台用户匿名投票，48 小时 */
    VOTING("VOTING", "陪审期"),
    /** 陪审期满，待平台管理员终审 */
    PENDING_RULING("PENDING_RULING", "待平台裁决"),
    /** 已裁决（终态） */
    RULED("RULED", "已裁决");

    private final String code;
    private final String desc;

    JuryStageEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static JuryStageEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (JuryStageEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    public static String descOf(String code) {
        JuryStageEnum value = of(code);
        return value == null ? code : value.desc;
    }
}
