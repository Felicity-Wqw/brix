package com.brix.campus.service;

import com.brix.campus.model.Category;
import com.brix.campus.model.vo.CategoryVO;

import java.util.List;

/**
 * 分类服务。
 */
public interface CategoryService {

    /**
     * 前台分类列表（按 sort 升序）。
     *
     * @return 分类列表
     */
    List<CategoryVO> listForFront();

    /**
     * 取分类实体。
     *
     * @param id 分类 ID
     * @return 分类，不存在返回 null
     */
    Category getEntity(Long id);

    /**
     * 取分类名，不存在返回空串。
     *
     * @param id 分类 ID
     * @return 分类名
     */
    String nameOf(Long id);

    /**
     * 取带父类的完整分类名（AI 生成简介按分类词条定制用）。
     *
     * <p>子类返回「父类-子类」（如「数码电子-手机/平板」），一级分类返回自身名称。</p>
     *
     * @param id 分类 ID
     * @return 完整分类名，不存在返回空串
     */
    String displayNameOf(Long id);
}
