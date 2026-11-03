package com.brix.campus.model.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 个人买卖分析 VO（G05 个人数据可视化）。
 *
 * <p>分「买入」与「卖出」两个维度，各自包含汇总指标、分类占比与月度趋势，
 * 全部基于 DONE（已完成）订单统计。</p>
 */
public class UserAnalysisVO {

    /** 买入维度统计（当前用户作为买家） */
    private Side buy;

    /** 卖出维度统计（当前用户作为卖家） */
    private Side sell;

    public Side getBuy() {
        return buy;
    }

    public void setBuy(Side buy) {
        this.buy = buy;
    }

    public Side getSell() {
        return sell;
    }

    public void setSell(Side sell) {
        this.sell = sell;
    }

    /**
     * 单一维度（买入或卖出）的完整统计。
     */
    public static class Side {

        /** 汇总指标 */
        private SummaryRow summary;

        /** 分类占比（按成交金额降序） */
        private List<CategorySlice> categories;

        /** 月度趋势（按月份升序） */
        private List<MonthlyPoint> monthly;

        public SummaryRow getSummary() {
            return summary;
        }

        public void setSummary(SummaryRow summary) {
            this.summary = summary;
        }

        public List<CategorySlice> getCategories() {
            return categories;
        }

        public void setCategories(List<CategorySlice> categories) {
            this.categories = categories;
        }

        public List<MonthlyPoint> getMonthly() {
            return monthly;
        }

        public void setMonthly(List<MonthlyPoint> monthly) {
            this.monthly = monthly;
        }
    }

    /** 汇总指标行（Mapper 聚合结果） */
    public static class SummaryRow {

        /** 已完成订单笔数 */
        private Long orderCount;

        /** 成交总金额 */
        private BigDecimal totalAmount;

        /** 平均单笔金额 */
        private BigDecimal avgAmount;

        /** 最大单笔金额 */
        private BigDecimal maxAmount;

        /** 涉及分类数 */
        private Long categoryCount;

        /** 平均成交天数（挂牌→成交，仅卖出维度有值） */
        private BigDecimal avgDaysToSell;

        public Long getOrderCount() {
            return orderCount;
        }

        public void setOrderCount(Long orderCount) {
            this.orderCount = orderCount;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public BigDecimal getAvgAmount() {
            return avgAmount;
        }

        public void setAvgAmount(BigDecimal avgAmount) {
            this.avgAmount = avgAmount;
        }

        public BigDecimal getMaxAmount() {
            return maxAmount;
        }

        public void setMaxAmount(BigDecimal maxAmount) {
            this.maxAmount = maxAmount;
        }

        public Long getCategoryCount() {
            return categoryCount;
        }

        public void setCategoryCount(Long categoryCount) {
            this.categoryCount = categoryCount;
        }

        public BigDecimal getAvgDaysToSell() {
            return avgDaysToSell;
        }

        public void setAvgDaysToSell(BigDecimal avgDaysToSell) {
            this.avgDaysToSell = avgDaysToSell;
        }
    }

    /** 分类占比切片 */
    public static class CategorySlice {

        /** 分类 ID */
        private Long categoryId;

        /** 分类名称 */
        private String categoryName;

        /** 订单笔数 */
        private Long orderCount;

        /** 成交总金额 */
        private BigDecimal totalAmount;

        public Long getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(Long categoryId) {
            this.categoryId = categoryId;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
        }

        public Long getOrderCount() {
            return orderCount;
        }

        public void setOrderCount(Long orderCount) {
            this.orderCount = orderCount;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }
    }

    /** 月度趋势点 */
    public static class MonthlyPoint {

        /** 月份，如 2026-10 */
        private String month;

        /** 订单笔数 */
        private Long orderCount;

        /** 成交总金额 */
        private BigDecimal totalAmount;

        public String getMonth() {
            return month;
        }

        public void setMonth(String month) {
            this.month = month;
        }

        public Long getOrderCount() {
            return orderCount;
        }

        public void setOrderCount(Long orderCount) {
            this.orderCount = orderCount;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }
    }
}
