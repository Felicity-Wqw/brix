package com.brix.campus.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.model.dto.CommentCreateDTO;
import com.brix.campus.model.query.CommentQuery;
import com.brix.campus.model.vo.CommentVO;

/**
 * 留言服务（D01-D03）：仅一级留言 + 其对一级留言的单层回复。
 */
public interface CommentService {

    /**
     * 商品留言分页：一级留言按时间倒序，每条挂其回复（按时间升序）。
     *
     * @param itemId   商品 ID
     * @param query    分页条件
     * @param viewerId 当前登录用户（可空，用于 canDelete）
     * @return 分页结果
     */
    IPage<CommentVO> pageByItem(Long itemId, CommentQuery query, Long viewerId);

    /**
     * 发表留言 / 回复一级留言。
     *
     * @param userId 留言人 ID
     * @param itemId 商品 ID
     * @param dto    入参
     * @return 新留言
     */
    CommentVO create(Long userId, Long itemId, CommentCreateDTO dto);

    /**
     * 删除自己的留言（删除一级留言时其回复一并逻辑删除）。
     *
     * @param userId    用户 ID
     * @param commentId 留言 ID
     */
    void delete(Long userId, Long commentId);

    /**
     * 我收到的未读留言：统计 + 最新未读留言所在商品（导航红点用）。
     *
     * @param sellerId 卖家 ID（我发布的商品）
     * @return {count, latestItemId}；count 为 0 时 latestItemId 为 null
     */
    java.util.Map<String, Object> unreadSummary(Long sellerId);
}
