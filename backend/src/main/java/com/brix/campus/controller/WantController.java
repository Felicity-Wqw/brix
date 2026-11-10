package com.brix.campus.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.security.SecurityUtils;
import com.brix.campus.model.dto.WantSaveDTO;
import com.brix.campus.model.vo.WantVO;
import com.brix.campus.service.WantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 求购墙。
 *
 * <p>列表浏览允许游客（与商品列表一致）；发布与关闭需登录。
 * 卖家接单走现有聊天（/messages?peerId=发帖人），成交后发帖人关闭帖子。</p>
 */
@RestController
@RequestMapping("/wants")
@Validated
public class WantController {

    private final WantService wantService;

    public WantController(WantService wantService) {
        this.wantService = wantService;
    }

    /** 发布求购帖（返回新建对象，便于前端直接定位/回填） */
    @PostMapping
    public R<WantVO> publish(@RequestBody @Valid WantSaveDTO dto) {
        return R.ok(wantService.publish(SecurityUtils.currentUserId(), dto), "求购已发布");
    }

    /**
     * 求购列表。
     *
     * @param status OPEN（默认）/ CLOSED
     */
    @GetMapping
    public R<IPage<WantVO>> list(
            @RequestParam(value = "status", defaultValue = "OPEN") String status,
            @RequestParam(value = "currentPage", defaultValue = "1") @Min(1) long currentPage,
            @RequestParam(value = "pageSize", defaultValue = "12") @Min(1) @Max(50) long pageSize) {
        Long viewerId = SecurityUtils.currentUserIdOrNull();
        return R.ok(wantService.page(status, currentPage, pageSize, viewerId));
    }

    /** 发帖人关闭求购帖 */
    @DeleteMapping("/{id}")
    public R<Void> close(@PathVariable("id") Long id) {
        wantService.close(SecurityUtils.currentUserId(), id);
        return R.ok(null, "求购已结束");
    }
}
