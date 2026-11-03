package com.brix.campus.ai;

/**
 * LLM 知识库给出的新品参考价（估价第一阶段产物，作为无用户原价时的定价锚点）。
 *
 * @param newPriceLow  新品参考价下限（元）
 * @param newPriceHigh 新品参考价上限（元）
 * @param basis        依据说明（如「京东当前售价」「同系列定位推算，价格不确定」）
 */
public record NewPriceHint(java.math.BigDecimal newPriceLow,
                           java.math.BigDecimal newPriceHigh,
                           String basis) {
}
