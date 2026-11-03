package com.brix.campus.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.model.dto.ChatSendDTO;
import com.brix.campus.model.query.ChatQuery;
import com.brix.campus.model.vo.ChatMessageVO;
import com.brix.campus.model.vo.ConversationVO;

import java.util.List;

/**
 * 私信服务（I01-I04）：HTTP 兜底通道与 WebSocket 共用同一发送方法。
 */
public interface ChatService {

    /**
     * 发送私信：先落库再实时推送（离线仅落库）。
     *
     * @param fromUserId 发送者 ID
     * @param dto        入参
     * @return 落库后的消息
     */
    ChatMessageVO send(Long fromUserId, ChatSendDTO dto);

    /**
     * 会话列表：按对方用户聚合，按最后消息时间倒序。
     *
     * @param userId 当前用户 ID
     * @return 会话列表
     */
    List<ConversationVO> conversations(Long userId);

    /**
     * 历史消息：双向、按 id 升序分页。
     *
     * @param userId 当前用户 ID
     * @param query  查询条件（peerId 必填）
     * @return 分页结果
     */
    IPage<ChatMessageVO> messages(Long userId, ChatQuery query);

    /**
     * 全局未读数。
     *
     * @param userId 当前用户 ID
     * @return 未读条数
     */
    long unreadCount(Long userId);

    /**
     * 打开会话：把对方发来的消息置为已读，返回清零后的全局未读数。
     *
     * @param userId 当前用户 ID
     * @param peerId 对话方 ID
     * @return 清零后的全局未读数
     */
    long markRead(Long userId, Long peerId);
}
