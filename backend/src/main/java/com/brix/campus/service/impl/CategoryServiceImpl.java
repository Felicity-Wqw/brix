package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.brix.campus.mapper.CategoryMapper;
import com.brix.campus.model.Category;
import com.brix.campus.model.vo.CategoryVO;
import com.brix.campus.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类服务实现。
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Override
    public List<CategoryVO> listForFront() {
        List<Category> categories = list(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        List<CategoryVO> result = new ArrayList<>(categories.size());
        for (Category category : categories) {
            result.add(toVO(category));
        }
        return result;
    }

    @Override
    public Category getEntity(Long id) {
        if (id == null) {
            return null;
        }
        return getById(id);
    }

    @Override
    public String nameOf(Long id) {
        Category category = getEntity(id);
        return category == null ? "" : category.getName();
    }

    @Override
    public String displayNameOf(Long id) {
        Category category = getEntity(id);
        if (category == null) {
            return "";
        }
        if (category.getParentId() == null || category.getParentId() == 0L) {
            return category.getName();
        }
        Category parent = getEntity(category.getParentId());
        return parent == null ? category.getName() : parent.getName() + "-" + category.getName();
    }

    /**
     * 实体转 VO。
     *
     * @param category 分类实体
     * @return VO
     */
    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setParentId(category.getParentId());
        vo.setIcon(category.getIcon());
        vo.setSort(category.getSort());
        return vo;
    }
}
