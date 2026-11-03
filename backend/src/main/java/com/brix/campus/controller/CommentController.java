package com.brix.campus.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.common.R;
import com.brix.campus.model.dto.CommentCreateDTO;
import com.brix.campus.model.query.CommentQuery;
import com.brix.campus.model.vo.CommentVO;
import com.brix.campus.security.SecurityUtils;
import com.brix.campus.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 留言接口（D01-D03）。
 */
@RestController
@Validated
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * 商品留言列表（无需登录）。
     *
     * @param id    商品 ID
     * @param query 分页条件
     * @return 分页结果
     */
    @GetMapping("/items/{id}/comments")
    public R<IPage<CommentVO>> list(@PathVariable("id") Long id, @Valid CommentQuery query) {
        return R.ok(commentService.pageByItem(id, query, SecurityUtils.currentUserIdOrNull()));
    }

    /**
     * 发表留言 / 回复一级留言。
     *
     * @param id  商品 ID
     * @param dto 入参
     * @return 新留言
     */
    @PostMapping("/items/{id}/comments")
    public R<CommentVO> create(@PathVariable("id") Long id, @RequestBody @Valid CommentCreateDTO dto) {
        return R.ok(commentService.create(SecurityUtils.currentUserId(), id, dto), "留言成功");
    }

    /**
     * 我收到的未读留言摘要（导航红点用）：count + 最新未读留言所在商品。
     *
     * @return {count, latestItemId}
     */
    @GetMapping("/comments/unread-summary")
    public R<java.util.Map<String, Object>> unreadSummary() {
        return R.ok(commentService.unreadSummary(SecurityUtils.currentUserId()));
    }

    /**
     * 删除自己的留言。
     *
     * @param id 留言 ID
     * @return 空
     */
    @DeleteMapping("/comments/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        commentService.delete(SecurityUtils.currentUserId(), id);
        return R.ok(null, "已删除");
    }
}
