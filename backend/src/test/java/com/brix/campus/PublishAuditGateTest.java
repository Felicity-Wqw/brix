package com.brix.campus;

import com.brix.campus.ai.AuditVerdict;
import com.brix.campus.ai.ItemAuditAgent;
import com.brix.campus.model.dto.AuditDecision;
import com.brix.campus.model.enums.RiskLevelEnum;
import com.brix.campus.service.impl.PublishAuditServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;

/**
 * 发布合规两道闸 / 三级分流单元测试（不依赖数据库与真实 LLM）。
 *
 * <p>测试对象是 {@link PublishAuditServiceImpl#judge} 的输出，而不是「抛不抛异常」——
 * P2 之后审核不再中断发布，而是返回一个风险级别，由商品服务按级别落不同状态。
 * 因此断言的是 {@code decision.level()} 与两道闸的明细。</p>
 *
 * <p>覆盖点：</p>
 * <ul>
 *   <li>低风险：两道闸都放行 → LOW（直发上架）；</li>
 *   <li>中风险：轻度关键词命中（玩具枪）/ 交易形态词（代购）/ AI 模糊 → MEDIUM（转人工）；</li>
 *   <li>高风险：黑名单命中（代开发票）/ AI 判不可上架 → HIGH（拦截留痕）；</li>
 *   <li>取严原则：黑名单命中时 AI 说 PASS 也不能降级；轻度词命中时 AI 说 PASS 也不能降级；</li>
 *   <li>留痕原则：黑名单已定论时 AI 仍被调用一次，日志才能记下第二道闸结果；</li>
 *   <li>降级原则：AI 抛异常 / 返回 null / 返回未知值 → 只看关键词闸，故障不阻断发布。</li>
 * </ul>
 */
class PublishAuditGateTest {

    private static final String TITLE = "九成新蓝牙耳机出售";
    private static final String DESC = "用了半年，音质很好，配件齐全。";
    private static final String CATEGORY = "数码电子 / 耳机/音箱";
    private static final BigDecimal PRICE = new BigDecimal("199.00");

    @SuppressWarnings("unchecked")
    private ObjectProvider<ItemAuditAgent> mockProvider(ItemAuditAgent agent) {
        ObjectProvider<ItemAuditAgent> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(agent);
        return provider;
    }

    private AuditVerdict verdictOf(String verdict) {
        AuditVerdict v = new AuditVerdict();
        v.setVerdict(verdict);
        v.setReason("测试原因");
        v.setSuggestion("测试建议");
        return v;
    }

    /** 构造一个固定返回某判定的服务 */
    private PublishAuditServiceImpl serviceWithVerdict(String verdict) {
        ItemAuditAgent agent = Mockito.mock(ItemAuditAgent.class);
        Mockito.when(agent.audit(Mockito.anyString())).thenReturn(verdictOf(verdict));
        return new PublishAuditServiceImpl(mockProvider(agent),
                Mockito.mock(com.brix.campus.service.ImageAuditService.class),
                Mockito.mock(com.brix.campus.service.ItemAuditLogService.class));
    }

    /** 无 AI（未配置 Key）的服务 */
    private PublishAuditServiceImpl serviceWithoutAi() {
        return new PublishAuditServiceImpl(mockProvider(null),
                Mockito.mock(com.brix.campus.service.ImageAuditService.class),
                Mockito.mock(com.brix.campus.service.ItemAuditLogService.class));
    }

    // ========================================================================
    // 低风险：两道闸都放行
    // ========================================================================

    @Test
    @DisplayName("低风险：无敏感词 + AI 通过 → LOW，两道闸明细都是 PASS")
    void lowRisk_whenBothGatesPass() {
        AuditDecision decision = serviceWithVerdict(AuditVerdict.PASS).judge(TITLE, DESC, CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.LOW, decision.level());
        Assertions.assertEquals(AuditDecision.Gate1.PASS, decision.gate1().result());
        Assertions.assertEquals(AuditDecision.Gate2.PASS, decision.gate2().result());
        Assertions.assertNull(decision.gate1().hitWord(), "未命中时不应有命中词");
        // 低风险没有给卖家看的说明，避免「通过」也弹一段话
        Assertions.assertNull(decision.reason());
        // 低风险落库状态必须是「在售」，这是「发完即可见」体验不倒退的关键
        Assertions.assertEquals("ON_SALE", RiskLevelEnum.LOW.getInitialStatus());
    }

