package com.brix.campus.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分类实体（物理表 {@code t_category}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Category extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 分类名，唯一 */
    private String name;

    /** 父分类 ID，0=一级分类（发布页级联选择用） */
    private Long parentId;

    /** 图标名（Element Plus 图标名） */
    private String icon;

    /** 排序值，升序展示 */
    private Integer sort;
}
