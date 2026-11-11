package com.brix.campus.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.model.dto.AdminRulingDTO;
import com.brix.campus.model.dto.JuryDraftDTO;
import com.brix.campus.model.vo.AdminJuryCaseVO;
import com.brix.campus.security.SecurityUtils;
import com.brix.campus.service.JuryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端小法庭终审接口（审核员 REVIEWER）。
 *
 * <p>可见：双方真实身份 + 全部证据 + 投票统计（支持率/参与人数）；
 * 裁决支持买家 → 退款执行；支持卖家 → 售后关闭。</p>
 */
@RestController
@RequestMapping("/admin/refunds")
public class AdminJuryController {

    private final JuryService juryService;
    private final com.brix.campus.service.PayAlipayService payAlipayService;

    public AdminJuryController(JuryService juryService,
                               com.brix.campus.service.PayAlipayService payAlipayService) {
        this.juryService = juryService;
        this.payAlipayService = payAlipayService;
    }

    /** 待裁决案件分页 */
    @GetMapping("/jury/pending")
    public R<IPage<AdminJuryCaseVO>> pending(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size) {
        return R.ok(juryService.pagePendingRuling(page, size));
    }

    /** 案件详情（真实身份 + 票数统计） */
    @GetMapping("/jury/{id}")
    public R<AdminJuryCaseVO> detail(@PathVariable("id") Long id) {
        return R.ok(juryService.adminDetail(id));
    }

    /** 终审裁决 */
    @PostMapping("/jury/{id}/ruling")
    public R<Void> ruling(@PathVariable("id") Long id, @RequestBody @Valid AdminRulingDTO dto) {
        juryService.ruling(SecurityUtils.currentUserId(), id, dto);
        return R.ok(null, "裁决已提交");
    }

    /** AI 起草裁决理由（按给定方向，返回正文供编辑确认；AI 未配置或失败时业务异常提示） */
    @PostMapping("/jury/{id}/ruling-draft")
    public R<String> rulingDraft(@PathVariable("id") Long id, @RequestBody @Valid JuryDraftDTO dto) {
        return R.ok(juryService.draftRuling(id, dto.getResult()), "草稿已生成");
    }

    /**
     * 支付宝退款核验（功能完整性）：按售后单查退款是否真实到账。
     *
     * @param refundId 售后单 ID（退款幂等号 = BRIXRF + refundId）
     * @return refunded / refundAmount / tradeNo
     */
    @GetMapping("/jury/alipay-refund-query")
    public R<java.util.Map<String, Object>> alipayRefundQuery(@RequestParam("orderId") Long orderId,
                                                              @RequestParam("refundId") Long refundId) {
        return R.ok(payAlipayService.refundQuery(orderId, "BRIXRF" + refundId));
    }
}
