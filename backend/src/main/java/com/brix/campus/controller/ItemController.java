package com.brix.campus.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.model.dto.ItemSaveDTO;
import com.brix.campus.model.dto.ItemStatusDTO;
import com.brix.campus.model.query.ItemQuery;
import com.brix.campus.model.vo.ItemDetailVO;
import com.brix.campus.model.vo.ItemVO;
import com.brix.campus.security.SecurityUtils;
import com.brix.campus.service.ItemService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品接口（B01-B08）。
 */
@RestController
@RequestMapping("/items")
@Validated
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * 发布商品（合规审核通过后直接上架）。
     *
     * @param dto 入参
     * @return 新商品
     */
    @PostMapping
    public R<ItemVO> create(@RequestBody @Valid ItemSaveDTO dto) {
        return R.ok(itemService.create(SecurityUtils.currentUserId(), dto), "发布成功");
    }

    /**
     * 前台商品列表（仅 ON_SALE，支持筛选与排序）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping
    public R<IPage<ItemVO>> list(@Valid ItemQuery query) {
        return R.ok(itemService.pageForFront(query));
    }

    /**
     * 我的商品（全部状态）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/mine")
    public R<IPage<ItemVO>> mine(@Valid ItemQuery query) {
        return R.ok(itemService.pageMine(SecurityUtils.currentUserId(), query));
    }

    /**
     * 商品详情（浏览量 +1，同一访问者 10 分钟内不重复计数）。
     *
     * @param id      商品 ID
     * @param request 用于取游客 IP 作为去重标识
     * @return 详情
     */
    @GetMapping("/{id}")
    public R<ItemDetailVO> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        Long viewerId = SecurityUtils.currentUserIdOrNull();
        String viewerKey = viewerId != null ? "u:" + viewerId : "ip:" + clientIp(request);
        return R.ok(itemService.detail(id, viewerId, viewerKey));
    }

    /**
     * 编辑商品（编辑后回到待审核）。
     *
     * @param id  商品 ID
     * @param dto 入参
     * @return 空
     */
    @PutMapping("/{id}")
    public R<ItemVO> update(@PathVariable("id") Long id, @RequestBody @Valid ItemSaveDTO dto) {
        ItemVO item = itemService.update(SecurityUtils.currentUserId(), id, dto);
        return R.ok(item, "修改成功");
    }

    /**
     * 删除商品（逻辑删除）。
     *
     * @param id 商品 ID
     * @return 空
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        itemService.delete(SecurityUtils.currentUserId(), id);
        return R.ok(null, "已删除");
    }

    /**
     * 上架 / 下架。
     *
     * @param id  商品 ID
     * @param dto 入参
     * @return 更新后的商品
     */
    @PatchMapping("/{id}/status")
    public R<ItemVO> updateStatus(@PathVariable("id") Long id, @RequestBody @Valid ItemStatusDTO dto) {
        return R.ok(itemService.updateStatus(SecurityUtils.currentUserId(), id, dto), "操作成功");
    }

    /**
     * 同类推荐：同子类优先，同父类其他子类补足，按浏览量倒序（无需登录）。
     *
     * @param id 商品 ID
     * @return 推荐商品列表（最多 4 件）
     */
    @GetMapping("/{id}/similar")
    public R<List<ItemVO>> similar(@PathVariable("id") Long id) {
        return R.ok(itemService.listSimilar(id, 4));
    }

    /**
     * 取客户端 IP（兼容代理头）。
     *
     * @param request 请求
     * @return IP
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
