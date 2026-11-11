package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品审核日志（物理表 {@code t_item_audit_log}）——<b>不可变、只增不改</b>。
 *
 * <p>为什么单独建表、而且不继承 {@link BaseEntity}：</p>
 * <ul>
 *   <li>不继承 BaseEntity 是因为它带 {@code updated_at} 和 {@code deleted}，
 *       而审核日志的语义是「发生过就不许再变」，多这两个字段等于给了改和删的口子；</li>
 *   <li>要落<b>快照</b>而不是外键关联：商品被卖家改标题、改价格、改图片后，
 *       {@code t_item} 里只剩最新值；卖家改昵称、换校区、信用分变动后，{@code t_user} 也一样。
 *       审核留痕要回答的是「<i>当时</i>审的是什么内容、由什么人发布」，
 *       必须把商品与卖家信息按审核那一刻的样子抄一份存下来，不依赖 join。</li>
 * </ul>
 *
 * <p>一条日志 = 一次完整的审核证据链：谁发的（卖家快照）→ 发了什么（商品快照）→
 * 第一道闸命中什么词 → 第二道闸 AI 说了什么 → 结论是什么级别 → 落到什么状态 → 什么时候审的。
 * 低风险直接上架的商品同样写一条，平台才能证明「这条内容我确实审过了」。</p>
 *
 * <p>人工处置不回写新行，而是在最近一条日志上补 {@code handlerId / handledAt / handleNote}，
 * 保持「一次自动审核一条记录」的粒度，同时能一眼看出这条判定最终被人确认还是被推翻。</p>
 */
@Data
public class ItemAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品 ID */
    private Long itemId;

    /** 卖家 ID */
    private Long sellerId;

    // ---------------------- 商品信息快照（审核当时的样子） ----------------------

    /** 标题快照 */
    private String itemTitle;

    /** 价格快照 */
    private BigDecimal itemPrice;

    /** 首图快照 */
    private String itemCover;

    /** 分类名快照（如「数码电子 / 手机/平板」） */
    private String categoryName;

    // ---------------------- 卖家信息快照（审核当时的样子） ----------------------

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

    // ---------------------- 第一道闸：关键词闸 ----------------------

    /** PASS 未命中 / MILD_HIT 命中轻度词 / BLACK_HIT 命中黑名单 */
    private String gate1Result;

    /** 命中的具体词，未命中为 null */
    private String gate1Word;

    /** 命中词所属类目 */
    private String gate1Category;

    // ---------------------- 第二道闸：AI 审核 ----------------------

    /** PASS 通过 / UNCERTAIN 模糊不确定 / BAN 判定无法上架 / UNAVAILABLE 未启用或调用失败 */
    private String gate2Result;

    /** AI 原始判定值（对照 prompt 契约用） */
    private String gate2Verdict;

    /** AI 给出的理由 */
    private String gate2Reason;

    /** AI 闸逐图审核明细 JSON：[{index,url,verdict,reason}]（无图片审核为 NULL） */
    @TableField("gate2_findings")
    private String gate2Findings;

    // ---------------------- 分流结论 ----------------------

    /** LOW / MEDIUM / HIGH */
    private String riskLevel;

    /** 本次审核后商品落到什么状态：ON_SALE / PENDING / REJECTED */
    private String decisionStatus;

    /** 给卖家看的判定说明（原因 + 修改建议） */
    private String decisionNote;

    // ---------------------- 人工处置回写 ----------------------

    /** 人工处置人 ID，未处置为 null */
    private Long handlerId;

    /** 人工处置时间 */
    private LocalDateTime handledAt;

    /** 人工处置说明（通过理由 / 驳回原因） */
    private String handleNote;

    /** 审核时间（插入时自动填充） */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
