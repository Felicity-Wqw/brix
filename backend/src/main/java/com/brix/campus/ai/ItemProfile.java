package com.brix.campus.ai;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 归一化后的估价入参（AI 链路第一步 Parse 的输出）。
 */
@Data
public class ItemProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分类 ID */
    private Long categoryId;

    /** 分类名（用于规则引擎的品类基准价匹配） */
    private String categoryName;

    /** 完整分类名（带父类前缀，如「数码电子-手机/平板」，供 AI 按分类词条定制文案） */
    private String categoryFullName;

    /** 品牌型号，如「iPad 9 64G」 */
    private String brandModel;

    /** 成色：BRAND_NEW / LIKE_NEW / LIGHT_USE / HEAVY_USE */
    private String conditionLevel;

    /** 购入月数，可空 */
    private Integer purchaseMonths;

    /** 原价，可空 */
    private BigDecimal originalPrice;
}
