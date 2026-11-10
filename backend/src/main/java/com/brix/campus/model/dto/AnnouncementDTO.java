package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 公告新增/修改入参。
 */
@Data
public class AnnouncementDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 公告标题 */
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 60, message = "标题不能超过 60 字")
    private String title;

    /** 公告内容 */
    @NotBlank(message = "公告内容不能为空")
    @Size(max = 200, message = "内容不能超过 200 字")
    private String content;

    /** 1 上架 / 0 下架 */
    private Integer enabled;

    /** 排序，越小越靠前 */
    private Integer sort;
}
