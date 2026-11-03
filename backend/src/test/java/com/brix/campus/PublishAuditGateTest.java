package com.brix.campus;

import com.brix.campus.ai.AuditVerdict;
import com.brix.campus.ai.ItemAuditAgent;
import com.brix.campus.common.BusinessException;
import com.brix.campus.service.impl.PublishAuditServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 发布合规两道闸单元测试（不依赖数据库 / 真实 LLM）。
 *
 * <p>覆盖：违禁词闸（命中即拒、大小写不敏感、正常文案放行）、
 * AI 闸（PASS 放行、REJECT_BAN 拒绝、REPORT_RISK 阻断、
 * 异常/空结果/未知判定值降级放行、无 Key 场景只走词表闸）。</p>
 */
class PublishAuditGateTest {

    private static final String TITLE = "九成新蓝牙耳机出售";
    private static final String DESC = "用了半年，音质很好，配件齐全。";
    private static final String CATEGORY = "数码电子 / 耳机/音箱";

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
        return v;
    }

    // ---------------- 第一道：违禁词闸 ----------------

    @Test
    @DisplayName("词表闸：标题命中「枪支」直接拦截，且不触发 AI")
    void keywordGate_blocksOnHit() {
        ItemAuditAgent agent = Mockito.mock(ItemAuditAgent.class);
        PublishAuditServiceImpl service = new PublishAuditServiceImpl(mockProvider(agent));

        BusinessException ex = Assertions.assertThrows(BusinessException.class,
                () -> service.check("出售自制枪支配件", DESC, CATEGORY));
        Assertions.assertTrue(ex.getMessage().contains("管制武器"),
                "提示应包含违规分类：" + ex.getMessage());
        Mockito.verifyNoInteractions(agent); // 词表闸命中后不再调 AI
    }

    @Test
    @DisplayName("词表闸：大小写不敏感——「加V」「K粉」等混写同样命中")
    void keywordGate_isCaseInsensitive() {
        PublishAuditServiceImpl service = new PublishAuditServiceImpl(mockProvider(null));

        Assertions.assertThrows(BusinessException.class,
                () -> service.check("加V看货", DESC, CATEGORY), "大写 V 应命中「加v」");
        Assertions.assertThrows(BusinessException.class,
                () -> service.check(TITLE, "低价出售 K粉", CATEGORY), "大写 K 应命中「k粉」");
    }

    @Test
    @DisplayName("词表闸：正常文案放行（AI 为 null 即无 Key 场景）")
    void keywordGate_allowsNormalContent() {
        PublishAuditServiceImpl service = new PublishAuditServiceImpl(mockProvider(null));
        Assertions.assertDoesNotThrow(() -> service.check(TITLE, DESC, CATEGORY));
    }

    // ---------------- 第二道：AI 审核员闸 ----------------

    @Test
    @DisplayName("AI 闸：判定 PASS 放行")
    void aiGate_passesOnPass() {
        ItemAuditAgent agent = Mockito.mock(ItemAuditAgent.class);
        Mockito.when(agent.audit(Mockito.anyString())).thenReturn(verdictOf(AuditVerdict.PASS));
        PublishAuditServiceImpl service = new PublishAuditServiceImpl(mockProvider(agent));

        Assertions.assertDoesNotThrow(() -> service.check(TITLE, DESC, CATEGORY));
    }

    @Test
    @DisplayName("AI 闸：判定 REJECT_BAN 拒绝上架")
    void aiGate_rejectsOnBan() {
        ItemAuditAgent agent = Mockito.mock(ItemAuditAgent.class);
        Mockito.when(agent.audit(Mockito.anyString())).thenReturn(verdictOf(AuditVerdict.REJECT_BAN));
        PublishAuditServiceImpl service = new PublishAuditServiceImpl(mockProvider(agent));

        BusinessException ex = Assertions.assertThrows(BusinessException.class,
                () -> service.check(TITLE, DESC, CATEGORY));
        Assertions.assertTrue(ex.getMessage().contains("禁止上架"), ex.getMessage());
    }

    @Test
    @DisplayName("AI 闸：判定 REPORT_RISK 阻断发布")
    void aiGate_blocksOnReportRisk() {
        ItemAuditAgent agent = Mockito.mock(ItemAuditAgent.class);
        Mockito.when(agent.audit(Mockito.anyString())).thenReturn(verdictOf(AuditVerdict.REPORT_RISK));
        PublishAuditServiceImpl service = new PublishAuditServiceImpl(mockProvider(agent));

        Assertions.assertThrows(BusinessException.class,
                () -> service.check(TITLE, DESC, CATEGORY));
    }

    @Test
    @DisplayName("AI 闸：LLM 抛异常时降级放行（故障不阻断发布）")
    void aiGate_degradesOnException() {
        ItemAuditAgent agent = Mockito.mock(ItemAuditAgent.class);
        Mockito.when(agent.audit(Mockito.anyString())).thenThrow(new RuntimeException("connection timeout"));
        PublishAuditServiceImpl service = new PublishAuditServiceImpl(mockProvider(agent));

        Assertions.assertDoesNotThrow(() -> service.check(TITLE, DESC, CATEGORY));
    }

    @Test
    @DisplayName("AI 闸：返回 null / 未知判定值时降级放行")
    void aiGate_degradesOnUnknownVerdict() {
        ItemAuditAgent nullAgent = Mockito.mock(ItemAuditAgent.class);
        Mockito.when(nullAgent.audit(Mockito.anyString())).thenReturn(null);
        PublishAuditServiceImpl nullService = new PublishAuditServiceImpl(mockProvider(nullAgent));
        Assertions.assertDoesNotThrow(() -> nullService.check(TITLE, DESC, CATEGORY));

        ItemAuditAgent oddAgent = Mockito.mock(ItemAuditAgent.class);
        Mockito.when(oddAgent.audit(Mockito.anyString())).thenReturn(verdictOf("MAYBE"));
        PublishAuditServiceImpl oddService = new PublishAuditServiceImpl(mockProvider(oddAgent));
        Assertions.assertDoesNotThrow(() -> oddService.check(TITLE, DESC, CATEGORY));
    }
}
