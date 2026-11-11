package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 商品申诉创建入参。
 */
@Data
public class AppealCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 申诉商品 */
    @NotNull(message = "商品不能为空")
    private Long itemId;

    /** 申诉理由（10~500 字） */
    @NotBlank(message = "申诉理由不能为空")
    @Size(min = 10, max = 500, message = "申诉理由需 10~500 字")
    private String reason;
}
