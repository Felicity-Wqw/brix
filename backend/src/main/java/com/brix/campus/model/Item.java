package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体（物理表 {@code t_item}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Item extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 标题，≤40 字 */
    private String title;

    /** 描述，≤2000 字 */
    private String description;

    /** 分类 ID */
    @TableField("category_id")
    private Long categoryId;

    /** 现价，0 < price ≤ 99999.99 */
    private BigDecimal price;

    /** 原价，可选，填写时须 ≥ 现价 */
    @TableField("original_price")
    private BigDecimal originalPrice;

    /** 成色：BRAND_NEW / LIKE_NEW / LIGHT_USE / HEAVY_USE */
    @TableField("condition_level")
    private String conditionLevel;

    /** 校区（名称快照，展示与历史订单用，不因校区改名而变化） */
    private String campus;

    /** 所属学校 ID（筛选与统计维度，历史数据可空） */
    @TableField("school_id")
    private Long schoolId;

    /** 所属校区 ID（筛选与统计维度，历史数据可空） */
    @TableField("campus_id")
    private Long campusId;

    /** 交易方式：MEET / SHIP / BOTH */
    @TableField("trade_type")
    private String tradeType;

    /** 联系方式 */
    private String contact;

    /** 状态：PENDING / ON_SALE / OFF_SHELF / SOLD / REJECTED */
    private String status;

    /** 风险级别：LOW / MEDIUM / HIGH（NULL 表示改造前的历史数据，视为已放行） */
    @TableField("risk_level")
    private String riskLevel;

    /** 判定来源：WORD / RULE / AI / MANUAL / LEGACY */
    @TableField("audit_source")
    private String auditSource;

    /** 判定说明：命中词 / AI 理由 / 人工驳回原因 */
    @TableField("audit_reason")
    private String auditReason;

    /** 最后一次人工审核人 ID（自动分流时为 NULL） */
    @TableField("audit_by")
    private Long auditBy;

    /** 最后一次人工审核时间 */
    @TableField("audit_at")
    private LocalDateTime auditAt;

    /** 首图 URL（与图片 sort 最小者同步） */
    @TableField("cover_image")
    private String coverImage;

    /** 浏览量 */
    @TableField("view_count")
    private Integer viewCount;

    /** 收藏数 */
    @TableField("favorite_count")
    private Integer favoriteCount;

    /** 品牌型号（可选，≤60 字） */
    @TableField("brand_model")
    private String brandModel;

    /** 卖家 ID */
    @TableField("seller_id")
    private Long sellerId;
}
