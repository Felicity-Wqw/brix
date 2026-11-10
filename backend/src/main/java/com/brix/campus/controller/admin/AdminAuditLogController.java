package com.brix.campus.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.model.query.ItemAuditLogQuery;
import com.brix.campus.model.vo.ItemAuditLogVO;
import com.brix.campus.service.ItemAuditLogService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台审核日志接口（仅 REVIEWER，权限由 SecurityConfig 强制）。
 *
 * <p>这是「审核留痕」的出口：回答四个问题——谁发的、发了什么、两道闸各自判了什么、什么时候审的。
 * 与 {@code /admin/items} 的区别在于<b>视角</b>：{@code /admin/items} 看的是商品当前状态
 * （在售 / 待审 / 驳回），这里看的是<b>历史动作</b>，商品的当前状态已经被后来的编辑覆盖也不影响这里的记录。</p>
 *
 * <p>放在独立的 {@code /admin/audit-logs} 路径下而不是塞进 {@code /admin/items/**}，
 * 是为了避开 {@code GET /admin/items/{id}} 的路径变量冲突（{@code {id}} 是 Long，
 * 拼成 {@code /admin/items/audit-logs} 会先命中它再做类型转换而报错）。</p>
 */
@RestController
@RequestMapping("/admin/audit-logs")
@Validated
public class AdminAuditLogController {

    private final ItemAuditLogService auditLogService;

    public AdminAuditLogController(ItemAuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 审核日志分页检索。
     *
     * @param query 条件：itemId、sellerId、riskLevel、gate1Result、gate2Result、keyword、handled、日期区间、分页
     * @return 分页结果
     */
    @GetMapping
    public R<IPage<ItemAuditLogVO>> list(@Valid ItemAuditLogQuery query) {
        return R.ok(auditLogService.page(query));
    }

    /**
     * 某件商品的完整审核轨迹（时间倒序）。
     *
     * @param itemId 商品 ID
     * @return 日志列表
     */
    @GetMapping("/by-item/{itemId}")
    public R<List<ItemAuditLogVO>> listByItem(@PathVariable("itemId") Long itemId) {
        return R.ok(auditLogService.listByItem(itemId));
    }
}
