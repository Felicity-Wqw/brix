package com.brix.campus.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.model.dto.ReportHandleDTO;
import com.brix.campus.model.query.ReportQuery;
import com.brix.campus.model.vo.ReportVO;
import com.brix.campus.security.SecurityUtils;
import com.brix.campus.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台举报处理接口（仅 REVIEWER）。
 *
 * <p>举报与审核共用同一份「内容治理」权限：谁能审商品，谁才能裁决举报。
 * 运营角色即便能看订单也不能处理举报——避免「为了 GMV 放过违规商品」的利益冲突。</p>
 */
@RestController
@RequestMapping("/admin/reports")
@Validated
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 举报队列。
     *
     * @param query 状态 / 原因 / 分页
     * @return 分页结果
     */
    @GetMapping
    public R<IPage<ReportVO>> list(@Valid ReportQuery query) {
        return R.ok(reportService.pageForAdmin(query));
    }

    /**
     * 处理举报。
     *
     * @param id  举报 ID
     * @param dto 动作（OFFLINE 成立 / REJECT 不成立）与备注
     * @return 操作结果
     */
    @PostMapping("/{id}/handle")
    public R<Void> handle(@PathVariable("id") Long id, @RequestBody @Valid ReportHandleDTO dto) {
        reportService.handle(SecurityUtils.currentUserId(), id, dto);
        return R.ok(null, "举报已处理");
    }
}
