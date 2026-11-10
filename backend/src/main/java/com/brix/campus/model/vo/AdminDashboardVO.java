package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 管理后台数据看板（一次请求给全量，避免首屏 6 个接口并发）。
 *
 * <p>口径约定（答辩时必被问，先写死在这里）：</p>
 * <ul>
 *   <li>「成交」只算 {@code DONE} 状态订单，取消 / 关闭不计入 GMV；</li>
 *   <li>「GMV」= 已完成订单 amount 求和，不是商品挂牌价求和；</li>
 *   <li>「学校交易量」取订单上的 {@code school_id} 快照，商品被删/改归属也不影响历史统计；</li>
 *   <li>「环比」对比上一个等长周期（7 天看板比前 7 天，30 天看板比前 30 天）。</li>
 * </ul>
 */
@Data
public class AdminDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 概览四卡 */
    private Overview overview;

    /** 成交趋势（按天补零，缺失日期也返回 0，前端不用补点） */
    private List<TrendPoint> trend;

    /** 在售商品分类分布 */
    private List<Slice> categories;

    /** 各学校交易量（按成交金额降序） */
    private List<SchoolSlice> schools;

    /** 转化漏斗：发布 → 曝光 → 下单 → 成交 */
    private List<FunnelStep> funnel;

    /** 审核风险分布（P2 三级分流的落地效果） */
    private List<Slice> riskDistribution;

    /** 概览指标 */
    @Data
    public static class Overview implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 用户总数 */
        private long userCount;
        /** 近 7 天新增用户 */
        private long userNew7d;
        /** 在售商品数 */
        private long itemOnSale;
        /** 待审核商品数（人工队列） */
        private long itemPending;
        /** 已驳回商品数（近 7 天） */
        private long itemRejected7d;
        /** 已完成订单数 */
        private long orderDone;
        /** 今日已完成订单数 */
        private long orderDoneToday;
        /** GMV（已完成订单金额合计） */
        private BigDecimal gmv;
        /** 客单价 */
        private BigDecimal avgAmount;
        /** 本期 GMV（看板周期内） */
        private BigDecimal periodGmv;
        /** 上期 GMV（用于环比） */
        private BigDecimal prevGmv;
        /** GMV 环比增长率（百分比，保留 1 位；上期为 0 时返回 null 表示不可比） */
        private Double growthRate;
        /** 审核覆盖率：有风险级别标注的商品 / 全部商品（百分比） */
        private double auditCoverage;
        /** 自动放行率：LOW 占比（百分比） */
        private double autoPassRate;
    }

    /** 趋势点 */
    @Data
    public static class TrendPoint implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 日期 yyyy-MM-dd */
        private String date;
        /** 成交单数 */
        private long orderCount;
        /** 成交金额 */
        private BigDecimal amount;
    }

    /** 通用切片（分类 / 风险级别通用） */
    @Data
    public static class Slice implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private long count;
        private BigDecimal amount;
    }

    /** 学校维度切片 */
    @Data
    public static class SchoolSlice implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long schoolId;
        private String schoolName;
        private long orderCount;
        private BigDecimal amount;
        /** 该校在售商品数（供给侧的活跃度参考） */
        private long onSaleCount;
    }

    /** 漏斗步骤 */
    @Data
    public static class FunnelStep implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private long value;
        /** 相对第一步的转化率（百分比） */
        private double rate;
    }
}
