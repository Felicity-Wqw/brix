package com.brix.campus.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.model.dto.JuryVoteDTO;
import com.brix.campus.model.vo.JuryCaseVO;
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
 * 小法庭接口（用户端，匿名视角；陪审完全自愿，无任何推送）。
 */
@RestController
@RequestMapping("/jury")
public class JuryController {

    private final JuryService juryService;

    public JuryController(JuryService juryService) {
        this.juryService = juryService;
    }

    /** 进行中的案件列表（陪审期 + 举证期，自愿围观） */
    @GetMapping("/cases")
    public R<IPage<JuryCaseVO>> cases(@RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "10") long size) {
        return R.ok(juryService.pageOpenCases(page, size));
    }

    /** 案件匿名详情 */
    @GetMapping("/cases/{id}")
    public R<JuryCaseVO> detail(@PathVariable("id") Long id) {
        return R.ok(juryService.detailForUser(id, SecurityUtils.currentUserId()));
    }

    /** 陪审投票（非当事人、每案一票） */
    @PostMapping("/cases/{id}/vote")
    public R<Void> vote(@PathVariable("id") Long id, @RequestBody @Valid JuryVoteDTO dto) {
        juryService.vote(SecurityUtils.currentUserId(), id, dto);
        return R.ok(null, "投票成功");
    }

    /**
     * 平台 AI 预审参考（幂等：已生成过直接返回 false；AI 不可用静默返回 false）。
     * 举证期结束进入陪审期后由定时任务自动生成，此接口供当事人/围观用户手动补触发。
     */
    @PostMapping("/cases/{id}/ai-preview")
    public R<Boolean> aiPreview(@PathVariable("id") Long id) {
        boolean generated = juryService.generateAiPreview(id);
        return R.ok(generated, generated ? "预审参考已生成" : "暂未生成（可能已存在、AI 未配置或案件尚在举证期）");
    }
}
