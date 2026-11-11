package com.brix.campus.config;

import com.brix.campus.ai.AiEstimator;
import com.brix.campus.ai.AiRepository;
import com.brix.campus.ai.AiTools;
import com.brix.campus.ai.AppealReviewAgent;
import com.brix.campus.ai.DashboardReportAgent;
import com.brix.campus.ai.ItemAuditAgent;
import com.brix.campus.ai.ItemEstimationAgent;
import com.brix.campus.ai.JuryAdvisorAgent;
import com.brix.campus.ai.LlmEstimator;
import com.brix.campus.ai.RuleBasedEstimator;
import com.brix.campus.service.CategoryService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * AI 条件装配（架构 6.3）：<b>有 Key 走 LLM，无 Key 走本地规则</b>。
 *
 * <p>不用 {@code langchain4j-spring-boot-starter}：稳定核心包 + 手工装配，功能等价、无 beta 依赖、
 * 无 Key 时启动零报错（PRD J03）。</p>
 *
 * <p>关于 {@code @ConditionalOnMissingBean}：本项目的规则引擎 {@link RuleBasedEstimator} 本身实现了
 * {@link AiEstimator}，且<b>必须同时作为 LLM 的降级目标存在</b>，因此两个 Bean 需要共存。
 * 这里改用「{@code @ConditionalOnExpression}（Key 非空才装配）控制 LLM + {@code @Primary} 指定优先级」达成完全相同的效果：
 * <ul>
 *   <li>无 Key：容器里只有 {@code ruleBasedEstimator} 一个 AiEstimator → 注入成功；</li>
 *   <li>有 Key：{@code llmEstimator} 为 {@code @Primary} → 注入到 LlmEstimator，规则引擎留作降级。</li>
 * </ul>
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * 本地规则引擎：始终存在，既可在无 Key 时作为唯一实现，也可作为 LLM 超时/异常的降级目标。
     */
    @Bean
    public RuleBasedEstimator ruleBasedEstimator(CategoryService categoryService, AiRepository aiRepository) {
        return new RuleBasedEstimator(categoryService, aiRepository);
    }

    /**
     * ChatModel（文本）：仅在配置了 {@code brix.ai.api-key} 时创建（空字符串等于未配置）。
     * 标记 {@code @Primary}：容器中同时存在视觉模型 Bean 时，文本智能体按类型注入仍取文本模型。
     */
    @Bean
    @Primary
    @ConditionalOnExpression("!'${brix.ai.api-key:} '.isBlank()")
    public ChatModel chatModel(@Value("${brix.ai.base-url}") String baseUrl,
                               @Value("${brix.ai.api-key}") String apiKey,
                               @Value("${brix.ai.model}") String modelName,
                               @Value("${brix.ai.timeout-seconds:10}") long timeoutSeconds) {
        String effectiveBaseUrl = (baseUrl == null || baseUrl.isBlank())
                ? "https://api.deepseek.com/v1" : baseUrl;
        log.info("已启用 LLM：baseUrl={}, model={}", effectiveBaseUrl, modelName);
        return OpenAiChatModel.builder()
                .baseUrl(effectiveBaseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.3d)
                // J01 十秒超时；maxRetries=0 表示立即降级，不叠加延迟
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(0)
                .build();
    }

    /**
     * 视觉审核模型（图片审核第二道闸）：OpenAI 兼容协议接入智谱 glm-4v-flash（免费）。
     * 独立 Key/厂商，未配置时图片闸记 UNAVAILABLE，发布仅走文本闸。
     */
    @Bean
    @ConditionalOnExpression("!'${brix.ai.vision.api-key:} '.isBlank()")
    public ChatModel visionChatModel(@Value("${brix.ai.vision.base-url}") String baseUrl,
                                     @Value("${brix.ai.vision.api-key}") String apiKey,
                                     @Value("${brix.ai.vision.model}") String modelName,
                                     @Value("${brix.ai.vision.timeout-seconds:8}") long timeoutSeconds) {
        log.info("已启用视觉审核模型：baseUrl={}, model={}", baseUrl, modelName);
        return OpenAiChatModel.builder()
                .baseUrl((baseUrl == null || baseUrl.isBlank())
                        ? "https://open.bigmodel.cn/api/paas/v4" : baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.1d)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(0)
                .build();
    }

    /**
     * AiServices 智能体：注册 3 个 {@code @Tool}，由模型按需多轮查库。
     */
    @Bean
    @ConditionalOnExpression("!'${brix.ai.api-key:} '.isBlank()")
    public ItemEstimationAgent itemEstimationAgent(ChatModel chatModel, AiTools aiTools) {
        return AiServices.builder(ItemEstimationAgent.class)
                .chatModel(chatModel)
                .tools(aiTools)
                .build();
    }

    /**
     * LLM 估价实现：有 Key 时作为 {@code @Primary} 的首选实现。
     */
    @Bean
    @Primary
    @ConditionalOnExpression("!'${brix.ai.api-key:} '.isBlank()")
    public AiEstimator llmEstimator(ItemEstimationAgent itemEstimationAgent) {
        return new LlmEstimator(itemEstimationAgent);
    }

    /**
     * 上架审核智能体：无工具、单轮结构化输出，专供发布合规第二道闸。
     * 无 Key 时不创建，{@code PublishAuditServiceImpl} 自动降级为仅违禁词闸。
     */
    @Bean
    @ConditionalOnExpression("!'${brix.ai.api-key:} '.isBlank()")
    public ItemAuditAgent itemAuditAgent(ChatModel chatModel) {
        return AiServices.builder(ItemAuditAgent.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * 小法庭预审智能体：无工具、单轮结构化输出，专供纠纷环节举证期预审参考。
     * 无 Key 时不创建，预审相关流程自动静默跳过。
     */
    @Bean
    @ConditionalOnExpression("!'${brix.ai.api-key:} '.isBlank()")
    public JuryAdvisorAgent juryAdvisorAgent(ChatModel chatModel) {
        return AiServices.builder(JuryAdvisorAgent.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * 申诉复核智能体：无工具、单轮结构化输出，专供后台申诉处理参考。
     * 无 Key 时不创建，申诉复核接口业务报错提示手写处理。
     */
    @Bean
    @ConditionalOnExpression("!'${brix.ai.api-key:} '.isBlank()")
    public AppealReviewAgent appealReviewAgent(ChatModel chatModel) {
        return AiServices.builder(AppealReviewAgent.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * 运营数据分析报告智能体：单轮输出，周 / 月 / 季度三周期复用。
     * 无 Key 时不创建，报告接口业务报错提示查看统计图表。
     */
    @Bean
    @ConditionalOnExpression("!'${brix.ai.api-key:} '.isBlank()")
    public DashboardReportAgent dashboardReportAgent(ChatModel chatModel) {
        return AiServices.builder(DashboardReportAgent.class)
                .chatModel(chatModel)
                .build();
    }
}
