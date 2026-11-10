package com.brix.campus.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 发布 / 编辑商品入参（B01、B05）。
 */
@Data
public class ItemSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 标题，≤40 字 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 40, message = "标题不能超过 40 个字")
    private String title;

    /** 描述，≤2000 字 */
    @NotBlank(message = "描述不能为空")
    @Size(max = 2000, message = "描述不能超过 2000 个字")
    private String description;

    /** 分类 ID */
    @NotNull(message = "分类不能为空")
    private Long categoryId;

    /** 现价 */
    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0 且不超过 99999.99")
    @DecimalMax(value = "99999.99", message = "价格必须大于 0 且不超过 99999.99")
    @Digits(integer = 5, fraction = 2, message = "价格最多两位小数")
    private BigDecimal price;

    /** 原价，可选；填写时必须 ≥ 现价 */
    @DecimalMin(value = "0.01", message = "原价格式不正确")
    @DecimalMax(value = "999999.99", message = "原价格式不正确")
    @Digits(integer = 6, fraction = 2, message = "原价最多两位小数")
    private BigDecimal originalPrice;

    /** 成色：BRAND_NEW / LIKE_NEW / LIGHT_USE / HEAVY_USE */
    @NotBlank(message = "成色不能为空")
    private String conditionLevel;

    /** 校区 */
    @NotBlank(message = "校区不能为空")
    @Size(max = 50, message = "校区名称过长")
    private String campus;

    /** 所属学校 ID（两级联动；可空以兼容旧版客户端） */
    private Long schoolId;

    /** 所属校区 ID（两级联动；可空以兼容旧版客户端，传了则必须与 schoolId 匹配） */
    private Long campusId;

    /** 交易方式：MEET / SHIP / BOTH */
    @NotBlank(message = "交易方式不能为空")
    private String tradeType;

    /** 联系方式 */
    @Size(max = 50, message = "联系方式过长")
    private String contact;

    /** 品牌型号，可选，≤60 字 */
    @Size(max = 60, message = "品牌型号过长")
    private String brandModel;

    /** 图片 URL 列表，0~9 张，顺序即展示顺序 */
    private List<String> images;
}
