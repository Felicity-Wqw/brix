package com.brix.campus.model.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审核日志查询条件（后台「审核日志」页用）。
 *
 * <p>日志表是 append-only 的，所以这里的条件全部是精确匹配或前后缀匹配，
 * 不涉及状态机——已经发生的事没有「状态」可筛，只有「哪一类结论」可筛。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ItemAuditLogQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 商品 ID（某件商品的完整审核轨迹） */
    private Long itemId;

    /** 卖家 ID（某个卖家的全部过审记录，用于识别惯犯） */
    private Long sellerId;

    /** 风险级别：LOW / MEDIUM / HIGH，空表示不限 */
    private String riskLevel;

    /** 第一道闸结果：PASS / MILD_HIT / BLACK_HIT，空表示不限 */
    private String gate1Result;

    /** 第二道闸结果：PASS / UNCERTAIN / BAN / UNAVAILABLE，空表示不限 */
    private String gate2Result;

    /** 关键词：匹配商品标题或卖家昵称 */
    private String keyword;

    /**
     * 是否已人工处置。
     *
     * <p>{@code true} 只看审核员动过手的，{@code false} 只看「自动判完还没人看」的，
     * {@code null} 不限。答辩演示「机器判了、人还没复核」的积压情况时用得上。</p>
     */
    private Boolean handled;

    /** 审核时间下界（含），格式 yyyy-MM-dd */
    private String startDate;

    /** 审核时间上界（含当天），格式 yyyy-MM-dd */
    private String endDate;
}
