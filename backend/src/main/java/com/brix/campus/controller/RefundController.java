package com.brix.campus.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.model.dto.RefundApplyDTO;
import com.brix.campus.model.dto.RefundHandleDTO;
import com.brix.campus.model.dto.RefundLogisticsDTO;
import com.brix.campus.model.dto.JuryEvidenceDTO;
import com.brix.campus.model.vo.JuryCaseVO;
import com.brix.campus.model.vo.RefundVO;
import com.brix.campus.security.SecurityUtils;
import com.brix.campus.service.JuryService;
import com.brix.campus.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 售后接口（买家申请/撤销/重提/寄回，卖家处理/确认收货，双方申请介入与举证）。
 *
 * <p>归属校验在 Service 层（数据归属层），URL 层仅需登录。</p>
 */
@RestController
@RequestMapping("/refunds")
public class RefundController {

    private final RefundService refundService;
    private final JuryService juryService;

    public RefundController(RefundService refundService, JuryService juryService) {
        this.refundService = refundService;
        this.juryService = juryService;
    }

    /** 买家申请售后 */
    @PostMapping
    public R<RefundVO> apply(@RequestBody @Valid RefundApplyDTO dto) {
        return R.ok(refundService.apply(SecurityUtils.currentUserId(), dto), "售后申请已提交");
    }

    /** 买家撤销售后 */
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable("id") Long id) {
        refundService.cancel(SecurityUtils.currentUserId(), id);
        return R.ok(null, "已撤销售后申请");
    }

    /** 买家修改重提（被拒绝后 5 日内） */
    @PostMapping("/{id}/revise")
    public R<RefundVO> revise(@PathVariable("id") Long id, @RequestBody @Valid RefundApplyDTO dto) {
        return R.ok(refundService.revise(SecurityUtils.currentUserId(), id, dto), "已重新提交");
    }

    /** 卖家处理（同意/拒绝） */
    @PostMapping("/{id}/handle")
    public R<RefundVO> handle(@PathVariable("id") Long id, @RequestBody @Valid RefundHandleDTO dto) {
        return R.ok(refundService.handle(SecurityUtils.currentUserId(), id, dto), "处理完成");
    }

    /** 买家填写退货运单号 */
    @PostMapping("/{id}/logistics")
    public R<RefundVO> logistics(@PathVariable("id") Long id,
                                 @RequestBody @Valid RefundLogisticsDTO dto) {
        return R.ok(refundService.submitLogistics(SecurityUtils.currentUserId(), id, dto), "运单号已登记");
    }

    /** 卖家确认收到退货 */
    @PostMapping("/{id}/receive")
    public R<RefundVO> receive(@PathVariable("id") Long id) {
        return R.ok(refundService.confirmReceive(SecurityUtils.currentUserId(), id), "退款已完成");
    }

    /** 申请平台介入（小法庭） */
    @PostMapping("/{id}/jury")
    public R<JuryCaseVO> initiateJury(@PathVariable("id") Long id,
                                      @RequestBody @Valid JuryEvidenceDTO dto) {
        return R.ok(juryService.initiate(SecurityUtils.currentUserId(), id, dto.getContent()),
                "已进入平台介入流程");
    }

    /** 当事人提交小法庭证据 */
    @PostMapping("/{id}/evidence")
    public R<Void> submitEvidence(@PathVariable("id") Long id, @RequestBody @Valid JuryEvidenceDTO dto) {
        RefundVO refund = refundService.detail(SecurityUtils.currentUserId(), id);
        if (refund.getJuryCaseId() == null) {
            return R.fail("该售后未进入小法庭");
        }
        juryService.submitEvidence(SecurityUtils.currentUserId(), refund.getJuryCaseId(), dto);
        return R.ok(null, "证据已提交");
    }

    /** 我申请的售后（买家） */
    @GetMapping("/mine")
    public R<IPage<RefundVO>> mine(@RequestParam(defaultValue = "1") long page,
                                   @RequestParam(defaultValue = "10") long size) {
        return R.ok(refundService.pageMyApplied(SecurityUtils.currentUserId(), page, size));
    }

    /** 我收到的售后（卖家） */
    @GetMapping("/received")
    public R<IPage<RefundVO>> received(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "10") long size) {
        return R.ok(refundService.pageMyReceived(SecurityUtils.currentUserId(), page, size));
    }

    /** 售后详情（买卖双方本人） */
    @GetMapping("/{id}")
    public R<RefundVO> detail(@PathVariable("id") Long id) {
        return R.ok(refundService.detail(SecurityUtils.currentUserId(), id));
    }
}
