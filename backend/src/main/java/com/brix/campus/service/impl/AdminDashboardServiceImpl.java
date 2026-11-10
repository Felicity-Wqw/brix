package com.brix.campus.service.impl;

import com.brix.campus.mapper.AdminStatsMapper;
import com.brix.campus.model.enums.RiskLevelEnum;
import com.brix.campus.model.vo.AdminDashboardVO;
import com.brix.campus.service.AdminDashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 后台数据看板实现。
 *
 * <p>设计取舍：</p>
 * <ul>
 *   <li>所有聚合都下推到 SQL —— 不看板不加载明细，避免把几十万行订单拉进 JVM；</li>
 *   <li>趋势按天补零 —— 图表要连续，缺失日期后端补 0，前端不做补点逻辑；</li>
 *   <li>漏斗定义在「商品」粒度而非「次数」，四步同单位才有可比性（见 {@link #buildFunnel()}）。</li>
 * </ul>
 */
@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    /** 周期下限 / 上限，防止前端传 3650 天把库拖垮 */
    private static final int MIN_DAYS = 7;
    private static final int MAX_DAYS = 90;

    /** 趋势横轴格式 */
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AdminStatsMapper statsMapper;

    public AdminDashboardServiceImpl(AdminStatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    @Override
    public AdminDashboardVO load(int days) {
        int period = Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
        LocalDate today = LocalDate.now();
        LocalDate periodStartDate = today.minusDays(period - 1L);
        LocalDateTime periodStart = periodStartDate.atStartOfDay();
        // 上期同长度区间：[periodStart - period, periodStart)
        LocalDateTime prevStart = periodStartDate.minusDays(period).atStartOfDay();
        LocalDateTime userSince = today.minusDays(6L).atStartOfDay();
        LocalDateTime rejectSince = today.minusDays(6L).atStartOfDay();

        AdminDashboardVO vo = new AdminDashboardVO();
        AdminDashboardVO.Overview overview =
                statsMapper.selectOverview(userSince, rejectSince, periodStart, prevStart);
        fillDerived(overview);
        vo.setOverview(overview);
        vo.setTrend(fillTrend(statsMapper.selectTrend(periodStart), periodStartDate, today));
        vo.setCategories(statsMapper.selectCategorySlices());
        vo.setSchools(statsMapper.selectSchoolSlices());
        vo.setRiskDistribution(statsMapper.selectRiskSlices());
        vo.setFunnel(buildFunnel());
        return vo;
    }

    /**
     * 补齐派生指标：环比增长率、审核覆盖率、自动放行率。
     *
     * @param overview 概览（原地修改）
     */
    private void fillDerived(AdminDashboardVO.Overview overview) {
        BigDecimal periodGmv = nz(overview.getPeriodGmv());
        BigDecimal prevGmv = nz(overview.getPrevGmv());
        if (prevGmv.compareTo(BigDecimal.ZERO) > 0) {
            // 环比 =（本期 - 上期）/ 上期 × 100，保留 1 位
            BigDecimal diff = periodGmv.subtract(prevGmv);
            overview.setGrowthRate(diff.multiply(BigDecimal.valueOf(100))
                    .divide(prevGmv, 1, RoundingMode.HALF_UP).doubleValue());
        } else {
            // 上期为 0 时增长率数学上无意义，返回 null 让前端显示「—」而不是 Infinity
            overview.setGrowthRate(null);
        }

        long total = statsMapper.countAllItems();
        long withRisk = statsMapper.countItemsWithRisk();
        long low = statsMapper.countItemsByRisk(RiskLevelEnum.LOW.getCode());
        overview.setAuditCoverage(percent(withRisk, total));
        overview.setAutoPassRate(percent(low, total));
    }

    /**
     * 趋势按天补零。
     *
     * @param rows  数据库返回的趋势点
     * @param start 起始日期（含）
     * @param end   结束日期（含）
     * @return 连续日期序列
     */
    private List<AdminDashboardVO.TrendPoint> fillTrend(List<AdminDashboardVO.TrendPoint> rows,
                                                        LocalDate start, LocalDate end) {
        Map<String, AdminDashboardVO.TrendPoint> index = rows.stream()
                .collect(Collectors.toMap(AdminDashboardVO.TrendPoint::getDate,
                        Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<AdminDashboardVO.TrendPoint> result = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.format(DAY_FMT);
            AdminDashboardVO.TrendPoint point = index.get(key);
            if (point == null) {
                point = new AdminDashboardVO.TrendPoint();
                point.setDate(key);
                point.setOrderCount(0);
                point.setAmount(BigDecimal.ZERO);
            }
            result.add(point);
        }
        return result;
    }

    /**
     * 构建转化漏斗。
     *
     * <p>刻意四步都按「商品」计数，保证同单位：
     * 发布商品 → 被浏览过 → 产生过订单 → 产生过成交。
     * 如果把浏览 PV（次）和成交（单）混在一张漏斗里，转化率就是假的。</p>
     *
     * @return 漏斗步骤
     */
    private List<AdminDashboardVO.FunnelStep> buildFunnel() {
        long published = statsMapper.countAllItems();
        long[] values = {
                published,
                statsMapper.countItemsViewed(),
                statsMapper.countItemsOrdered(),
                statsMapper.countItemsDone()
        };
        String[] names = {"发布商品", "产生浏览", "产生订单", "完成成交"};
        List<AdminDashboardVO.FunnelStep> steps = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            AdminDashboardVO.FunnelStep step = new AdminDashboardVO.FunnelStep();
            step.setName(names[i]);
            step.setValue(values[i]);
            step.setRate(percent(values[i], published));
            steps.add(step);
        }
        return steps;
    }

    /**
     * 百分比（保留 1 位，分母为 0 返回 0）。
     *
     * @param part  分子
     * @param total 分母
     * @return 百分比数值
     */
    private static double percent(long part, long total) {
        if (total <= 0) {
            return 0D;
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * null 转 0。
     *
     * @param value 原值
     * @return 非 null 金额
     */
    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
