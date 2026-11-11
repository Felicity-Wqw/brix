package com.brix.campus.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 小法庭案件 VO。
 *
 * <p>用户端（{@link AdminJuryCaseVO} 的父类）：买卖双方一律匿名——
 * 只暴露「当事人 A（买家）/ 当事人 B（卖家）」代号，不吐任何用户 ID 与昵称；
 * 管理端子类补充真实身份。</p>
 */
@Data
public class JuryCaseVO {

    private Long id;

    /** 案件编号 */
    private String caseNo;

    /** 阶段：EVIDENCE / VOTING / PENDING_RULING / RULED */
    private String status;

    /** 阶段描述 */
    private String statusDesc;

    /** 纠纷事由 */
    private String reason;

    /** 发起方：BUYER / SELLER */
    private String initiator;

    /** 商品标题快照 */
    private String itemTitle;

    /** 商品首图快照 */
    private String itemCover;

    /** 订单金额 */
    private BigDecimal amount;

    /** 交易方式：MEET / SHIP / BOTH */
    private String tradeType;

    /** 证据列表（按提交时间正序） */
    private List<EvidenceVO> evidences;

    /** 支持买家的票数 */
    private Long voteBuyer;

    /** 支持卖家的票数 */
    private Long voteSeller;

    /** 参与投票总人数 */
    private Long voteTotal;

    /** 支持买家占比（百分数，四舍五入） */
    private Integer voteBuyerPercent;

    /** 支持卖家占比（百分数，四舍五入） */
    private Integer voteSellerPercent;

    /** 当前用户是否可投票（陪审期 + 非当事人 + 未投过） */
    private Boolean canVote;

    /** 当前用户已投的立场（未投为 null） */
    private String myVote;

    /** 投票截止时间 */
    private LocalDateTime voteDeadline;

    /** 举证截止时间 */
    private LocalDateTime evidenceDeadline;

    /** 裁决结果（已裁决时有值）：BUYER / SELLER */
    private String rulingResult;

    /** 裁决理由 */
    private String rulingReason;

    /** AI 预审参考 JSON（争议焦点/证据摘要/倾向），仅供陪审参考，不影响计票；AI 不可用为 null */
    private String aiPreview;

    /** AI 预审生成时间 */
    private LocalDateTime aiPreviewedAt;

    private LocalDateTime ruledAt;

    private LocalDateTime createdAt;

    /**
     * 匿名证据 VO：side 之外不带任何提交者身份。
     */
    @Data
    public static class EvidenceVO {

        /** 提交方视角文案：「当事人 A（买家）」/「当事人 B（卖家）」 */
        private String sideLabel;

        /** 原始方：BUYER / SELLER */
        private String side;

        private String content;

        private List<String> images;

        private LocalDateTime createdAt;
    }
}
