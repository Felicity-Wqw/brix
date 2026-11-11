package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 小法庭案件（物理表 {@code t_jury_case}）。
 *
 * <p>一张售后单最多一桩案件（唯一索引兜底）；买卖双方真实身份仅管理端可见，
 * 用户端一律匿名展示为「当事人 A（买家）/ 当事人 B（卖家）」。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JuryCase extends BaseEntity {

    /** 案件编号（如 JF20261107-001） */
    @TableField("case_no")
    private String caseNo;

    /** 售后单 ID（唯一） */
    @TableField("refund_id")
    private Long refundId;

    /** 订单 ID */
    @TableField("order_id")
    private Long orderId;

    /** 买家 ID（仅管理端可见） */
    @TableField("buyer_id")
    private Long buyerId;

    /** 卖家 ID（仅管理端可见） */
    @TableField("seller_id")
    private Long sellerId;

    /** 阶段（见 {@link com.brix.campus.model.enums.JuryStageEnum}） */
    private String status;

    /** 纠纷事由（发起介入一方的陈述） */
    private String reason;

    /** 发起方：BUYER / SELLER */
    private String initiator;

    /** 举证截止时间（开庭 + 24h） */
    @TableField("evidence_deadline")
    private LocalDateTime evidenceDeadline;

    /** 投票截止时间（举证截止 + 48h） */
    @TableField("vote_deadline")
    private LocalDateTime voteDeadline;

    /** 裁决结果：BUYER 支持买家 / SELLER 支持卖家 */
    @TableField("ruling_result")
    private String rulingResult;

    /** 裁决理由 */
    @TableField("ruling_reason")
    private String rulingReason;

    /** 裁决管理员 ID */
    @TableField("ruling_by")
    private Long rulingBy;

    /** 裁决时间 */
    @TableField("ruled_at")
    private LocalDateTime ruledAt;

    /** AI 预审参考 JSON（争议焦点/证据摘要/倾向），举证期结束进入投票期时生成；AI 不可用为 null */
    @TableField("ai_preview")
    private String aiPreview;

    /** AI 预审生成时间 */
    @TableField("ai_previewed_at")
    private LocalDateTime aiPreviewedAt;
}
