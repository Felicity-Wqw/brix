package com.brix.campus.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.model.dto.ItemAuditDTO;
import com.brix.campus.model.query.ItemQuery;
import com.brix.campus.model.vo.ItemDetailVO;
import com.brix.campus.model.vo.ItemVO;
import com.brix.campus.security.SecurityUtils;
import com.brix.campus.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台商品审核接口（仅 REVIEWER，权限由 SecurityConfig 强制）。
 *
 * <p>与前台 {@code /items} 的本质区别：前台只看 {@code ON_SALE}，
 * 后台要看全部状态——审核队列（PENDING）、误杀纠正（REJECTED）、
 * 举报后的强制下架（ON_SALE → OFF_SHELF）都发生在这里。</p>
 */
@RestController
@RequestMapping("/admin/items")
@Validated
public class AdminItemController {

    private final ItemService itemService;

    public AdminItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * 审核队列 / 商品检索。
     *
     * @param query 条件：status、riskLevel、keyword、categoryId、schoolId、分页
     * @return 分页结果（含风险级别与判定说明）
     */
    @GetMapping
    public R<IPage<ItemVO>> list(@Valid ItemQuery query) {
        return R.ok(itemService.pageForAdmin(query));
    }

    /**
     * 商品详情（审核上下文：描述全文 + 图片，不计浏览量）。
     *
     * @param id 商品 ID
     * @return 详情
     */
    @GetMapping("/{id}")
    public R<ItemDetailVO> detail(@PathVariable("id") Long id) {
        return R.ok(itemService.detailForAdmin(id));
    }

    /**
     * 人工审核处置：PASS / REJECT / OFFLINE。
     *
     * @param id  商品 ID
     * @param dto 动作与说明
     * @return 处置后的商品
     */
    @PostMapping("/{id}/audit")
    public R<ItemVO> audit(@PathVariable("id") Long id, @RequestBody @Valid ItemAuditDTO dto) {
        Long reviewerId = SecurityUtils.currentUserId();
        itemService.audit(reviewerId, id, dto);
        return R.ok(itemService.toVO(itemService.getEntity(id)), "处置完成");
    }
}
