package com.brix.campus.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * 申诉复核 AI 建议（后台管理环节 AI 应用）。
 *
 * <p>对照原审核留痕、被判文案与申诉理由输出，仅供审核员处理申诉时参考；
 * 最终维持或改判由审核员决定。</p>
 */
@Data
public class AppealReviewResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 建议：MAINTAIN 维持原判 / OVERTURN 建议改判 / UNCERTAIN 证据不足难判断 */
    private String suggestion;

    /** 置信度 0~1 */
    private Double confidence;

    /** 建议理由（对照原文与申诉理由的具体依据） */
    private String reason;
}
