package com.brix.campus.ai;

/**
 * 估价能力统一入口：Service 只依赖本接口，不关心背后是 LLM 还是本地规则。
 */
public interface AiEstimator {

    /**
     * 智能估价。
     *
     * @param profile      归一化入参
     * @param pricingHints 定价参考数据包文本（用户原价 / LLM 新品价估计 / 折价参考），可空
     * @return 估价结果
     */
    EstimateResult estimate(ItemProfile profile, String pricingHints);

    /**
     * 新品原价查询（估价第一阶段锚点，仅 LLM 实现有意义；无原价时由服务层调用）。
     *
     * @param profile 归一化入参
     * @return 新品价区间 + 依据；无法给出返回 null
     */
    NewPriceHint estimateNewPrice(ItemProfile profile);

    /**
     * 一键生成标题与描述。
     *
     * @param profile 归一化入参
     * @return 文案结果
     */
    CopyResult copywrite(ItemProfile profile);

    /**
     * 数据来源标识。
     *
     * @return LLM 或 RULE
     */
    String source();
}
