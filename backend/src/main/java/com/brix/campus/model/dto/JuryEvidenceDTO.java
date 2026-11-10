package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 小法庭提交证据入参。
 */
@Data
public class JuryEvidenceDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 证据说明（文字） */
    @NotBlank(message = "证据说明不能为空")
    @Size(max = 500, message = "证据说明不能超过 500 字")
    private String content;

    /** 证据图片（最多 3 张） */
    @Size(max = 3, message = "证据图片最多 3 张")
    private List<String> images;
}
