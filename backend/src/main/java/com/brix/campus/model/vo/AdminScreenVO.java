package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据可视化大屏聚合出参（{@code GET /admin/dashboard/screen}）。
 *
 * <p>统计口径与看板 {@link AdminDashboardVO} 完全一致（复用同一实现），
 * 额外补充 AI 调用统计与实时动态流，供 30 秒轮询渲染。</p>
 */
@Data
public class AdminScreenVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 核心指标（在售/本月成交/GMV/用户/过审率等） */
    private AdminDashboardVO.Overview overview;

    /** 近 30 天成交单数 + GMV 趋势（按天补零） */
    private List<AdminDashboardVO.TrendPoint> trend;

    /** 学校成交分布（含在售商品数，地图气泡数据源） */
    private List<AdminDashboardVO.SchoolSlice> schools;

    /** 主校区坐标点（地图散点：学校级聚合数据 + 校区位置） */
    private List<CampusPoint> points;

    /** 品类成交占比 */
    private List<AdminDashboardVO.Slice> categories;

    /** 发布 → 浏览 → 下单 → 成交 漏斗 */
    private List<AdminDashboardVO.FunnelStep> funnel;

    /** 风险级别分布 */
    private List<AdminDashboardVO.Slice> riskDistribution;

    /** AI 调用统计（估价/文案/图片审核） */
    private AiStats aiStats;

    /** 实时动态（最新成交/上架/拦截） */
    private List<RealtimeEvent> realtime;

    /** 待办角标（待审商品/待处理举报/待终审案件） */
    private Map<String, Long> pending;

    /**
     * AI 调用统计。
     */
    @Data
    public static class AiStats implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 今日 AI 调用总次数（估价 + 文案） */
        private long aiCallsToday;

        /** 智能估价累计次数 */
        private long estimateCount;

        /** 一键文案累计次数 */
        private long copyCount;

        /** LLM 直答次数 */
        private long llmCount;

        /** 规则降级次数 */
        private long ruleCount;

        /** 图片审核累计张数（出结论的） */
        private long imageAudited;

        /** 图片违规拦截张数 */
        private long imageReject;

        /** 图片存疑转人工张数 */
        private long imageUncertain;

        /** 图片审核平均耗时（毫秒；无数据为 null） */
        private Double imageAvgCostMs;
    }

    /**
     * 主校区坐标点（地图散点数据源）。
     */
    @Data
    public static class CampusPoint implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 学校名 */
        private String schoolName;

        /** 校区名 */
        private String campusName;

        /** 纬度（GCJ-02） */
        private BigDecimal lat;

        /** 经度（GCJ-02） */
        private BigDecimal lng;

        /** 该校在售商品数 */
        private long onSaleCount;

        /** 该校成交单数 */
        private long orderCount;

        /** 该校成交金额 */
        private BigDecimal amount;
    }

    /**
     * 实时动态条目。
     */
    @Data
    public static class RealtimeEvent implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 类型：SOLD 成交 / PUBLISHED 上架 / INTERCEPT 拦截 */
        private String type;

        /** 展示文案（时间在 time 字段，这里只放正文） */
        private String content;

        /** 事件时间 */
        private LocalDateTime time;
    }
}
