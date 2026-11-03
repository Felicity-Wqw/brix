package com.brix.campus.service;

/**
 * 商品发布合规审核（发布/编辑前调用，两道闸：违禁词黑名单 + AI 审核员复审）。
 */
public interface PublishAuditService {

    /**
     * 校验商品发布内容是否合规，不合规直接抛业务异常（商品不落库）。
     *
     * <p>第一道：本地违禁词闸（同步、毫秒级、零成本、断网可用）；
     * 第二道：AI 审核员闸（LLM 不可用/超时/解析失败时自动降级放行）。</p>
     *
     * @param title        商品标题
     * @param description  商品描述
     * @param categoryName 分类名称（可空）
     */
    void check(String title, String description, String categoryName);
}
