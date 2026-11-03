package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

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

    /** 校区 */
    private String campus;

    /** 交易方式：MEET / SHIP / BOTH */
    @TableField("trade_type")
    private String tradeType;

    /** 联系方式 */
    private String contact;

    /** 状态：PENDING / ON_SALE / OFF_SHELF / SOLD / REJECTED */
    private String status;

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
