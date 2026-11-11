package com.brix.campus.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.model.dto.AppealCreateDTO;
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
 * 商品申诉接口（卖家本人）。
 */
@RestController
@RequestMapping("/appeals")
public class AppealController {

    private final AppealService appealService;

    public AppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    /** 提交申诉（卖家本人；商品被拒或被处置下架） */
    @PostMapping
    public R<AppealVO> create(@RequestBody @Valid AppealCreateDTO dto) {
        return R.ok(appealService.create(SecurityUtils.currentUserId(), dto), "申诉已提交，请等待审核员处理");
    }

    /** 我的申诉分页 */
    @GetMapping("/mine")
    public R<IPage<AppealVO>> mine(@RequestParam(defaultValue = "1") long page,
                                   @RequestParam(defaultValue = "10") long size) {
        return R.ok(appealService.pageMine(SecurityUtils.currentUserId(), page, size));
    }

    /** 商品最新申诉（详情页展示申诉进度；无申诉返回 null） */
    @GetMapping("/item/{itemId}")
    public R<AppealVO> latestByItem(@PathVariable Long itemId) {
        return R.ok(appealService.latestByItem(itemId));
    }
}
