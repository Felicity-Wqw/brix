package com.brix.campus.model.enums;

/**
 * 用户角色。
 *
 * <p>P2 恢复两类后台角色，按最小权限原则拆分：</p>
 * <ul>
 *   <li>{@link #REVIEWER} 审核员：只管内容（商品审核、举报处理、强制下架），不碰经营数据；</li>
 *   <li>{@link #OPERATOR} 运营：只管经营（数据看板、用户管理、订单查询、分类管理），不动内容审核。</li>
 * </ul>
 *
 * <p>交叉访问由 {@code SecurityConfig} 的 URL 规则直接 403，Service 层不再重复判断角色。</p>
 */
public enum RoleEnum {

    /** 普通用户（学生，既可买也可卖） */
    USER("USER", "普通用户"),

    /** 审核员（后台）：商品审核、举报处理 */
    REVIEWER("REVIEWER", "审核员"),

    /** 运营（后台）：数据看板、用户 / 订单 / 分类管理 */
    OPERATOR("OPERATOR", "运营");

    private final String code;
    private final String desc;

    RoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static RoleEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (RoleEnum value : values()) {
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
        RoleEnum value = of(code);
        return value == null ? code : value.desc;
    }

    /**
     * 是否为后台角色（可进入管理后台）。
     *
     * <p>{@code USER} 不是后台角色，登录后台一律返回「无后台权限」。</p>
     *
     * @param code 角色码
     * @return 是否可进后台
     */
    public static boolean isAdmin(String code) {
        return REVIEWER.code.equals(code) || OPERATOR.code.equals(code);
    }
}
