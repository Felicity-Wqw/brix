package com.brix.campus.service.impl;

import com.brix.campus.ai.AiPrompts;
import com.brix.campus.ai.DashboardReportAgent;
import com.brix.campus.common.BusinessException;
import com.brix.campus.mapper.AdminStatsMapper;
import com.brix.campus.mapper.AiEstimateLogMapper;
import com.brix.campus.mapper.CampusMapper;
import com.brix.campus.mapper.ImageAuditCacheMapper;
import com.brix.campus.mapper.ItemAuditLogMapper;
import com.brix.campus.mapper.ItemMapper;
import com.brix.campus.mapper.JuryCaseMapper;
import com.brix.campus.mapper.OrderMapper;
import com.brix.campus.mapper.SchoolMapper;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.AiEstimateLog;
import com.brix.campus.model.Campus;
import com.brix.campus.model.ImageAuditCache;
import com.brix.campus.model.Item;
import com.brix.campus.model.ItemAuditLog;
import com.brix.campus.model.JuryCase;
import com.brix.campus.model.Order;
import com.brix.campus.model.School;
import com.brix.campus.model.User;
import com.brix.campus.model.enums.AiSceneEnum;
import com.brix.campus.model.enums.ItemStatusEnum;
import com.brix.campus.model.enums.JuryStageEnum;
import com.brix.campus.model.enums.OrderStatusEnum;
import com.brix.campus.model.enums.RiskLevelEnum;
import com.brix.campus.model.vo.AdminDashboardVO;
import com.brix.campus.model.vo.AdminScreenVO;
import com.brix.campus.service.AdminDashboardService;
import com.brix.campus.service.ItemService;
import com.brix.campus.service.ReportService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardServiceImpl.class);

    private final AdminStatsMapper statsMapper;
    private final ObjectProvider<DashboardReportAgent> reportAgentProvider;
    private final OrderMapper orderMapper;
    private final ItemMapper itemMapper;
    private final UserMapper userMapper;
    private final ItemAuditLogMapper itemAuditLogMapper;
    private final AiEstimateLogMapper aiEstimateLogMapper;
    private final ImageAuditCacheMapper imageAuditCacheMapper;
    private final CampusMapper campusMapper;
    private final SchoolMapper schoolMapper;
    private final ItemService itemService;
    private final ReportService reportService;
    private final JuryCaseMapper juryCaseMapper;

    public AdminDashboardServiceImpl(AdminStatsMapper statsMapper,
                                     ObjectProvider<DashboardReportAgent> reportAgentProvider,
                                     OrderMapper orderMapper,
                                     ItemMapper itemMapper,
                                     UserMapper userMapper,
                                     ItemAuditLogMapper itemAuditLogMapper,
                                     AiEstimateLogMapper aiEstimateLogMapper,
                                     ImageAuditCacheMapper imageAuditCacheMapper,
                                     CampusMapper campusMapper,
                                     SchoolMapper schoolMapper,
                                     ItemService itemService,
                                     ReportService reportService,
                                     JuryCaseMapper juryCaseMapper) {
        this.statsMapper = statsMapper;
        this.reportAgentProvider = reportAgentProvider;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
        this.itemAuditLogMapper = itemAuditLogMapper;
        this.aiEstimateLogMapper = aiEstimateLogMapper;
        this.imageAuditCacheMapper = imageAuditCacheMapper;
        this.campusMapper = campusMapper;
        this.schoolMapper = schoolMapper;
        this.itemService = itemService;
        this.reportService = reportService;
        this.juryCaseMapper = juryCaseMapper;
    }

    @Override
    public AdminScreenVO screen() {
        AdminDashboardVO base = load(30);
        AdminScreenVO vo = new AdminScreenVO();
        vo.setOverview(base.getOverview());
        vo.setTrend(base.getTrend());
        vo.setSchools(base.getSchools());
        vo.setPoints(buildCampusPoints(base.getSchools()));
        vo.setCategories(base.getCategories());
        vo.setFunnel(base.getFunnel());
        vo.setRiskDistribution(base.getRiskDistribution());
        vo.setAiStats(buildAiStats());
        vo.setRealtime(buildRealtime());

        Map<String, Long> pending = new LinkedHashMap<>();
        pending.put("pendingItems", itemService.countPending());
        pending.put("pendingReports", reportService.countPending());
        Long pendingJury = juryCaseMapper.selectCount(Wrappers.<JuryCase>lambdaQuery()
                .eq(JuryCase::getStatus, JuryStageEnum.PENDING_RULING.getCode()));
        pending.put("pendingJury", pendingJury == null ? 0L : pendingJury);
        vo.setPending(pending);
        return vo;
    }

    /**
     * 主校区坐标点：全部 13 所学校的主校区（is_main=1）都出气泡——
     * 有成交的按成交额映射大小，无成交的用在售数映射（小气泡），地图更饱满；
     * 学校级聚合数据，不涉及个人位置。
     */
    private List<AdminScreenVO.CampusPoint> buildCampusPoints(List<AdminDashboardVO.SchoolSlice> schools) {
        List<Campus> mainCampuses = campusMapper.selectList(Wrappers.<Campus>lambdaQuery()
                .eq(Campus::getIsMain, 1)
                .eq(Campus::getEnabled, 1)
                .isNotNull(Campus::getLat)
                .isNotNull(Campus::getLng));
        if (mainCampuses.isEmpty()) {
            return List.of();
        }
        // 在售商品按学校计数（轻量：只拉 id + school_id 两列）
        Map<Long, Long> onSaleBySchool = itemMapper.selectList(Wrappers.<Item>lambdaQuery()
                        .eq(Item::getStatus, ItemStatusEnum.ON_SALE.getCode())
                        .select(Item::getSchoolId)).stream()
                .filter(item -> item.getSchoolId() != null)
                .collect(Collectors.groupingBy(Item::getSchoolId, Collectors.counting()));
        Map<Long, AdminDashboardVO.SchoolSlice> bySchool = schools.stream()
                .filter(s -> s.getSchoolId() != null)
                .collect(Collectors.toMap(AdminDashboardVO.SchoolSlice::getSchoolId, Function.identity(),
                        (a, b) -> a));

        List<AdminScreenVO.CampusPoint> points = new ArrayList<>();
        for (Campus campus : mainCampuses) {
            AdminDashboardVO.SchoolSlice slice = bySchool.get(campus.getSchoolId());
            AdminScreenVO.CampusPoint point = new AdminScreenVO.CampusPoint();
            point.setCampusName(campus.getName());
            point.setLat(campus.getLat());
            point.setLng(campus.getLng());
            point.setOnSaleCount(onSaleBySchool.getOrDefault(campus.getSchoolId(), 0L));
            if (slice != null) {
                point.setSchoolName(slice.getSchoolName());
                point.setOrderCount(slice.getOrderCount());
                point.setAmount(slice.getAmount());
            } else {
                point.setSchoolName(schoolNameOf(campus.getSchoolId()));
                point.setOrderCount(0);
                point.setAmount(BigDecimal.ZERO);
            }
            points.add(point);
        }
        return points;
    }

    /** 学校名兜底查询（无成交学校不在看板切片里，需要反查字典） */
    private String schoolNameOf(Long schoolId) {
        School school = schoolMapper.selectById(schoolId);
        return school == null ? "未知学校" : school.getName();
    }

    /**
     * AI 调用统计：估价/文案按场景与来源计数，图片审核按结论与耗时汇总。
     */
    private AdminScreenVO.AiStats buildAiStats() {
        AdminScreenVO.AiStats stats = new AdminScreenVO.AiStats();
        stats.setAiCallsToday(aiEstimateLogMapper.selectCount(Wrappers.<AiEstimateLog>lambdaQuery()
                .eq(AiEstimateLog::getBizDate, LocalDate.now())));
        stats.setEstimateCount(aiEstimateLogMapper.selectCount(Wrappers.<AiEstimateLog>lambdaQuery()
                .eq(AiEstimateLog::getScene, AiSceneEnum.ESTIMATE.getCode())));
        stats.setCopyCount(aiEstimateLogMapper.selectCount(Wrappers.<AiEstimateLog>lambdaQuery()
                .eq(AiEstimateLog::getScene, AiSceneEnum.COPY.getCode())));
        stats.setLlmCount(aiEstimateLogMapper.selectCount(Wrappers.<AiEstimateLog>lambdaQuery()
                .eq(AiEstimateLog::getSource, "LLM")));
        stats.setRuleCount(aiEstimateLogMapper.selectCount(Wrappers.<AiEstimateLog>lambdaQuery()
                .eq(AiEstimateLog::getSource, "RULE")));

        List<ImageAuditCache> audited = imageAuditCacheMapper.selectList(Wrappers.<ImageAuditCache>lambdaQuery()
                .ne(ImageAuditCache::getStatus, ImageAuditCache.STATUS_PENDING));
        stats.setImageAudited(audited.size());
        stats.setImageReject(audited.stream()
                .filter(row -> ImageAuditCache.STATUS_REJECT.equals(row.getStatus())).count());
        stats.setImageUncertain(audited.stream()
                .filter(row -> ImageAuditCache.STATUS_UNCERTAIN.equals(row.getStatus())).count());
        List<Integer> costs = audited.stream()
                .map(ImageAuditCache::getCostMs).filter(Objects::nonNull).toList();
        stats.setImageAvgCostMs(costs.isEmpty() ? null
                : costs.stream().mapToInt(Integer::intValue).average().orElse(0.0d));
        return stats;
    }

    /**
     * 实时动态：最新成交 / 最新上架 / 最新拦截，按时间倒序合并取前 6 条。
     */
    private List<AdminScreenVO.RealtimeEvent> buildRealtime() {
        List<AdminScreenVO.RealtimeEvent> events = new ArrayList<>();

        List<Order> dones = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatusEnum.DONE.getCode())
                .orderByDesc(Order::getFinishTime).last("LIMIT 5"));
        if (!dones.isEmpty()) {
            Map<Long, String> titles = itemMapper.selectByIds(
                    dones.stream().map(Order::getItemId).collect(Collectors.toSet())).stream()
                    .collect(Collectors.toMap(Item::getId, Item::getTitle));
            Map<Long, String> nicknames = userMapper.selectByIds(
                    dones.stream().map(Order::getBuyerId).collect(Collectors.toSet())).stream()
                    .collect(Collectors.toMap(User::getId, User::getNickname));
            for (Order order : dones) {
                events.add(event("SOLD",
                        nicknames.getOrDefault(order.getBuyerId(), "同学") + " 售出 "
                                + titles.getOrDefault(order.getItemId(), "商品"),
                        order.getFinishTime()));
            }
        }

        List<Item> latest = itemMapper.selectList(Wrappers.<Item>lambdaQuery()
                .orderByDesc(Item::getCreatedAt).last("LIMIT 5"));
        if (!latest.isEmpty()) {
            Map<Long, String> nicknames = userMapper.selectByIds(
                    latest.stream().map(Item::getSellerId).collect(Collectors.toSet())).stream()
                    .collect(Collectors.toMap(User::getId, User::getNickname));
            for (Item item : latest) {
                events.add(event("PUBLISHED",
                        nicknames.getOrDefault(item.getSellerId(), "同学") + " 上架 " + item.getTitle(),
                        item.getCreatedAt()));
            }
        }

        List<ItemAuditLog> intercepts = itemAuditLogMapper.selectList(Wrappers.<ItemAuditLog>lambdaQuery()
                .eq(ItemAuditLog::getRiskLevel, RiskLevelEnum.HIGH.getCode())
                .orderByDesc(ItemAuditLog::getCreatedAt).last("LIMIT 5"));
        for (ItemAuditLog row : intercepts) {
            events.add(event("INTERCEPT", "拦截「" + row.getItemTitle() + "」", row.getCreatedAt()));
        }

        events.sort(Comparator.comparing(AdminScreenVO.RealtimeEvent::getTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return events.size() > 6 ? new ArrayList<>(events.subList(0, 6)) : events;
    }

    private AdminScreenVO.RealtimeEvent event(String type, String content, LocalDateTime time) {
        AdminScreenVO.RealtimeEvent event = new AdminScreenVO.RealtimeEvent();
        event.setType(type);
        event.setContent(content);
        event.setTime(time);
        return event;
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

    // ================================ AI 运营分析报告 ================================

    @Override
    public String aiReport(int days) {
        DashboardReportAgent agent = reportAgentProvider.getIfAvailable();
        if (agent == null) {
            throw BusinessException.param("AI 未配置，请直接查看上方统计图表");
        }
        int period = Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
        AdminDashboardVO vo = load(period);
        String periodName = switch (period) {
            case 7 -> "本周";
            case 30 -> "最近 30 天";
            default -> "最近 " + period + " 天（季度汇报）";
        };
        AdminDashboardVO.Overview o = vo.getOverview();
        StringBuilder stats = new StringBuilder();
        stats.append("总用户：").append(o.getUserCount()).append("（近 7 天新增 ").append(o.getUserNew7d()).append("）\n");
        stats.append("在售商品：").append(o.getItemOnSale()).append("，待审核 ").append(o.getItemPending())
                .append("，近 7 天被拒 ").append(o.getItemRejected7d()).append("\n");
        stats.append("累计成交：").append(o.getOrderDone()).append(" 单，GMV ").append(o.getGmv())
                .append(" 元，客单价 ").append(o.getAvgAmount()).append(" 元\n");
        stats.append("本期 GMV：").append(o.getPeriodGmv()).append(" 元，上期 ").append(o.getPrevGmv())
                .append(" 元，环比 ").append(o.getGrowthRate()).append("%\n");
        stats.append("审核覆盖率：").append(o.getAuditCoverage()).append("%，自动放行率：").append(o.getAutoPassRate()).append("%\n");
        stats.append("分类成交 TOP3：").append(topSlices(vo.getCategories(), 3)).append("\n");
        stats.append("学校成交 TOP3：").append(topSchoolSlices(vo.getSchools(), 3)).append("\n");
        stats.append("风险分布：").append(riskText(vo.getRiskDistribution())).append("\n");
        try {
            String report = agent.generateReport(AiPrompts.dashboardReportUserPrompt(periodName, stats.toString()));
            if (report == null || report.isBlank()) {
                throw BusinessException.param("AI 未返回报告内容，请稍后重试");
            }
            return report.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("运营分析报告生成失败：days={}，{}", period, e.getMessage());
            throw BusinessException.param("AI 报告生成失败，请稍后重试");
        }
    }

    private String topSlices(List<AdminDashboardVO.Slice> slices, int top) {
        if (slices == null || slices.isEmpty()) {
            return "（无数据）";
        }
        return slices.stream().limit(top)
                .map(s -> s.getName() + " " + s.getCount() + " 单/" + s.getAmount() + " 元")
                .collect(java.util.stream.Collectors.joining("；"));
    }

    private String topSchoolSlices(List<AdminDashboardVO.SchoolSlice> slices, int top) {
        if (slices == null || slices.isEmpty()) {
            return "（无数据）";
        }
        return slices.stream().limit(top)
                .map(s -> s.getSchoolName() + " " + s.getOrderCount() + " 单/" + s.getAmount() + " 元")
                .collect(java.util.stream.Collectors.joining("；"));
    }

    private String riskText(List<AdminDashboardVO.Slice> slices) {
        if (slices == null || slices.isEmpty()) {
            return "（无数据）";
        }
        return slices.stream()
                .map(s -> s.getName() + " " + s.getCount())
                .collect(java.util.stream.Collectors.joining("，"));
    }
}
