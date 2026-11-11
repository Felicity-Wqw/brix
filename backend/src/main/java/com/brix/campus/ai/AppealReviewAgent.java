package com.brix.campus.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 申诉复核智能体（后台管理环节 AI 应用）：
 * 无工具、单轮结构化输出，由 {@code AiConfig} 手工装配；
 * 输出仅供审核员处理申诉时参考，终裁由人决定。
 */
public interface AppealReviewAgent {

    /**
     * 生成申诉复核建议（维持 / 改判 / 难判断 + 置信度 + 理由）。
     *
     * @param userPrompt 含原审核留痕、被判文案与申诉理由的用户提示词
     * @return 结构化复核建议
     */
    @SystemMessage(AiPrompts.APPEAL_REVIEW_SYSTEM)
    AppealReviewResult review(@UserMessage String userPrompt);
}
