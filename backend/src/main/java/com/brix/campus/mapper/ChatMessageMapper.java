package com.brix.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brix.campus.model.ChatMessage;
import com.brix.campus.model.vo.ConversationVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 私信 Mapper：会话列表按「对方用户 ID」聚合，必须写 XML（不建会话表）。
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 会话列表（按最后消息时间倒序）。
     *
     * @param uid 当前用户 ID
     * @return 会话列表
     */
    List<ConversationVO> selectConversations(@Param("uid") Long uid);

    /**
     * 全局未读数。
     *
     * @param uid 当前用户 ID
     * @return 未读条数
     */
    Long countUnread(@Param("uid") Long uid);

    /**
     * 把某会话中发给当前用户的消息置为已读。
     *
     * @param uid    当前用户 ID
     * @param peerId 对话方 ID
     * @return 影响行数
     */
    int markRead(@Param("uid") Long uid, @Param("peerId") Long peerId);
}
