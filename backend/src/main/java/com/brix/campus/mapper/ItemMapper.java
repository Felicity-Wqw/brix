package com.brix.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brix.campus.model.Item;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品 Mapper：单表用 {@link BaseMapper}；联表 / 聚合查询走 XML。
 */
public interface ItemMapper extends BaseMapper<Item> {

    /**
     * 同款在售最低价（AI 工具 onSaleLowestPrice）。
     *
     * @param keyword    标题关键词
     * @param categoryId 分类 ID
     * @return 最低价，无数据返回 null
     */
    BigDecimal selectOnSaleLowestPrice(@Param("keyword") String keyword,
                                       @Param("categoryId") Long categoryId);

    /**
     * 近 90 天同款成交金额列表（AI 工具 soldMedianPrice，中位数在 Java 侧计算）。
     *
     * @param keyword 标题关键词
     * @return 成交金额列表
     */
    List<BigDecimal> selectSoldAmounts(@Param("keyword") String keyword);

    /**
     * 近 90 天同款成交样本标题（去重前 5 个，供 AI 核对成交与待估商品是否同款）。
     *
     * @param keyword 标题关键词
     * @return 标题列表
     */
    java.util.List<String> selectSoldTitles(@Param("keyword") String keyword);
}
