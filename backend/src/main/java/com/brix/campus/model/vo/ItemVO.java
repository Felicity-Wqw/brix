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

    /** 所属学校 ID（编辑回填与筛选用，历史数据可能为空） */
    private Long schoolId;

    /** 所属校区 ID（编辑回填与筛选用，历史数据可能为空） */
    private Long campusId;

    /** 学校名称（学校+校区两级展示，历史数据可能为空） */
    private String schoolName;

    /** 交易方式码 */
    private String tradeType;

    /** 交易方式文案 */
    private String tradeTypeDesc;

    /** 状态码 */
    private String status;

    /** 状态文案 */
    private String statusDesc;

    /** 风险级别码 LOW / MEDIUM / HIGH（历史数据可能为空） */
    private String riskLevel;

    /** 风险级别文案 */
    private String riskLevelDesc;

    /** 判定来源码 WORD / RULE / AI / MANUAL / LEGACY */
    private String auditSource;

    /** 判定说明：命中词 / AI 理由 / 人工驳回原因（卖家可见，便于修改后重发） */
    private String auditReason;

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
