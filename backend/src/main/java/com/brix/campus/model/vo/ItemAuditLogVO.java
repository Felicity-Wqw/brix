package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审核日志出参（后台「审核日志」页 / 商品审核轨迹）。
 *
 * <p>除了日志表里的原始码值，额外带上中文文案（{@code riskLevelDesc} 等），
 * 让前端只负责排版、不负责翻译枚举——枚举只有后端一份定义，前端写死文案迟早对不上。</p>
 */
@Data
public class ItemAuditLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日志 ID */
    private Long id;

    /** 商品 ID */
    private Long itemId;

    /** 卖家 ID */
    private Long sellerId;

    // ---------------------- 商品信息快照 ----------------------

    /** 标题快照 */
    private String itemTitle;

    /** 价格快照 */
    private BigDecimal itemPrice;

    /** 首图快照 */
    private String itemCover;

    /** 分类名快照 */
    private String categoryName;

    // ---------------------- 卖家信息快照 ----------------------

    /** 卖家登录名 */
    private String sellerUsername;

    /** 卖家昵称 */
    private String sellerNickname;

    /** 卖家学校 */
    private String sellerSchool;

    /** 卖家校区 */
    private String sellerCampus;

    /** 卖家信用分 */
    private Integer sellerCredit;

    // ---------------------- 第一道闸 ----------------------

    /** 第一道闸结果码：PASS / MILD_HIT / BLACK_HIT */
    private String gate1Result;

    /** 第一道闸结果文案 */
    private String gate1ResultDesc;

    /** 命中的具体词 */
    private String gate1Word;

    /** 命中词所属类目 */
    private String gate1Category;

    // ---------------------- 第二道闸 ----------------------

    /** 第二道闸结果码：PASS / UNCERTAIN / BAN / UNAVAILABLE */
    private String gate2Result;

    /** 第二道闸结果文案 */
    private String gate2ResultDesc;

    /** AI 原始判定值 */
    private String gate2Verdict;

    /** AI 给出的理由 */
    private String gate2Reason;

    // ---------------------- 结论 ----------------------

    /** 风险级别码 */
    private String riskLevel;

    /** 风险级别文案 */
    private String riskLevelDesc;

    /** 本次审核后商品落入的状态码 */
    private String decisionStatus;

    /** 状态文案 */
    private String decisionStatusDesc;

    /** 给卖家看的判定说明 */
    private String decisionNote;

    // ---------------------- 人工处置 ----------------------

    /** 处置人 ID */
    private Long handlerId;

    /** 处置人昵称 */
    private String handlerNickname;

    /** 处置时间 */
    private LocalDateTime handledAt;

    /** 处置说明 */
    private String handleNote;

    /** 是否已被人工处置 */
    private Boolean handled;

    /** 审核时间 */
    private LocalDateTime createdAt;
}
