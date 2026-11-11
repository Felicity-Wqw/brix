package com.brix.campus.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 小法庭预审智能体（纠纷环节 AI 应用）：
 * 无工具、单轮结构化输出，由 {@code AiConfig} 手工装配；
 * 输出仅供陪审员参考，不参与计票与裁决。
 */
public interface JuryAdvisorAgent {

    /**
     * 生成案件预审参考（争议焦点 + 双方证据摘要 + 倾向）。
     *
     * @param userPrompt 含案件信息与双方证据的用户提示词
     * @return 结构化预审结果
     */
    @SystemMessage(AiPrompts.JURY_PREVIEW_SYSTEM)
    JuryPreviewResult preview(@UserMessage String userPrompt);

    /**
     * 按裁决方向起草裁决理由（供审核员编辑确认，不直接发布）。
     *
     * @param userPrompt 含案件信息、投票统计与裁决方向的用户提示词
     * @return 裁决理由正文
     */
    @SystemMessage(AiPrompts.JURY_RULING_SYSTEM)
    String draftRuling(@UserMessage String userPrompt);
}
