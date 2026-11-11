package com.brix.campus.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.model.dto.AppealHandleDTO;
import com.brix.campus.model.vo.AppealVO;
import com.brix.campus.security.SecurityUtils;
import com.brix.campus.service.AppealService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端申诉处理接口（仅 REVIEWER——申诉针对内容审核/处置结果，属内容治理职责）。
 */
@RestController
@RequestMapping("/admin/appeals")
public class AdminAppealController {

    private final AppealService appealService;

    public AdminAppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    /** 申诉分页（status: PENDING / MAINTAINED / OVERTURNED，可空 = 全部） */
    @GetMapping
    public R<IPage<AppealVO>> page(@RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "1") long page,
                                   @RequestParam(defaultValue = "10") long size) {
        return R.ok(appealService.pageForAdmin(status, page, size));
    }

    /** 处理申诉：MAINTAINED 维持 / OVERTURNED 改判恢复在售 */
    @PostMapping("/{id}/handle")
    public R<Void> handle(@PathVariable Long id, @RequestBody @Valid AppealHandleDTO dto) {
        appealService.handle(SecurityUtils.currentUserId(), id, dto);
        return R.ok(null, "处理完成");
    }

    /** AI 复核建议（对照原审核留痕 + 原文 + 申诉理由；结果落库供参考） */
    @PostMapping("/{id}/ai-review")
    public R<String> aiReview(@PathVariable Long id) {
        return R.ok(appealService.aiReview(id), "AI 复核已生成");
    }
}
