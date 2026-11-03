package com.brix.campus.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 商品上架审核智能体（发布合规第二道闸）：
 * 无工具、单轮结构化输出，由 {@code AiConfig} 手工装配。
 */
public interface ItemAuditAgent {

    /**
     * 以审核专员视角复审商品发布内容。
     *
     * @param userPrompt 含分类/标题/描述的用户提示词
     * @return 结构化审核结论
     */
    @SystemMessage(AiPrompts.AUDIT_SYSTEM)
    AuditVerdict audit(@UserMessage String userPrompt);
}
