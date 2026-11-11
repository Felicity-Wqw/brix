package com.brix.campus.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 运营数据分析报告智能体（后台管理环节 AI 应用）：
 * 无工具、单轮输出，输入为后端聚合好的统计指标文本，
 * 输出仅供运营参考的分析报告，周 / 月 / 季度三周期复用。
 */
public interface DashboardReportAgent {

    /**
     * 生成运营数据分析报告。
     *
     * @param userPrompt 含统计指标的用户提示词
     * @return 报告正文
     */
    @SystemMessage(AiPrompts.DASHBOARD_REPORT_SYSTEM)
    String generateReport(@UserMessage String userPrompt);
}
