package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 申诉处理入参（审核员）。
 */
@Data
public class AppealHandleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 处理动作：MAINTAINED 维持 / OVERTURNED 改判恢复在售 */
    @NotBlank(message = "处理动作不能为空")
    private String action;

    /** 处理回复（将展示给申诉人） */
    @NotBlank(message = "处理回复不能为空")
    @Size(max = 500, message = "回复不能超过 500 字")
    private String reply;
}
