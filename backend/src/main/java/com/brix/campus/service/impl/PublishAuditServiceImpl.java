package com.brix.campus.service.impl;

import com.brix.campus.ai.AiPrompts;
import com.brix.campus.ai.AuditVerdict;
import com.brix.campus.ai.ItemAuditAgent;
import com.brix.campus.common.BusinessException;
import com.brix.campus.service.PublishAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布合规审核实现：两道闸。
 *
 * <p>第一道「违禁词闸」：代码内置黑名单（判定规则第 1/2/3/4 条的高频词），
 * contains 命中即拒——确定性强、零延迟、不花钱、断网可用；</p>
 *
 * <p>第二道「AI 审核员闸」：把分类+标题+描述交给 {@link ItemAuditAgent} 复审，
 * 能识别谐音变体、隐晦暗示、挂羊头卖狗肉等词表盲区。
 * 降级原则：无 Key / 超时 / 异常 / 判定值不认识，一律记日志后放行——
 * AI 只能拒绝，不能故障阻断发布。</p>
 *
 * <p>注意：审核调用<b>不消耗</b>用户每日 AI 额度（额度只计估价与文案）。</p>
 */
@Service
public class PublishAuditServiceImpl implements PublishAuditService {

    private static final Logger log = LoggerFactory.getLogger(PublishAuditServiceImpl.class);

    /**
     * 违禁词黑名单：key=违规分类（提示文案用），value=关键词列表。
     * 覆盖判定规则第 1（高危禁售）、2（假冒侵权）、3（站外引流）、4（广告违规）的高频词；
     * 变体与隐晦表达交给第二道 AI 闸识别。
     */
    private static final Map<String, List<String>> BLOCKED_WORDS = buildBlockedWords();

    private final ObjectProvider<ItemAuditAgent> auditAgentProvider;

    public PublishAuditServiceImpl(ObjectProvider<ItemAuditAgent> auditAgentProvider) {
        this.auditAgentProvider = auditAgentProvider;
    }

    @Override
    public void check(String title, String description, String categoryName) {
        // ---------------- 第一道：违禁词闸 ----------------
        String content = (safe(title) + "\n" + safe(description)).toLowerCase();
        for (Map.Entry<String, List<String>> entry : BLOCKED_WORDS.entrySet()) {
            for (String word : entry.getValue()) {
                if (content.contains(word.toLowerCase())) {
                    log.info("发布被违禁词闸拦截：category={}，word={}", entry.getKey(), word);
                    throw BusinessException.param(
                            "发布内容涉嫌【" + entry.getKey() + "】（命中敏感词），请修改标题或描述后重试");
                }
            }
        }

        // ---------------- 第二道：AI 审核员闸 ----------------
        ItemAuditAgent agent = auditAgentProvider.getIfAvailable();
        if (agent == null) {
            // 无 API Key：未创建审核智能体 Bean，仅靠词表闸兜底
            return;
        }
        AuditVerdict verdict;
        try {
            verdict = agent.audit(AiPrompts.auditUserPrompt(categoryName, title, description));
        } catch (Exception e) {
            // 超时 / 网络 / 解析失败：降级放行，不阻断发布
            log.warn("AI 审核降级放行（LLM 不可用）：{}", e.getMessage());
            return;
        }
        if (verdict == null || verdict.getVerdict() == null) {
            log.warn("AI 审核降级放行（返回为空或格式不识别）");
            return;
        }
        applyVerdict(verdict);
    }

    /**
     * 按 AI 判定结果分流处理。
     *
     * @param verdict 审核结论
     */
    private void applyVerdict(AuditVerdict verdict) {
        switch (verdict.getVerdict()) {
            case AuditVerdict.PASS -> { /* 审核通过 */ }
            case AuditVerdict.REJECT_BAN -> {
                log.info("AI 审核禁止上架：{}", verdict.getReason());
                throw BusinessException.param("审核未通过（禁止上架）："
                        + safe(verdict.getReason()));
            }
            case AuditVerdict.REJECT_REVISE -> {
                log.info("AI 审核要求修改后重审：{}", verdict.getReason());
                throw BusinessException.param("审核未通过（请修改后重新发布）："
                        + safe(verdict.getReason())
                        + (isBlank(verdict.getSuggestion()) ? "" : "。修改建议：" + verdict.getSuggestion()));
            }
            case AuditVerdict.REPORT_RISK -> {
                // 高风险金融类：暂无风控后台，先阻断发布并留痕日志
                log.warn("AI 审核上报风控：reason={}，legalBasis={}，violationText={}",
                        verdict.getReason(), verdict.getLegalBasis(), verdict.getViolationText());
                throw BusinessException.param("商品涉及高风险内容，已提交平台风控复核，暂时无法发布");
            }
            default -> log.warn("AI 审核降级放行（未知判定值：{}）", verdict.getVerdict());
        }
    }

    /**
     * 构建违禁词库（LinkedHashMap 保持分类展示顺序）。
     *
     * @return 分类 → 词表
     */
    private static Map<String, List<String>> buildBlockedWords() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("管制武器与危险物品", List.of(
                "枪支", "手枪", "步枪", "弹药", "子弹", "炸药", "雷管", "管制刀具", "弩",
                "电击器", "易燃易爆", "易制毒", "危化品"));
        map.put("毒品与吸毒工具", List.of(
                "冰毒", "大麻", "摇头丸", "k粉", "鸦片", "吗啡", "麻黄素", "吸毒工具"));
        map.put("违禁药品", List.of(
                "处方药", "假药", "医美针剂", "玻尿酸", "肉毒素"));
        map.put("色情低俗", List.of(
                "色情", "淫秽", "裸聊", "援交", "约炮", "嫖娼"));
        map.put("赌博", List.of(
                "博彩", "赌博", "六合彩", "外围下注", "赌资"));
        map.put("证照与个人信息", List.of(
                "假证", "代开发票", "证件代办", "身份证买卖", "收购身份证", "公民个人信息",
                "银行卡买卖", "收购银行卡", "电话卡买卖", "收购电话卡"));
        map.put("学术不端与作弊", List.of(
                "代考", "替考", "保过", "论文代写", "代写论文", "刷单", "刷课", "外挂", "翻墙"));
        map.put("野生动物制品", List.of(
                "象牙", "穿山甲", "红珊瑚", "玳瑁", "熊掌"));
        map.put("烟草制品", List.of(
                "香烟", "整条烟", "电子烟", "烟弹"));
        map.put("假冒侵权", List.of(
                "高仿", "a货", "复刻表", "假货", "1:1精仿", "一比一精仿"));
        map.put("引导站外交易", List.of(
                "微信号", "加微信", "加v", "加好友", "私聊", "私下转账", "货到付款", "不走平台"));
        map.put("广告绝对化用语", List.of(
                "全网最低", "全网第一", "顶级", "根治", "万能", "特效"));
        return Collections.unmodifiableMap(map);
    }

    /**
     * null 安全转字符串。
     *
     * @param value 原值
     * @return 非 null 字符串
     */
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 空白判断。
     *
     * @param value 原值
     * @return 是否空白
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
