package com.brix.campus.service;

import com.brix.campus.model.dto.AuditDecision;

import java.math.BigDecimal;

/**
 * 商品发布合规审核（发布 / 编辑前调用，三道闸叠加取最高风险级别）。
 *
 * <p>三道闸的分工：</p>
 * <ol>
 *   <li><b>违禁词闸</b>（同步、毫秒级、零成本、断网可用）：命中即判高危；</li>
 *   <li><b>规则闸</b>（同步、确定性）：软风险词与高价值品类判中危，转人工复核；</li>
 *   <li><b>AI 审核员闸</b>（异步 LLM）：识别谐音变体、隐晦暗示、挂羊头卖狗肉等词表盲区。</li>
 * </ol>
 *
 * <p>关键改造（P2）：审核<b>不再抛异常中断发布</b>，而是返回 {@link AuditDecision}，
 * 由商品服务按级别写不同状态（低→在售 / 中→待审核 / 高→驳回）。
 * 这样平台才能留下完整的审核痕迹，卖家可申诉、审核员可复核、统计可算拦截率。</p>
 *
 * <p>降级原则不变：AI 不可用 / 超时 / 解析失败，一律只按前两道闸的结论走，绝不阻断发布。</p>
 */
public interface PublishAuditService {

    /**
     * 判定商品发布内容的风险级别。
     *
     * @param title        商品标题
     * @param description  商品描述
     * @param categoryName 分类名称（可空）
     * @param price        价格（用于高价值品类规则，可空）
     * @return 审核结论，永不为 null；无任何风险时返回低风险
     */
    AuditDecision judge(String title, String description, String categoryName, BigDecimal price);
}
