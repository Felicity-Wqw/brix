package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 后台封禁 / 解封用户入参（仅 OPERATOR）。
 */
@Data
public class UserStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态：NORMAL 正常 / DISABLED 封禁 */
    @NotBlank(message = "目标状态不能为空")
    private String status;

    /** 操作原因（封禁建议填写，≤100 字） */
    @Size(max = 100, message = "原因不能超过 100 字")
    private String reason;
}
