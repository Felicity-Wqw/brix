package com.brix.campus.job;

import com.brix.campus.service.JuryService;
import com.brix.campus.service.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 售后与小法庭超时自动流转任务（每分钟扫描，全部状态推进走条件更新防并发）。
 *
 * <p>覆盖 6 条超时规则：
 * ① 卖家 36h 未处理 → 自动同意退款；
 * ② 买家 7 日未寄回 → 关单；
 * ③ 卖家 7 日未确认收货 → 自动退款；
 * ④ 被拒 5 日未响应 → 失效关闭；
 * ⑤ 举证期 24h 到 → 转陪审；
 * ⑥ 陪审期 48h 到 → 推送管理端。</p>
 */
@Component
public class RefundTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(RefundTimeoutJob.class);

    private final RefundService refundService;
    private final JuryService juryService;

    public RefundTimeoutJob(RefundService refundService, JuryService juryService) {
        this.refundService = refundService;
        this.juryService = juryService;
    }

    /**
     * 每 1 分钟执行一次（固定延迟，与订单超时关单任务同节奏）。
     */
    @Scheduled(initialDelay = 90_000L, fixedDelay = 60_000L)
    public void process() {
        safe("卖家超时自动同意", refundService::autoAgreeOnSellerTimeout);
        safe("退货超时关单", refundService::autoCloseReturnTimeout);
        safe("收货超时自动退款", refundService::autoRefundOnReceiveTimeout);
        safe("拒绝后失效关闭", refundService::autoInvalidateRejected);
        safe("举证期转陪审", juryService::transferEvidenceTimeout);
        safe("陪审期推管理端", juryService::transferVoteTimeout);
    }

    private void safe(String name, TimeoutAction action) {
        try {
            int handled = action.run();
            if (handled > 0) {
                log.info("售后自动流转[{}]：处理 {} 件", name, handled);
            }
        } catch (Exception e) {
            log.error("售后自动流转[{}]失败", name, e);
        }
    }

    @FunctionalInterface
    private interface TimeoutAction {
        int run();
    }
}
