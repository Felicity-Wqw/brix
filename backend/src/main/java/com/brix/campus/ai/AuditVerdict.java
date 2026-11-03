package com.brix.campus.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 审核员复审结论（发布合规第二道闸）。
 *
 * <p>四种判定结果对应四种处理方式：
 * PASS=放行上架；REJECT_BAN=禁止上架（法律红线/假货/站外引流）；
 * REJECT_REVISE=修改后可重审（广告用语/信息不实）；REPORT_RISK=上报风控（高风险金融类）。</p>
 */
@Data
public class AuditVerdict implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审核结果：PASS / REJECT_BAN / REJECT_REVISE / REPORT_RISK */
    private String verdict;

    /** 违规原文片段（无违规为空串） */
    private String violationText;

    /** 法规依据（法律/规章名称 + 要点） */
    private String legalBasis;

    /** 违规原因说明 */
    private String reason;

    /** 修改建议（无违规为空串） */
    private String suggestion;

    /** 判定常量：审核通过 */
    public static final String PASS = "PASS";
    /** 判定常量：驳回-禁止上架 */
    public static final String REJECT_BAN = "REJECT_BAN";
    /** 判定常量：驳回-修改后可重审 */
    public static final String REJECT_REVISE = "REJECT_REVISE";
    /** 判定常量：上报风控 */
    public static final String REPORT_RISK = "REPORT_RISK";
}
