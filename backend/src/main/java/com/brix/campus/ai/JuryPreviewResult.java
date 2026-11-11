package com.brix.campus.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 小法庭 AI 预审参考（纠纷环节 AI 应用）。
 *
 * <p>举证期结束进入投票期时由平台 AI 生成，仅供陪审员快速抓住争议核心，
 * <b>不影响计票、不参与裁决</b>。AI 不可用或调用失败时整份结果为 null，
 * 前端不展示卡片。</p>
 */
@Data
public class JuryPreviewResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 争议焦点（2~4 条，每条一句话） */
    private List<String> disputedPoints;

    /** 买家方证据摘要（一段话） */
    private String buyerSummary;

    /** 卖家方证据摘要（一段话） */
    private String sellerSummary;

    /** 倾向：BUYER / SELLER / NEUTRAL（证据不足必须给 NEUTRAL） */
    private String lean;

    /** 倾向理由 */
    private String leanReason;
}
