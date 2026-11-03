package com.brix.campus.ai;

/**
 * LLM 估价实现：把 {@link ItemProfile} 拼成提示词交给智能体，工具调用由模型自行决定。
 */
public class LlmEstimator implements AiEstimator {

    /** 来源标识 */
    public static final String SOURCE = "LLM";

    private final ItemEstimationAgent agent;

    public LlmEstimator(ItemEstimationAgent agent) {
        this.agent = agent;
    }

    @Override
    public EstimateResult estimate(ItemProfile profile, String pricingHints) {
        return agent.estimate(AiPrompts.estimateUserPrompt(profile, pricingHints));
    }

    @Override
    public NewPriceHint estimateNewPrice(ItemProfile profile) {
        return agent.estimateNewPrice(AiPrompts.newPriceUserPrompt(profile));
    }

    @Override
    public CopyResult copywrite(ItemProfile profile) {
        return agent.copywrite(AiPrompts.copyUserPrompt(profile));
    }

    @Override
    public String source() {
        return SOURCE;
    }
}
