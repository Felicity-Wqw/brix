package com.brix.campus.model.dto;

import com.brix.campus.model.enums.RiskLevelEnum;

import java.util.List;

/**
 * 发布审核结论（{@code PublishAuditService} 的输出）。
 *
 * <p>结论由两道闸叠加得出，两道闸的过程证据一并带出，供 {@code ItemServiceImpl}
 * 写进 {@code t_item_audit_log}（低风险直发也要留痕）。</p>
 *
 * <p>分流口径：</p>
 * <ul>
 *   <li>第一道闸（关键词）命中<b>黑名单</b> → 高危，直接拦截；</li>
 *   <li>第一道闸命中<b>轻度风险词</b> → 中危，转人工；</li>
 *   <li>第一道闸未命中 → 由第二道闸（AI）定级：通过=低危、模糊不确定=中危、无法上架=高危；</li>
 *   <li>两道闸都判出级别时取更严的一个，AI 不能把黑名单命中降级成放行。</li>
 * </ul>
 *
 * @param level      风险级别
 * @param source     定级来源：WORD 关键词 / AI 智能体 / RULE 规则兜底 / MANUAL 人工 / LEGACY 历史数据
 * @param reason     判定说明（给卖家看，会截断进 t_item.audit_reason）
 * @param suggestion 修改建议（可空）
 * @param gate1      第一道闸明细
 * @param gate2      第二道闸明细
 */
public record AuditDecision(RiskLevelEnum level, String source, String reason, String suggestion,
                           Gate1 gate1, Gate2 gate2) {

    /** 定级来源：关键词闸 */
    public static final String SOURCE_WORD = "WORD";
    /** 定级来源：AI 审核员 */
    public static final String SOURCE_AI = "AI";
    /** 定级来源：人工审核 */
    public static final String SOURCE_MANUAL = "MANUAL";
    /** 定级来源：改造前的历史数据，未经过任何闸 */
    public static final String SOURCE_LEGACY = "LEGACY";

    /**
     * 第一道闸（关键词）的结果。
     *
     * @param result   PASS 未命中 / MILD_HIT 命中轻度词 / BLACK_HIT 命中黑名单
     * @param hitWord  命中的具体词，未命中为 null
     * @param category 命中词所属类目，未命中为 null
     */
    public record Gate1(String result, String hitWord, String category) {

        public static final String PASS = "PASS";
        public static final String MILD_HIT = "MILD_HIT";
        public static final String BLACK_HIT = "BLACK_HIT";

        public static Gate1 pass() {
            return new Gate1(PASS, null, null);
        }

        public static Gate1 mild(String word, String category) {
            return new Gate1(MILD_HIT, word, category);
        }

        public static Gate1 black(String word, String category) {
            return new Gate1(BLACK_HIT, word, category);
        }

        public boolean hitBlack() {
            return BLACK_HIT.equals(result);
        }

        public boolean hitMild() {
            return MILD_HIT.equals(result);
        }
    }

    /**
     * 第二道闸（AI 审核）的结果：文本结论与图片结论取严合并成一条。
     *
     * @param result        PASS 通过 / UNCERTAIN 模糊不确定 / BAN 判定无法上架 / UNAVAILABLE 未启用或调用失败
     * @param verdict       AI 原始判定值（对照 prompt 契约用）
     * @param reason        AI 给出的理由（文字问题与图片问题写在一起）
     * @param suggestion    AI 给出的修改建议
     * @param imageFindings 逐图审核明细，无图或图片闸未执行时为 null
     */
    public record Gate2(String result, String verdict, String reason, String suggestion,
                        List<ImageFinding> imageFindings) {

        public static final String PASS = "PASS";
        public static final String UNCERTAIN = "UNCERTAIN";
        public static final String BAN = "BAN";
        public static final String UNAVAILABLE = "UNAVAILABLE";

        /** 无 AI（未配置 Key / 超时 / 解析失败）：只靠关键词闸兜底 */
        public static Gate2 unavailable(String why) {
            return new Gate2(UNAVAILABLE, null, why, null, null);
        }

        public static Gate2 pass(String verdict, String reason) {
            return new Gate2(PASS, verdict, reason, null, null);
        }

        public static Gate2 pass(String verdict, String reason, List<ImageFinding> imageFindings) {
            return new Gate2(PASS, verdict, reason, null, imageFindings);
        }

        public static Gate2 uncertain(String verdict, String reason, String suggestion) {
            return new Gate2(UNCERTAIN, verdict, reason, suggestion, null);
        }

        public static Gate2 uncertain(String verdict, String reason, String suggestion,
                                      List<ImageFinding> imageFindings) {
            return new Gate2(UNCERTAIN, verdict, reason, suggestion, imageFindings);
        }

        public static Gate2 ban(String verdict, String reason, String suggestion) {
            return new Gate2(BAN, verdict, reason, suggestion, null);
        }

        public static Gate2 ban(String verdict, String reason, String suggestion,
                                List<ImageFinding> imageFindings) {
            return new Gate2(BAN, verdict, reason, suggestion, imageFindings);
        }

        /** AI 是否明确判定「无法上架」 */
        public boolean isBan() {
            return BAN.equals(result);
        }

        /** AI 是否给出「模糊/不确定」的判断 */
        public boolean isUncertain() {
            return UNCERTAIN.equals(result);
        }
    }

    /**
     * 单张图片的审核明细（并入 Gate2，不单独成篇）。
     *
     * @param index   图片在相册中的序号（从 1 开始）
     * @param url     图片可访问路径
     * @param verdict PASS / REJECT / UNCERTAIN / FAILED（技术失败或超时）
     * @param reason  审核理由
     */
    public record ImageFinding(int index, String url, String verdict, String reason) {

        public static final String PASS = "PASS";
        public static final String REJECT = "REJECT";
        public static final String UNCERTAIN = "UNCERTAIN";
        public static final String FAILED = "FAILED";
    }

    /** 第一道闸未命中（低风险候选） */
    public static AuditDecision lowByKeyword(Gate2 gate2) {
        return new AuditDecision(RiskLevelEnum.LOW, SOURCE_WORD, null, null, Gate1.pass(), gate2);
    }

    /** 第一道闸命中轻度词，或 AI 模糊不确定 → 中危 */
    public static AuditDecision medium(String source, String reason, String suggestion,
                                       Gate1 gate1, Gate2 gate2) {
        return new AuditDecision(RiskLevelEnum.MEDIUM, source, reason, suggestion, gate1, gate2);
    }

    /** 命中黑名单，或 AI 判定无法上架 → 高危 */
    public static AuditDecision high(String source, String reason, String suggestion,
                                     Gate1 gate1, Gate2 gate2) {
        return new AuditDecision(RiskLevelEnum.HIGH, source, reason, suggestion, gate1, gate2);
    }

    /**
     * 取两个结论中更严的一个（级别高者胜）。
     *
     * <p>用于「关键词闸已判高危，AI 却判通过」的场景：结论必须保持高危，
     * 但两道闸的过程证据都要保留进日志。</p>
     *
     * @param other 另一道闸的结论，可为 null
     * @return 更严的结论
     */
    public AuditDecision stricter(AuditDecision other) {
        if (other == null || other.level == null) {
            return this;
        }
        if (level == null) {
            return other;
        }
        return other.level.ordinal() > level.ordinal() ? other : this;
    }
}
