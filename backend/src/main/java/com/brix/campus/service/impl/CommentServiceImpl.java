package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brix.campus.common.BusinessException;
import com.brix.campus.mapper.ItemCommentMapper;
import com.brix.campus.mapper.ItemMapper;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.Item;
import com.brix.campus.model.ItemComment;
import com.brix.campus.model.User;
import com.brix.campus.model.dto.CommentCreateDTO;
import com.brix.campus.model.query.CommentQuery;
import com.brix.campus.model.vo.CommentVO;
import com.brix.campus.service.CommentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 留言服务实现。
 */
@Service
public class CommentServiceImpl implements CommentService {

    private final ItemCommentMapper itemCommentMapper;
    private final ItemMapper itemMapper;
    private final UserMapper userMapper;

    public CommentServiceImpl(ItemCommentMapper itemCommentMapper, ItemMapper itemMapper, UserMapper userMapper) {
        this.itemCommentMapper = itemCommentMapper;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
    }

    @Override
    public IPage<CommentVO> pageByItem(Long itemId, CommentQuery query, Long viewerId) {
        // 卖家查看自己商品的留言 = 已读，顺带清除导航红点
        if (viewerId != null) {
            markRead(viewerId, itemId);
        }
        long current = query.getCurrentPage() == null ? 1L : query.getCurrentPage();
        long size = query.getPageSize() == null ? 10L : query.getPageSize();
        Page<ItemComment> page = new Page<>(current, size);

        IPage<ItemComment> parentPage = itemCommentMapper.selectPage(page, Wrappers.<ItemComment>lambdaQuery()
                .eq(ItemComment::getItemId, itemId)
                .isNull(ItemComment::getParentId)
                .orderByDesc(ItemComment::getCreatedAt)
                .orderByDesc(ItemComment::getId));

        List<ItemComment> parents = parentPage.getRecords();
        List<ItemComment> replies = new ArrayList<>();
        if (!parents.isEmpty()) {
            Set<Long> parentIds = new HashSet<>();
            parents.forEach(parent -> parentIds.add(parent.getId()));
            replies = itemCommentMapper.selectList(Wrappers.<ItemComment>lambdaQuery()
                    .in(ItemComment::getParentId, parentIds)
                    .orderByAsc(ItemComment::getCreatedAt)
                    .orderByAsc(ItemComment::getId));
        }

        Map<Long, String> nicknames = new HashMap<>();
        Map<Long, String> avatars = new HashMap<>();
        loadUsers(parents, replies, nicknames, avatars);

        Map<Long, CommentVO> parentVoMap = new HashMap<>();
        for (ItemComment parent : parents) {
            CommentVO vo = toVO(parent, nicknames, avatars, viewerId);
            parentVoMap.put(parent.getId(), vo);
        }
        for (ItemComment reply : replies) {
            CommentVO parentVo = parentVoMap.get(reply.getParentId());
            if (parentVo != null) {
                parentVo.getReplies().add(toVO(reply, nicknames, avatars, viewerId));
            }
        }

        Page<CommentVO> voPage = new Page<>(parentPage.getCurrent(), parentPage.getSize(), parentPage.getTotal());
        List<CommentVO> voList = new ArrayList<>(parents.size());
        for (ItemComment parent : parents) {
            CommentVO vo = parentVoMap.get(parent.getId());
            if (vo != null) {
                voList.add(vo);
            }
        }
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO create(Long userId, Long itemId, CommentCreateDTO dto) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw BusinessException.notFound("商品不存在");
        }

        Long parentId = dto.getParentId();
        if (parentId != null) {
            ItemComment parent = itemCommentMapper.selectById(parentId);
            if (parent == null || !itemId.equals(parent.getItemId()) || parent.getParentId() != null) {
                throw BusinessException.param("只能回复同一商品的一级留言");
            }
        }

