package com.brix.campus.common.validation;

/**
 * 校验正则常量（DTO 注解与业务代码共用）。
 *
 * <p>为什么集中在这里：手机号这条规则同时出现在注册、改资料、下单收货人三处，
 * 之前每处各写一遍字符串，改的时候极易漏掉一处，出现「注册校验严、改资料校验松」
 * 的割裂。集中成常量后，改一行即全站生效。</p>
 *
 * <p>注意：注解的 {@code regexp} 要求编译期常量，因此这里必须是 {@code static final}
 * 且由常量表达式初始化，不能改成方法调用。</p>
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /**
     * 手机号：空 或 中国大陆 11 位手机号。
     *
     * <p>号段规则取自工信部现行规划：1 开头，第二位 3-9。
     * 只校验 `\d{11}` 是不够的——{@code 00000000000}、{@code 12345678901}
     * 这类明显非法的值会一路带进库，后续发短信/联系卖家时才暴露。</p>
     */
    public static final String PHONE = "^$|^1[3-9]\\d{9}$";

    /** 手机号校验失败提示（与 {@link #PHONE} 保持同一套口径） */
    public static final String PHONE_MESSAGE = "手机号格式不正确，应为 11 位大陆手机号";
}
