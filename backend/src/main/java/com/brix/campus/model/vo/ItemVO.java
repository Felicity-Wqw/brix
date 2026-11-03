package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品列表项出参。
 */
@Data
public class ItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品 ID */
    private Long id;

    /** 标题 */
    private String title;

    /** 首图 */
    private String coverImage;

    /** 现价 */
    private BigDecimal price;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 成色码 */
    private String conditionLevel;

    /** 成色文案 */
    private String conditionDesc;

    /** 校区 */
    private String campus;

    /** 交易方式码 */
    private String tradeType;

    /** 交易方式文案 */
    private String tradeTypeDesc;

    /** 状态码 */
    private String status;

    /** 状态文案 */
    private String statusDesc;

    /** 浏览量 */
    private Integer viewCount;

    /** 收藏数 */
    private Integer favoriteCount;

    /** 分类 ID */
    private Long categoryId;

    /** 分类名 */
    private String categoryName;

    /** 卖家 ID */
    private Long sellerId;

    /** 卖家昵称 */
    private String sellerNickname;

    /** 品牌型号（可选） */
    private String brandModel;

    /** 发布时间 */
    private LocalDateTime createdAt;
}
