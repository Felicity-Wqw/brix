package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 分类出参（后台列表额外带商品数）。
 */
@Data
public class CategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分类 ID */
    private Long id;

    /** 分类名称 */
    private String name;

    /** 父分类 ID，0=一级分类 */
    private Long parentId;

    /** 图标名 */
    private String icon;

    /** 排序值 */
    private Integer sort;

    /** 该分类下商品数（后台分类管理使用） */
    private Long itemCount;
}
