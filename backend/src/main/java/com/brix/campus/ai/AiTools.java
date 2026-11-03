package com.brix.campus.ai;

import com.brix.campus.model.enums.ConditionEnum;
import org.springframework.stereotype.Component;

/**
 * LangChain4j 工具集：模型按需多轮调用查库，使 reasons 能引用真实数字（架构 6.2）。
 */
@Component
public class AiTools {

    private final AiRepository aiRepository;

    public AiTools(AiRepository aiRepository) {
        this.aiRepository = aiRepository;
    }

    /**
     * 查询同款商品近 90 天成交价中位数（元）。
     *
     * @param keyword    品牌型号关键词，如「iPad 9」
     * @param categoryId 分类 ID
     * @return 中位数金额，无数据返回 0
     */
    @dev.langchain4j.agent.tool.Tool("查询同款商品近 90 天成交价中位数（元）。keyword 传品牌型号关键词（如 iPad 9），"
            + "categoryId 传分类 ID。无数据返回 0。")
    public java.math.BigDecimal soldMedianPrice(
            @dev.langchain4j.agent.tool.P("keyword") String keyword,
            @dev.langchain4j.agent.tool.P("categoryId") Long categoryId) {
        return aiRepository.soldMedianPrice(keyword, categoryId);
    }

    /**
     * 查询同款商品近 90 天成交统计（推荐优先使用）。
     *
     * @param keyword    品牌型号关键词
     * @param categoryId 分类 ID
     * @return 一段可读统计文本
     */
    @dev.langchain4j.agent.tool.Tool("查询近 90 天成交统计（优先调用）。keyword 必须传能唯一确定具体款式的词"
            + "（如「iPhone 13」「小黑瓶精华」），优先取待估商品标题的核心词，禁止传品牌名或宽泛品类词。"
            + "返回统计 + 成交样本标题，请核对样本与待估商品是否为同一具体产品，不同产品必须忽略该数据。")
    public String soldPriceStats(
            @dev.langchain4j.agent.tool.P("keyword") String keyword,
            @dev.langchain4j.agent.tool.P("categoryId") Long categoryId) {
        return aiRepository.soldPriceStats(keyword);
    }

    /**
     * 查询同款商品当前在售最低价（元）。
     *
     * @param keyword    品牌型号关键词
     * @param categoryId 分类 ID
     * @return 最低价，无数据返回 0
     */
    @dev.langchain4j.agent.tool.Tool("查询同款商品当前在售最低价（元）。无数据返回 0。")
    public java.math.BigDecimal onSaleLowestPrice(
            @dev.langchain4j.agent.tool.P("keyword") String keyword,
            @dev.langchain4j.agent.tool.P("categoryId") Long categoryId) {
        return aiRepository.onSaleLowestPrice(keyword, categoryId);
    }

    /**
     * 按成色返回折价系数。
     *
     * @param conditionLevel 成色码
     * @return 折价系数
     */
    @dev.langchain4j.agent.tool.Tool("按成色返回折价系数：BRAND_NEW=1.0、LIKE_NEW=0.85、LIGHT_USE=0.70、HEAVY_USE=0.55。")
    public double conditionDiscountFactor(
            @dev.langchain4j.agent.tool.P("conditionLevel") String conditionLevel) {
        return ConditionEnum.factorOf(conditionLevel);
    }
}
