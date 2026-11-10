package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端小法庭终审裁决入参。
 */
@Data
public class AdminRulingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 裁决结果：BUYER 支持买家（退款）/ SELLER 支持卖家（关闭售后） */
    @NotBlank(message = "裁决结果不能为空")
    private String result;

    /** 裁决理由（必填，会随结果通知双方） */
    @NotBlank(message = "裁决理由不能为空")
    @Size(max = 300, message = "裁决理由不能超过 300 字")
    private String reason;
}
