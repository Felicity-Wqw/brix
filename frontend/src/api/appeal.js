import request from '@/utils/request'

/**
 * 商品申诉接口集合。
 *
 * 范围：仅受理「平台对商品的处置」争议（审核拒绝 / 被处置下架），
 * 卖家主动下架与交易纠纷不在其列。AI 复核建议仅供审核员参考。
 */

/** 提交申诉（卖家本人） */
export function createAppeal(data) {
  return request.post('/appeals', data)
}

/** 我的申诉分页 */
export function fetchMyAppeals(params) {
  return request.get('/appeals/mine', { params })
}

/** 商品最新申诉（详情页展示申诉进度） */
export function fetchItemAppeal(itemId) {
  return request.get(`/appeals/item/${itemId}`)
}

/* ---------------- 管理端（REVIEWER） ---------------- */

/** 申诉分页（status: PENDING / MAINTAINED / OVERTURNED，可空） */
export function fetchAppeals(params) {
  return request.get('/admin/appeals', { params })
}

/** 处理申诉（action: MAINTAINED 维持 / OVERTURNED 改判恢复在售） */
export function handleAppeal(id, data) {
  return request.post(`/admin/appeals/${id}/handle`, data)
}

/** AI 复核建议（对照原审核留痕 + 原文 + 申诉理由） */
export function reviewAppeal(id) {
  return request.post(`/admin/appeals/${id}/ai-review`)
}
