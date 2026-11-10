package com.brix.campus.mapper;

import com.brix.campus.model.vo.AdminDashboardVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台统计 Mapper（聚合查询全部走 XML，见 AdminStatsMapper.xml）。
 *
 * <p>为什么单独一个 Mapper 而不是塞进 Item/Order Mapper：
 * 看板的口径是「平台级」的，跨 t_user / t_item / t_order / t_school 四张表，
 * 挂在任何一个领域 Mapper 上都会让那个 Mapper 变成上帝对象。</p>
 */
public interface AdminStatsMapper {

    /**
     * 概览指标（单条 SQL 用标量子查询一次取全，避免 8 次往返）。
     *
     * @param userSince   近 7 天起点
     * @param rejectSince 近 7 天起点（驳回统计用）
     * @param periodStart 本期起点
     * @param prevStart   上期起点
     * @return 概览（auditCoverage / autoPassRate 由服务层补齐）
     */
    AdminDashboardVO.Overview selectOverview(@Param("userSince") LocalDateTime userSince,
                                             @Param("rejectSince") LocalDateTime rejectSince,
                                             @Param("periodStart") LocalDateTime periodStart,
                                             @Param("prevStart") LocalDateTime prevStart);

    /**
     * 成交趋势（只返回有成交的日期，缺日由服务层补零）。
     *
     * @param start 起始时间
     * @return 趋势点（日期升序）
     */
    List<AdminDashboardVO.TrendPoint> selectTrend(@Param("start") LocalDateTime start);

    /**
     * 在售商品的分类分布。
     *
     * @return 分类切片（数量降序）
     */
    List<AdminDashboardVO.Slice> selectCategorySlices();

    /**
     * 各学校交易量（订单上的学校快照，含该校在售商品数）。
     *
     * @return 学校切片（成交金额降序）
     */
    List<AdminDashboardVO.SchoolSlice> selectSchoolSlices();

    /**
     * 风险级别分布（三级分流的落地效果）。
     *
     * @return 风险切片
     */
    List<AdminDashboardVO.Slice> selectRiskSlices();

    /** 商品总数 */
    long countAllItems();

    /** 有风险级别标注的商品数（P2 改造后新发布的商品） */
    long countItemsWithRisk();

    /** 被浏览过的商品数（view_count > 0） */
    long countItemsViewed();

    /** 产生过订单的商品数（去重） */
    long countItemsOrdered();

    /** 产生过成交订单的商品数（去重） */
    long countItemsDone();

    /**
     * 按风险级别统计商品数。
     *
     * @param level 风险级别
     * @return 商品数
     */
    long countItemsByRisk(@Param("level") String level);
}