        ItemComment comment = new ItemComment();
        comment.setItemId(itemId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setContent(dto.getContent().trim());
        // 商品主人自己留言不打扰自己；他人留言默认未读，触发卖家导航红点
        comment.setIsRead(userId.equals(item.getSellerId()) ? 1 : 0);
        itemCommentMapper.insert(comment);

        Map<Long, String> nicknames = new HashMap<>();
        Map<Long, String> avatars = new HashMap<>();
        User user = userMapper.selectById(userId);
        if (user != null) {
            nicknames.put(userId, user.getNickname());
            avatars.put(userId, user.getAvatar());
        }
        return toVO(comment, nicknames, avatars, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long commentId) {
        ItemComment comment = itemCommentMapper.selectById(commentId);
        if (comment == null) {
            throw BusinessException.notFound("留言不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权删除该留言");
        }
        // 删除一级留言时，其下回复一并逻辑删除
        if (comment.getParentId() == null) {
            itemCommentMapper.delete(Wrappers.<ItemComment>lambdaQuery()
                    .eq(ItemComment::getParentId, commentId));
        }
        itemCommentMapper.deleteById(commentId);
    }

    /**
     * 批量加载留言人昵称与头像。
     *
     * @param parents   一级留言
     * @param replies   回复
     * @param nicknames 输出：昵称映射
     * @param avatars   输出：头像映射
     */
    private void loadUsers(List<ItemComment> parents, List<ItemComment> replies,
                           Map<Long, String> nicknames, Map<Long, String> avatars) {
        Set<Long> userIds = new HashSet<>();
        parents.stream().map(ItemComment::getUserId).filter(Objects::nonNull).forEach(userIds::add);
        replies.stream().map(ItemComment::getUserId).filter(Objects::nonNull).forEach(userIds::add);
        if (userIds.isEmpty()) {
            return;
        }
        for (User user : userMapper.selectByIds(userIds)) {
            nicknames.put(user.getId(), user.getNickname());
            avatars.put(user.getId(), user.getAvatar());
        }
    }

    /**
     * 实体转 VO。
     *
     * @param comment   留言实体
     * @param nicknames 昵称映射
     * @param avatars   头像映射
     * @param viewerId  当前登录用户
     * @return VO
     */
    private CommentVO toVO(ItemComment comment, Map<Long, String> nicknames,
                           Map<Long, String> avatars, Long viewerId) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setItemId(comment.getItemId());
        vo.setUserId(comment.getUserId());
        vo.setUserNickname(nicknames.get(comment.getUserId()));
        vo.setUserAvatar(avatars.get(comment.getUserId()));
        vo.setContent(comment.getContent());
        vo.setParentId(comment.getParentId());
        vo.setCreatedAt(comment.getCreatedAt());
        vo.setCanDelete(viewerId != null && viewerId.equals(comment.getUserId()));
        return vo;
    }

    /**
     * 卖家查看自己商品时，把该商品上他人留言标记为已读（清除红点）。
     *
     * @param viewerId 查看者
     * @param itemId   商品 ID
     */
    private void markRead(Long viewerId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null || !viewerId.equals(item.getSellerId())) {
            return;
        }
        itemCommentMapper.update(null, Wrappers.<ItemComment>lambdaUpdate()
                .eq(ItemComment::getItemId, itemId)
                .eq(ItemComment::getIsRead, 0)
                .ne(ItemComment::getUserId, viewerId)
                .set(ItemComment::getIsRead, 1));
    }

    @Override
    public Map<String, Object> unreadSummary(Long sellerId) {
        // 我发布的商品 ID 集合
        List<Long> myItemIds = itemMapper.selectList(Wrappers.<Item>lambdaQuery()
                        .eq(Item::getSellerId, sellerId))
                .stream().map(Item::getId).toList();
        Map<String, Object> result = new HashMap<>();
        if (myItemIds.isEmpty()) {
            result.put("count", 0L);
            result.put("latestItemId", null);
            return result;
        }
        List<ItemComment> unread = itemCommentMapper.selectList(Wrappers.<ItemComment>lambdaQuery()
                .in(ItemComment::getItemId, myItemIds)
                .eq(ItemComment::getIsRead, 0)
                .ne(ItemComment::getUserId, sellerId)
                .orderByDesc(ItemComment::getCreatedAt));
        result.put("count", (long) unread.size());
        result.put("latestItemId", unread.isEmpty() ? null : unread.get(0).getItemId());
        return result;
    }
}
