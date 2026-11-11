package com.brix.campus.controller.admin;

import com.brix.campus.common.R;
import com.brix.campus.model.JuryCase;
import com.brix.campus.model.enums.JuryStageEnum;
import com.brix.campus.model.vo.AdminDashboardVO;
import com.brix.campus.model.vo.AdminScreenVO;
import com.brix.campus.service.AdminDashboardService;
import com.brix.campus.service.ItemService;
import com.brix.campus.service.ReportService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.brix.campus.mapper.JuryCaseMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台数据看板与待办角标。
 *
 * <p>路径刻意分成两段，对应两套权限：</p>
 * <ul>
 *   <li>{@code /admin/dashboard/overview} → 仅 OPERATOR；</li>
 *   <li>{@code /admin/badges} → 两个后台角色都能取，但按角色只返回自己关心的数字
 *       （审核员看待审商品/待处理举报，运营看待审商品 + 今日成交）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin")
@Validated
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;
    private final ItemService itemService;
    private final ReportService reportService;
    private final JuryCaseMapper juryCaseMapper;

    public AdminDashboardController(AdminDashboardService dashboardService,
                                    ItemService itemService,
                                    ReportService reportService,
                                    JuryCaseMapper juryCaseMapper) {
        this.dashboardService = dashboardService;
        this.itemService = itemService;
        this.reportService = reportService;
        this.juryCaseMapper = juryCaseMapper;
    }

    /**
     * 看板全量数据。
     *
     * @param days 统计周期（天），默认 7；前端切换 7 / 30 天
     * @return 看板
     */
    @GetMapping("/dashboard/overview")
    public R<AdminDashboardVO> overview(@RequestParam(value = "days", defaultValue = "7") int days) {
        return R.ok(dashboardService.load(days));
    }

    /**
     * AI 运营数据分析报告（周 / 月 / 季度三周期复用）。
     *
     * @param days 统计周期：7=周报、30=月报、90=季度汇报
     * @return 报告正文
     */
    @GetMapping("/dashboard/ai-report")
    public R<String> aiReport(@RequestParam(value = "days", defaultValue = "7") int days) {
        return R.ok(dashboardService.aiReport(days), "报告已生成");
    }

    /**
     * 数据可视化大屏聚合数据（30 秒轮询；口径与 overview 一致 + AI 统计与实时动态）。
     *
     * @return 大屏全量数据
     */
    @GetMapping("/dashboard/screen")
    public R<AdminScreenVO> screen() {
        return R.ok(dashboardService.screen());
    }

    /**
     * 侧栏待办角标。
     *
     * @return pendingItems 待审核商品数、pendingReports 待处理举报数、
     *         pendingJury 待人工终审的小法庭案件数（PENDING_RULING）
     */
    @GetMapping("/badges")
    public R<Map<String, Long>> badges() {
        Map<String, Long> data = new LinkedHashMap<>();
        data.put("pendingItems", itemService.countPending());
        data.put("pendingReports", reportService.countPending());
        Long pendingJury = juryCaseMapper.selectCount(Wrappers.<JuryCase>lambdaQuery()
                .eq(JuryCase::getStatus, JuryStageEnum.PENDING_RULING.getCode()));
        data.put("pendingJury", pendingJury == null ? 0L : pendingJury);
        return R.ok(data);
    }
}
