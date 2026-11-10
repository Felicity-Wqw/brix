package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 卖家处理售后入参（同意 / 拒绝）。
 */
@Data
public class RefundHandleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 处理动作：AGREE 同意 / REJECT 拒绝 */
    @NotBlank(message = "处理动作不能为空")
    private String action;

    /** 卖家回复说明（拒绝时建议填写） */
    @Size(max = 300, message = "回复说明不能超过 300 字")
    private String reply;
}
