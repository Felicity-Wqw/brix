import request from '@/utils/request'

/**
 * 留言接口（D01-D03）。
 */

/** 商品留言列表（一级 + replies） */
export function fetchComments(itemId, params) {
  return request.get(`/items/${itemId}/comments`, { params })
}

/** 发表留言 / 回复一级留言 */
export function createComment(itemId, data) {
  return request.post(`/items/${itemId}/comments`, data)
}

/** 删除自己的留言 */
export function deleteComment(id) {
  return request.delete(`/comments/${id}`)
}

/** 我收到的未读留言摘要（导航红点）：{ count, latestItemId } */
export function fetchCommentUnreadSummary() {
  return request.get('/comments/unread-summary')
}
