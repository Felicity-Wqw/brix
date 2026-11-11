package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 小法庭裁决草稿生成入参（任务 #14）。
 */
@Data
public class JuryDraftDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 裁决方向：BUYER 支持买家 / SELLER 支持卖家 */
    @NotBlank(message = "裁决方向不能为空")
    private String result;
}
