package com.brix.campus.service.impl;

import com.brix.campus.ai.AiPrompts;
import com.brix.campus.ai.AuditVerdict;
import com.brix.campus.ai.ItemAuditAgent;
import com.brix.campus.model.dto.AuditDecision;
import com.brix.campus.model.enums.RiskLevelEnum;
import com.brix.campus.service.PublishAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 发布合规审核实现：两道闸，三级分流。
 *
 * <h3>第一道闸：关键词</h3>
 * <p>本地词表，同步、毫秒级、零成本、断网可用。按严重度分三层扫描，命中即停：</p>
 * <ul>
 *   <li><b>黑名单</b>（{@link #BLACKLIST_WORDS}）→ 高危：枪支、毒品、色情、赌博、证件买卖、学术作弊、
 *       假冒侵权、站外引流等确定性违规；</li>
 *   <li><b>轻度风险词</b>（{@link #MILD_WORDS}）→ 中危：玩具枪、仿真枪、管制刀具、甩棍、电子烟、
 *       医美针剂这类「存在风险但校园里确实有人转手」的品类，需要人判断；</li>
 *   <li><b>交易形态词</b>（{@link #SOFT_WORDS}）→ 中危：代购、账号转让、票券、租赁押金、预付转账，
 *       商品本身未必违规，但交易方式可能绕开平台。</li>
 * </ul>
 *
 * <h3>第二道闸：AI 审核员</h3>
 * <p>识别谐音变体、隐晦暗示、挂羊头卖狗肉等词表盲区。三种结果：</p>
 * <ul>
 *   <li>通过 → 低危；</li>
 *   <li>模糊不确定（要求修改后重审 / 上报风控）→ 中危，交人判断；</li>
 *   <li>判定无法上架 → 高危。</li>
 * </ul>
 *
 * <h3>叠加规则</h3>
 * <p>两道闸各自定级后取更严者：关键词已判黑名单，AI 说「通过」也不能降级放行。
 * 反过来 AI 可以把「关键词未命中」的商品升级为高危。</p>
 *
 * <p><b>降级原则</b>：AI 未配置 Key / 超时 / 解析失败时记 {@code gate2=UNAVAILABLE}，
 * 结论只按关键词闸走——AI 只能加严，不能故障阻断发布。</p>
 *
 * <p><b>为什么黑名单命中还调 AI</b>：日志要留存「第二道闸的审核结果」。
 * 黑名单命中虽然已是定论，但把 AI 的判断一并记下，才构成完整的审核证据链。
 * 违规商品是极少数，多一次调用换完整留痕，值得。</p>
 */
@Service
public class PublishAuditServiceImpl implements PublishAuditService {

    private static final Logger log = LoggerFactory.getLogger(PublishAuditServiceImpl.class);

    /** 黑名单：命中即高危 */
    private static final Map<String, List<String>> BLACKLIST_WORDS = buildBlacklist();

    /** 轻度风险词：命中即中危（品类本身有风险，校园场景常见） */
    private static final Map<String, List<String>> MILD_WORDS = buildMildWords();

    /** 交易形态词：命中即中危（可能绕开平台交易） */
    private static final Map<String, List<String>> SOFT_WORDS = buildSoftWords();

    /** 命中豁免：内容出现这些语境短语时，对应词视为未命中（防来源描述被误判为交易意图） */
    private static final Map<String, Pattern> EXEMPT_PATTERNS = buildExemptPatterns();

    /** 紧凑形态匹配的最短黑名单词长：防止跨标点拼接出短词误伤 */
    private static final int COMPACT_MIN_LENGTH = 4;

    private final ObjectProvider<ItemAuditAgent> auditAgentProvider;

    public PublishAuditServiceImpl(ObjectProvider<ItemAuditAgent> auditAgentProvider) {
        this.auditAgentProvider = auditAgentProvider;
    }

    @Override
    public AuditDecision judge(String title, String description, String categoryName, BigDecimal price) {
        // 三种文本形态参与匹配：原文（摘录/豁免判断）、去空白（拦「代 开 发 票」）、紧凑（拦「代开·发票」）
        String rawLower = (safe(title) + "\n" + safe(description)).toLowerCase(Locale.ROOT);
        String noSpace = normalizeForm(rawLower, false);
        String compact = normalizeForm(rawLower, true);

        // ---------------- 第一道闸：关键词 ----------------
        AuditDecision.Gate1 gate1 = scanKeywords(rawLower, noSpace, compact);

        // ---------------- 第二道闸：AI（始终执行，用于留痕；结论取严） ----------------
        AuditDecision.Gate2 gate2 = scanByAi(title, description, categoryName);

        // 两道闸各自定级，取更严者
        RiskLevelEnum level = stricter(levelOfGate1(gate1), levelOfGate2(gate2));

        String reason;
        String suggestion;
        String source;
        if (level == RiskLevelEnum.HIGH) {
            source = gate1.hitBlack() ? AuditDecision.SOURCE_WORD : AuditDecision.SOURCE_AI;
            reason = blackReasonOf(gate1, gate2);
            suggestion = suggestionOf(gate1, gate2, "请删除相关违禁内容；如认为判定有误，可在商品详情页提交申诉。");
        } else if (level == RiskLevelEnum.MEDIUM) {
            source = gate1.hitMild() ? AuditDecision.SOURCE_WORD : AuditDecision.SOURCE_AI;
            reason = mediumReasonOf(gate1, gate2);
            suggestion = suggestionOf(gate1, gate2,
                    "如为正常二手转让，可补充实物照片与来源说明，便于审核员复核。");
        } else {
            source = AuditDecision.SOURCE_WORD;
            reason = null;
            suggestion = null;
        }

        AuditDecision decision = new AuditDecision(level, source, reason, suggestion, gate1, gate2);
        log.info("发布审核：level={}，gate1={}（{}），gate2={}（{}）",
                level.getCode(), gate1.result(),
                gate1.hitWord() == null ? "-" : gate1.hitWord(), gate2.result(), gate2.verdict());
        return decision;
    }

    // ========================================================================
    // 第一道闸
    // ========================================================================

    /**
     * 关键词扫描：黑名单 → 轻度词 → 交易形态词，逐层命中即停。
     *
     * <p>黑名单在原文、去空白、紧凑三种形态上匹配（拦分隔符/全角变体）；
     * 轻度与交易形态词只在原文、去空白上匹配（保留标点防拼接误伤），并支持豁免语境。</p>
     *
     * @param rawLower 原文小写
     * @param noSpace  去空白文本（NFKC 折叠后）
     * @param compact  紧凑文本（NFKC 折叠后，仅黑名单使用）
     * @return 第一道闸结果
     */
    private AuditDecision.Gate1 scanKeywords(String rawLower, String noSpace, String compact) {
        String[] black = firstHit(rawLower, noSpace, compact, BLACKLIST_WORDS, null);
        if (black != null) {
            return AuditDecision.Gate1.black(black[0], black[1]);
        }
        String[] mild = firstHit(rawLower, noSpace, null, MILD_WORDS, EXEMPT_PATTERNS);
        if (mild != null) {
            return AuditDecision.Gate1.mild(mild[0], mild[1]);
        }
        String[] soft = firstHit(rawLower, noSpace, null, SOFT_WORDS, EXEMPT_PATTERNS);
        if (soft != null) {
            return AuditDecision.Gate1.mild(soft[0], soft[1]);
        }
        return AuditDecision.Gate1.pass();
    }

    /**
     * 在多形态文本中查找第一个命中项。
     *
     * <p>扫描顺序：原文 → 去空白 → 紧凑。紧凑形态仅黑名单启用且词长 ≥
     * {@link #COMPACT_MIN_LENGTH}，防止跨标点拼接出短词误伤。</p>
     *
     * <p>轻度/交易形态词支持豁免：内容命中豁免短语（如「朋友代购带回」）时跳过该词，
     * 继续匹配其余词，避免来源描述被误判为交易意图。</p>
     *
     * @param rawLower 原文小写
     * @param noSpace  去空白文本，可为 null
     * @param compact  紧凑文本，null 表示不启用
     * @param dict     类目 → 词表（词已预转小写）
     * @param exempt   词 → 豁免模式，可为 null
     * @return {@code [命中词, 类目]}，未命中返回 null
     */
    private String[] firstHit(String rawLower, String noSpace, String compact,
                              Map<String, List<String>> dict, Map<String, Pattern> exempt) {
        for (Map.Entry<String, List<String>> entry : dict.entrySet()) {
            for (String word : entry.getValue()) {
                boolean hit = rawLower.contains(word)
                        || (noSpace != null && noSpace.contains(word))
                        || (compact != null && word.length() >= COMPACT_MIN_LENGTH && compact.contains(word));
                if (!hit) {
                    continue;
                }
                if (exempt != null) {
                    Pattern skip = exempt.get(word);
                    if (skip != null && skip.matcher(rawLower).find()) {
                        continue;
                    }
                }
                return new String[]{word, entry.getKey()};
            }
        }
        return null;
    }

    /**
     * 第一道闸定级：黑名单 → 高危，轻度/交易形态 → 中危，未命中 → 低危。
     *
     * @param gate1 第一道闸结果
     * @return 级别
     */
    private RiskLevelEnum levelOfGate1(AuditDecision.Gate1 gate1) {
        if (gate1.hitBlack()) {
            return RiskLevelEnum.HIGH;
        }
        return gate1.hitMild() ? RiskLevelEnum.MEDIUM : RiskLevelEnum.LOW;
    }

    // ========================================================================
    // 第二道闸
    // ========================================================================

    /**
     * AI 审核：不可用即记 UNAVAILABLE，不阻断发布。
     *
     * @param title        标题
     * @param description  描述
     * @param categoryName 分类名
     * @return 第二道闸结果
     */
    private AuditDecision.Gate2 scanByAi(String title, String description, String categoryName) {
        ItemAuditAgent agent = auditAgentProvider.getIfAvailable();
        if (agent == null) {
            return AuditDecision.Gate2.unavailable("未配置 AI 审核员（无 API Key），本次仅执行关键词闸");
        }
        AuditVerdict verdict;
        try {
            verdict = agent.audit(AiPrompts.auditUserPrompt(categoryName, title, description));
        } catch (Exception e) {
            log.warn("AI 审核降级（LLM 不可用）：{}", e.getMessage());
            return AuditDecision.Gate2.unavailable("AI 审核调用失败（超时或网络异常），已降级为关键词闸");
        }
        if (verdict == null || verdict.getVerdict() == null) {
            log.warn("AI 审核降级（返回为空或格式不识别）");
            return AuditDecision.Gate2.unavailable("AI 审核未返回可识别的判定，已降级为关键词闸");
        }
        String raw = verdict.getVerdict();
        String reason = isBlank(verdict.getReason()) ? null : verdict.getReason().trim();
        String suggestion = isBlank(verdict.getSuggestion()) ? null : verdict.getSuggestion().trim();
        return switch (raw) {
            case AuditVerdict.PASS -> AuditDecision.Gate2.pass(raw, reason);
            // 「要求修改后重审」本质是 AI 拿不准，交人工判断，不直接封杀
            case AuditVerdict.REJECT_REVISE -> AuditDecision.Gate2.uncertain(raw, reason, suggestion);
            // 「上报风控」属模糊地带（如疑似高风险金融类），同样交人工
            case AuditVerdict.REPORT_RISK -> AuditDecision.Gate2.uncertain(raw, reason, suggestion);
            case AuditVerdict.REJECT_BAN -> AuditDecision.Gate2.ban(raw, reason, suggestion);
            default -> {
                log.warn("AI 审核出现未知判定值：{}", raw);
                yield AuditDecision.Gate2.unavailable("AI 返回了未知判定值「" + raw + "」，已降级为关键词闸");
            }
        };
    }

    /**
     * 第二道闸定级：无法上架 → 高危，模糊不确定 → 中危，通过/不可用 → 低危。
     *
     * @param gate2 第二道闸结果
     * @return 级别
     */
    private RiskLevelEnum levelOfGate2(AuditDecision.Gate2 gate2) {
        if (gate2.isBan()) {
            return RiskLevelEnum.HIGH;
        }
        return gate2.isUncertain() ? RiskLevelEnum.MEDIUM : RiskLevelEnum.LOW;
    }

    /**
     * 取两个级别中更严的一个。
     *
     * @param a 第一道闸级别
     * @param b 第二道闸级别
     * @return 更严的级别
     */
    private RiskLevelEnum stricter(RiskLevelEnum a, RiskLevelEnum b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    // ========================================================================
    // 说明文案拼装
    // ========================================================================

    /**
     * 高危说明：优先用黑名单命中信息，其次用 AI 的禁止理由。
     *
     * @param gate1 第一道闸
     * @param gate2 第二道闸
     * @return 说明
     */
    private String blackReasonOf(AuditDecision.Gate1 gate1, AuditDecision.Gate2 gate2) {
        if (gate1.hitBlack()) {
            return "涉嫌【" + gate1.category() + "】，命中黑名单关键词「" + gate1.hitWord() + "」";
        }
        return "AI 审核判定无法上架：" + safe(gate2.reason());
    }

    /**
     * 中危说明：区分「关键词命中轻度词」与「AI 模糊不确定」两种情况。
     *
     * @param gate1 第一道闸
     * @param gate2 第二道闸
     * @return 说明
     */
    private String mediumReasonOf(AuditDecision.Gate1 gate1, AuditDecision.Gate2 gate2) {
        if (gate1.hitMild()) {
            return "命中风险关键词「" + gate1.hitWord() + "」（类目【" + gate1.category()
                    + "】），是否可售需人工确认";
        }
        return "AI 审核未能确定是否违规，转人工复核：" + safe(gate2.reason());
    }

    /**
     * 修改建议：关键词给出的固定建议优先，其次 AI 的建议。
     *
     * @param gate1      第一道闸
     * @param gate2      第二道闸
     * @param fallback   兜底建议
     * @return 建议
     */
    private String suggestionOf(AuditDecision.Gate1 gate1, AuditDecision.Gate2 gate2, String fallback) {
        if (gate1.category() != null && gate2.suggestion() != null) {
            return gate2.suggestion();
        }
        return gate2.suggestion() != null ? gate2.suggestion() : fallback;
    }

    // ========================================================================
    // 词库
    // ========================================================================

    /**
     * 黑名单：确定性违规，命中即高危。
     *
     * @return 类目 → 词表
     */
    private static Map<String, List<String>> buildBlacklist() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("管制武器与爆炸物", List.of(
                "枪支", "手枪", "步枪", "弹药", "子弹", "炸药", "雷管", "易燃易爆", "危化品", "易制毒"));
        map.put("毒品与吸毒工具", List.of(
                "冰毒", "大麻", "摇头丸", "k粉", "鸦片", "吗啡", "麻黄素", "吸毒工具"));
        map.put("色情低俗", List.of(
                "色情", "淫秽", "裸聊", "援交", "约炮", "嫖娼"));
        map.put("赌博", List.of(
                "博彩", "赌博", "六合彩", "外围下注", "赌资"));
        map.put("证照与个人信息", List.of(
                "假证", "代开发票", "代開發票", "证件代办", "身份证买卖", "收购身份证", "公民个人信息",
                "银行卡买卖", "收购银行卡", "电话卡买卖", "收购电话卡"));
        map.put("学术不端与作弊", List.of(
                "代考", "替考", "论文代写", "論文代寫", "代写论文", "刷单", "刷课", "外挂", "翻墙"));
        map.put("野生动物制品", List.of(
                "象牙", "穿山甲", "红珊瑚", "玳瑁", "熊掌"));
        map.put("假冒侵权", List.of(
                "高仿", "a货", "复刻表", "假货", "1:1精仿", "一比一精仿"));
        map.put("引导站外交易", List.of(
                "微信号", "加微信", "加v", "加好友", "私下转账", "货到付款", "不走平台"));
        map.replaceAll((category, words) -> lowerAll(words));
        return Collections.unmodifiableMap(map);
    }

    /**
     * 轻度风险词：品类本身敏感，但校园里确实存在真实转让需求，交人工判断。
     *
     * <p>典型例子就是「玩具枪」——既不等于真枪，也不能直接放行。</p>
     *
     * @return 类目 → 词表
     */
    private static Map<String, List<String>> buildMildWords() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("玩具与仿真武器", List.of(
                "玩具枪", "仿真枪", "模型枪", "水弹枪", "bb弹", "彩弹枪", "弩", "弓箭", "弹弓"));
        map.put("管制器具与防身用品", List.of(
                "管制刀具", "匕首", "电击器", "电棍", "甩棍", "指虎", "防身喷雾", "辣椒水", "开锁工具"));
        map.put("烟草与药品", List.of(
                "香烟", "整条烟", "电子烟", "烟弹", "处方药", "假药", "医美针剂", "玻尿酸", "肉毒素"));
        map.put("广告绝对化用语", List.of(
                "全网最低", "全网第一", "根治", "万能", "特效", "保过"));
        map.replaceAll((category, words) -> lowerAll(words));
        return Collections.unmodifiableMap(map);
    }

    /**
     * 交易形态词：商品本身未必违规，但交易方式可能绕开平台，交人工确认。
     *
     * @return 类目 → 词表
     */
    private static Map<String, List<String>> buildSoftWords() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("账号与虚拟资产转让", List.of(
                "账号出售", "出售账号", "账号转让", "会员号", "共享会员", "游戏账号", "代练", "充值卡"));
        map.put("代购与批发", List.of(
                "代购", "批发", "一件代发", "工厂直供", "尾单", "囤货"));
        map.put("票券转让", List.of(
                "演唱会票", "门票转让", "代金券", "优惠券", "购物卡", "礼品卡", "饭卡"));
        map.put("租赁与押金", List.of(
                "出租", "租赁", "短租", "押金", "日租"));
        map.put("预付与线下转账", List.of(
                "定金", "预付", "先付", "扫码付", "二维码", "支付宝转", "加qq"));
        map.replaceAll((category, words) -> lowerAll(words));
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
     * 词表统一预转小写（构建期一次），扫描时不再逐词转换。
     *
     * @param words 原词表
     * @return 全小写词表
     */
    private static List<String> lowerAll(List<String> words) {
        return words.stream().map(word -> word.toLowerCase(Locale.ROOT)).toList();
    }

    /**
     * 豁免短语表：键为会误杀的词，值为「出现即视为未命中」的语境模式。
     *
     * <p>只对轻度/交易形态词生效，黑名单不豁免。</p>
     *
     * @return 词 → 豁免模式
     */
    private static Map<String, Pattern> buildExemptPatterns() {
        Map<String, Pattern> map = new LinkedHashMap<>();
        map.put("代购", Pattern.compile("代购(入手|回来|带回)|朋友代购|以前代购|之前代购"));
        map.put("批发", Pattern.compile("批发(价|买的|入的|进的)"));
        map.put("弓箭", Pattern.compile("弓箭步"));
        map.put("香烟", Pattern.compile("香烟盒|烟盒"));
        return Collections.unmodifiableMap(map);
    }

    /**
     * 归一化文本形态：NFKC 折叠全角/兼容字符 → 小写 → 去零宽与空白，按需再去标点符号。
     *
     * <p>两档用途：
     * <ul>
     *   <li>去空白（{@code stripPunctuation=false}）：拦「代 开 发 票」「代开\n发票」；</li>
     *   <li>紧凑（{@code stripPunctuation=true}）：再拦「代开·发票」，仅黑名单 + 词长 ≥4 使用。</li>
     * </ul>
     * 归一化结果只用于匹配，不改变落库内容。</p>
     *
     * @param text             原文（已转小写）
     * @param stripPunctuation 是否同时去除标点符号
     * @return 归一化文本
     */
    private static String normalizeForm(String text, boolean stripPunctuation) {
        String normalized = Normalizer.normalize(safe(text), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        String dropped = stripPunctuation ? "[\\p{Cf}\\p{Zs}\\p{P}\\p{S}\\s]" : "[\\p{Cf}\\p{Zs}\\s]";
        return normalized.replaceAll(dropped, "");
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
