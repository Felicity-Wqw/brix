package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品申诉（物理表 {@code t_appeal}）。
 *
 * <p>商品被审核拒绝（REJECTED）或被运营处置下架后，卖家可提交申诉；
 * 审核员（REVIEWER）处理：维持原判或改判（改判恢复在售）。
 * AI 复核建议（ai_review）仅作处理参考。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_appeal")
public class Appeal extends BaseEntity {

    /** 申诉状态：待处理 */
    public static final String PENDING = "PENDING";
    /** 申诉状态：维持原判 */
    public static final String MAINTAINED = "MAINTAINED";
    /** 申诉状态：改判（恢复在售） */
    public static final String OVERTURNED = "OVERTURNED";

    /** 申诉人（卖家） */
    @TableField("user_id")
    private Long userId;

    /** 申诉商品 */
    @TableField("item_id")
    private Long itemId;

    /** 申诉理由 */
    @TableField("reason")
    private String reason;

    /** 状态：PENDING / MAINTAINED / OVERTURNED */
    @TableField("status")
    private String status;

    /** AI 复核建议 JSON（suggestion/confidence/reason），处理参考 */
    @TableField("ai_review")
    private String aiReview;

    /** 处理回复 */
    @TableField("reply")
    private String reply;

    /** 处理审核员 */
    @TableField("handler_id")
    private Long handlerId;

    /** 处理时间 */
    @TableField("handled_at")
    private java.time.LocalDateTime handledAt;
}
