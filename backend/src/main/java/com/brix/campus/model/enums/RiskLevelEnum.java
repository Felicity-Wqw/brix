package com.brix.campus.model.enums;

/**
 * 商品发布风险级别（审核三级分流的第一维）。
 *
 * <p>分流口径——级别决定商品落库后的初始状态，而不是决定「能不能发布」：</p>
 * <ul>
 *   <li>{@link #LOW} 低风险 → 直接 {@code ON_SALE}，用户发完即可见，体验与改造前一致；</li>
 *   <li>{@link #MEDIUM} 中风险 → {@code PENDING}，进人工审核队列，审核员通过后才上架；</li>
 *   <li>{@link #HIGH} 高风险 → {@code REJECTED}，落库留痕但前台不可见，卖家可见驳回原因，
 *       审核员可在后台纠正误杀（驳回转通过）。</li>
 * </ul>
 *
 * <p>为什么要落库而不是直接抛异常：平台侧必须留下审计痕迹（谁在什么时候因为哪条规则被拦），
 * 否则卖家无法申诉、审核员无法复核、统计也算不出拦截率。</p>
 */
public enum RiskLevelEnum {

    /** 低风险：放行直发 */
    LOW("LOW", "低风险", ItemStatusEnum.ON_SALE.getCode()),

    /** 中风险：转人工复核 */
    MEDIUM("MEDIUM", "中风险", ItemStatusEnum.PENDING.getCode()),

    /** 高风险：驳回，留痕可申诉 */
    HIGH("HIGH", "高风险", ItemStatusEnum.REJECTED.getCode());

    private final String code;
    private final String desc;
    /** 该风险级别对应的落库初始状态 */
    private final String initialStatus;

    RiskLevelEnum(String code, String desc, String initialStatus) {
        this.code = code;
        this.desc = desc;
        this.initialStatus = initialStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public String getInitialStatus() {
        return initialStatus;
    }

    public static RiskLevelEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (RiskLevelEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    public static String descOf(String code) {
        RiskLevelEnum value = of(code);
        return value == null ? code : value.desc;
    }
}