    @Test
    @DisplayName("低风险：无 Key 时只走关键词闸，AI 记 UNAVAILABLE")
    void lowRisk_withoutAi() {
        AuditDecision decision = serviceWithoutAi().judge(TITLE, DESC, CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.LOW, decision.level());
        Assertions.assertEquals(AuditDecision.Gate2.UNAVAILABLE, decision.gate2().result(),
                "无 AI 时必须显式记成「未参与」，不能伪装成 PASS");
    }

    // ========================================================================
    // 中风险：轻度关键词 / 交易形态词 / AI 模糊
    // ========================================================================

    @Test
    @DisplayName("中风险：轻度关键词「玩具枪」命中 → MEDIUM，转人工")
    void mediumRisk_onMildKeyword() {
        AuditDecision decision = serviceWithVerdict(AuditVerdict.PASS)
                .judge("水弹玩具枪 送两包弹", "玩过两次，配件齐全。", "运动户外 / 球类运动", PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.MEDIUM, decision.level());
        Assertions.assertEquals(AuditDecision.Gate1.MILD_HIT, decision.gate1().result());
        Assertions.assertEquals("玩具枪", decision.gate1().hitWord());
        Assertions.assertEquals("玩具与仿真武器", decision.gate1().category());
        Assertions.assertTrue(decision.reason().contains("玩具枪"), decision.reason());
        Assertions.assertEquals("PENDING", RiskLevelEnum.MEDIUM.getInitialStatus());
    }

    @Test
    @DisplayName("中风险：交易形态词「代购」命中 → MEDIUM")
    void mediumRisk_onTradeShapeKeyword() {
        AuditDecision decision = serviceWithVerdict(AuditVerdict.PASS)
                .judge("日本代购 保温杯", "朋友从日本带回来的，多买了几个。", "生活用品 / 厨房用品", PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.MEDIUM, decision.level());
        Assertions.assertEquals(AuditDecision.Gate1.MILD_HIT, decision.gate1().result());
        Assertions.assertEquals("代购", decision.gate1().hitWord());
    }

    @Test
    @DisplayName("中风险：AI 拿不准（要求修改后重审）→ MEDIUM")
    void mediumRisk_onAiUncertain() {
        AuditDecision decision = serviceWithVerdict(AuditVerdict.REJECT_REVISE)
                .judge(TITLE, DESC, CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.MEDIUM, decision.level());
        Assertions.assertEquals(AuditDecision.Gate2.UNCERTAIN, decision.gate2().result());
        Assertions.assertEquals(AuditDecision.SOURCE_AI, decision.source());
    }

    @Test
    @DisplayName("中风险：AI 上报风控（模糊地带）→ MEDIUM，不直接封杀")
    void mediumRisk_onAiReportRisk() {
        AuditDecision decision = serviceWithVerdict(AuditVerdict.REPORT_RISK)
                .judge(TITLE, DESC, CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.MEDIUM, decision.level());
        Assertions.assertEquals(AuditDecision.Gate2.UNCERTAIN, decision.gate2().result());
    }

    // ========================================================================
    // 高风险：黑名单 / AI 判不可上架
    // ========================================================================

    @Test
    @DisplayName("高风险：黑名单「代开发票」命中 → HIGH")
    void highRisk_onBlacklistKeyword() {
        AuditDecision decision = serviceWithVerdict(AuditVerdict.REJECT_BAN)
                .judge("代开发票 各类抬头", "可开各类发票，当天出。", "其他", PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.HIGH, decision.level());
        Assertions.assertEquals(AuditDecision.Gate1.BLACK_HIT, decision.gate1().result());
        Assertions.assertEquals("代开发票", decision.gate1().hitWord());
        Assertions.assertEquals(AuditDecision.SOURCE_WORD, decision.source());
        Assertions.assertEquals("REJECTED", RiskLevelEnum.HIGH.getInitialStatus());
    }

    @Test
    @DisplayName("高风险：关键词无命中但 AI 判不可上架 → HIGH，来源是 AI")
    void highRisk_onAiBan() {
        AuditDecision decision = serviceWithVerdict(AuditVerdict.REJECT_BAN)
                .judge(TITLE, DESC, CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.HIGH, decision.level());
        Assertions.assertEquals(AuditDecision.Gate1.PASS, decision.gate1().result());
        Assertions.assertEquals(AuditDecision.SOURCE_AI, decision.source());
    }

    // ========================================================================
    // 取严原则 + 留痕原则
    // ========================================================================

