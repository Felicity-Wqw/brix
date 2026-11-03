package com.brix.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brix.campus.model.Order;
import com.brix.campus.model.vo.UserAnalysisVO;

import java.util.List;

/**
 * 订单 Mapper：单表操作全部由 {@link BaseMapper} 提供，
 * 个人分析聚合 SQL 见 OrderMapper.xml。
 */
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 统计某用户作为买家/卖家的已完成订单汇总指标。
     *
     * @param userId 用户 ID
     * @param role   BUYER / SELLER
     * @return 汇总行（无订单时 orderCount=0，其余为 0/null）
     */
    UserAnalysisVO.SummaryRow selectDoneOrderSummary(Long userId, String role);

    /**
     * 按分类统计已完成订单（占比用）。
     *
     * @param userId 用户 ID
     * @param role   BUYER / SELLER
     * @return 分类切片，按成交金额降序
     */
    List<UserAnalysisVO.CategorySlice> selectDoneOrderCategoryStats(Long userId, String role);

    /**
     * 按月份统计已完成订单（趋势用）。
     *
     * @param userId 用户 ID
     * @param role   BUYER / SELLER
     * @return 月度点，按月份升序
     */
    List<UserAnalysisVO.MonthlyPoint> selectDoneOrderMonthlyStats(Long userId, String role);
}
