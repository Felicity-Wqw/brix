package com.brix.campus.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 求购墙发布入参。
 */
@Data
public class WantSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 求购标题 */
    @NotBlank(message = "求购标题不能为空")
    @Size(max = 60, message = "标题不能超过 60 字")
    private String title;

    /** 需求描述 */
    @Size(max = 500, message = "描述不能超过 500 字")
    private String description;

    /** 预算上限（可空 = 面议） */
    @DecimalMin(value = "0.01", message = "预算需大于 0")
    private BigDecimal budget;

    /** 期望交易校区 */
    @Size(max = 50, message = "校区不能超过 50 字")
    private String campus;

    /** 学校 ID（可空，选了则展示学校名） */
    private Long schoolId;
}