    @Test
    @DisplayName("取严：黑名单命中直接定高危，AI 复核转后台补档（gate2 记 PENDING_BACKFILL）")
    void stricter_blacklistBeatsAiPass() {
        AuditDecision decision = serviceWithVerdict(AuditVerdict.PASS)
                .judge("出售自制枪支配件", DESC, CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.HIGH, decision.level(), "黑名单命中必须保持高危");
        Assertions.assertEquals(AuditDecision.Gate1.BLACK_HIT, decision.gate1().result());
        Assertions.assertEquals(AuditDecision.Gate2.UNAVAILABLE, decision.gate2().result(),
                "黑名单结论已足以定级，文本 AI 复核不再阻塞发布，gate2 记待补档");
        Assertions.assertNotNull(decision.gate2().reason(), "降级原因要写清楚，便于排查");
    }

    @Test
    @DisplayName("取严：轻度词命中的商品，AI 说 PASS 也不能降到低风险")
    void stricter_mildKeywordBeatsAiPass() {
        AuditDecision decision = serviceWithVerdict(AuditVerdict.PASS)
                .judge("水弹玩具枪 送两包弹", DESC, CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.MEDIUM, decision.level(), "轻度词命中必须保持中危");
        Assertions.assertEquals(AuditDecision.Gate1.MILD_HIT, decision.gate1().result());
    }

    @Test
    @DisplayName("留痕：黑名单已定论时，文本 AI 不再同步调用（发布零等待，复核转后台补档）")
    void auditTrail_aiNotCalledOnBlacklist() {
        ItemAuditAgent agent = Mockito.mock(ItemAuditAgent.class);
        Mockito.when(agent.audit(Mockito.anyString())).thenReturn(verdictOf(AuditVerdict.REJECT_BAN));
        new PublishAuditServiceImpl(mockProvider(agent),
                Mockito.mock(com.brix.campus.service.ImageAuditService.class),
                Mockito.mock(com.brix.campus.service.ItemAuditLogService.class))
                .judge("代开发票 各类抬头", DESC, CATEGORY, PRICE, null);

        Mockito.verify(agent, Mockito.never()).audit(Mockito.anyString());
    }

    @Test
    @DisplayName("词表闸：大小写不敏感——「加V」「K粉」等混写同样命中黑名单")
    void keywordGate_isCaseInsensitive() {
        PublishAuditServiceImpl service = serviceWithoutAi();

        Assertions.assertEquals(AuditDecision.Gate1.BLACK_HIT,
                service.judge("加V看货", DESC, CATEGORY, PRICE, null).gate1().result());
        Assertions.assertEquals(AuditDecision.Gate1.BLACK_HIT,
                service.judge(TITLE, "低价出售 K粉", CATEGORY, PRICE, null).gate1().result());
    }

    // ========================================================================
    // 降级原则
    // ========================================================================

    @Test
    @DisplayName("降级：LLM 抛异常时只看关键词闸，不阻断发布")
    void degrade_onException() {
        ItemAuditAgent agent = Mockito.mock(ItemAuditAgent.class);
        Mockito.when(agent.audit(Mockito.anyString())).thenThrow(new RuntimeException("connection timeout"));
        PublishAuditServiceImpl service = new PublishAuditServiceImpl(mockProvider(agent), Mockito.mock(com.brix.campus.service.ImageAuditService.class), Mockito.mock(com.brix.campus.service.ItemAuditLogService.class));

        AuditDecision decision = service.judge(TITLE, DESC, CATEGORY, PRICE, null);
        Assertions.assertEquals(RiskLevelEnum.LOW, decision.level());
        Assertions.assertEquals(AuditDecision.Gate2.UNAVAILABLE, decision.gate2().result());
        Assertions.assertNotNull(decision.gate2().reason(), "降级原因要写清楚，便于排查");
    }

    @Test
    @DisplayName("降级：AI 返回 null / 未知判定值 → UNAVAILABLE")
    void degrade_onNullOrUnknownVerdict() {
        ItemAuditAgent nullAgent = Mockito.mock(ItemAuditAgent.class);
        Mockito.when(nullAgent.audit(Mockito.anyString())).thenReturn(null);
        Assertions.assertEquals(AuditDecision.Gate2.UNAVAILABLE,
                new PublishAuditServiceImpl(mockProvider(nullAgent),
                        Mockito.mock(com.brix.campus.service.ImageAuditService.class),
                        Mockito.mock(com.brix.campus.service.ItemAuditLogService.class))
                        .judge(TITLE, DESC, CATEGORY, PRICE, null).gate2().result());

        ItemAuditAgent oddAgent = Mockito.mock(ItemAuditAgent.class);
        Mockito.when(oddAgent.audit(Mockito.anyString())).thenReturn(verdictOf("MAYBE"));
        AuditDecision decision = new PublishAuditServiceImpl(mockProvider(oddAgent), Mockito.mock(com.brix.campus.service.ImageAuditService.class), Mockito.mock(com.brix.campus.service.ItemAuditLogService.class))
                .judge(TITLE, DESC, CATEGORY, PRICE, null);
        Assertions.assertEquals(AuditDecision.Gate2.UNAVAILABLE, decision.gate2().result());
        Assertions.assertEquals(RiskLevelEnum.LOW, decision.level(), "未知判定不能误判成违规");
    }

    @Test
    @DisplayName("降级：AI 不可用时，关键词闸仍能独立拦下黑名单")
    void degrade_stillBlocksBlacklist() {
        AuditDecision decision = serviceWithoutAi().judge("出售自制枪支配件", DESC, CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.HIGH, decision.level());
        Assertions.assertEquals(AuditDecision.Gate2.UNAVAILABLE, decision.gate2().result());
    }

    // ========================================================================
    // 第一道闸加固：归一化变体拦截 + 豁免语境
    // ========================================================================

    @Test
    @DisplayName("变体：空白分隔的黑名单词（代 开 发 票）仍判高危")
    void variant_spacedBlacklist() {
        AuditDecision decision = serviceWithoutAi().judge("低价出闲置", "代 开 发 票 各类抬头", CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.HIGH, decision.level());
        Assertions.assertEquals("代开发票", decision.gate1().hitWord());
    }

    @Test
    @DisplayName("变体：符号分隔（代开·发票）仍判高危")
    void variant_punctuatedBlacklist() {
        AuditDecision decision = serviceWithoutAi().judge("低价出闲置", "代开·发票 加急", CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.HIGH, decision.level());
        Assertions.assertEquals("代开发票", decision.gate1().hitWord());
    }

    @Test
    @DisplayName("变体：全角字符（加Ｖ信）仍判高危")
    void variant_fullWidth() {
        AuditDecision decision = serviceWithoutAi().judge("低价出闲置", "便宜出，加Ｖ信聊", CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.HIGH, decision.level());
        Assertions.assertEquals(AuditDecision.Gate1.BLACK_HIT, decision.gate1().result());
    }

    @Test
    @DisplayName("变体：繁体词表（代開發票）仍判高危")
    void variant_traditionalBlacklist() {
        AuditDecision decision = serviceWithoutAi().judge("低价出闲置", "代開發票 各类抬头", CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.HIGH, decision.level());
        Assertions.assertEquals("代開發票", decision.gate1().hitWord());
    }

    @Test
    @DisplayName("豁免：来源描述「朋友代购带回」不再误判为交易意图")
    void exempt_daigouProvenance() {
        AuditDecision decision = serviceWithoutAi()
                .judge("闲置蓝牙耳机", "朋友代购带回，没用几次，现低价转卖。", CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.LOW, decision.level());
        Assertions.assertEquals(AuditDecision.Gate1.PASS, decision.gate1().result());
    }

    @Test
    @DisplayName("豁免：只豁免命中的词，其余词仍正常判定")
    void exempt_onlySkipsMatchedWord() {
        // 内容同时含「朋友代购带回」（豁免）与「玩具枪」（轻度词，不豁免）
        AuditDecision decision = serviceWithoutAi()
                .judge("闲置玩具枪", "朋友代购带回，孩子不要了。", "玩具 / 模型", PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.MEDIUM, decision.level());
        Assertions.assertEquals("玩具枪", decision.gate1().hitWord());
    }

    @Test
    @DisplayName("豁免不误伤：真实交易意图「可代购」仍判中危")
    void exempt_doesNotHideIntent() {
        AuditDecision decision = serviceWithoutAi().judge("海外好物", "长期可代购 各类日妆，价格私聊。", CATEGORY, PRICE, null);

        Assertions.assertEquals(RiskLevelEnum.MEDIUM, decision.level());
        Assertions.assertEquals("代购", decision.gate1().hitWord());
    }

    @Test
    @DisplayName("豁免：弓箭步 / 香烟盒等衍生词不误杀")
    void exempt_derivedPhrases() {
        AuditDecision fitness = serviceWithoutAi().judge("跑步装备", "练弓箭步专用的束腰，几乎全新。", "运动户外", PRICE, null);
        Assertions.assertEquals(RiskLevelEnum.LOW, fitness.level());

        AuditDecision collection = serviceWithoutAi().judge("老物件收藏", "收来一组香烟盒，全是老包装。", "收藏", PRICE, null);
        Assertions.assertEquals(RiskLevelEnum.LOW, collection.level());
    }
}
