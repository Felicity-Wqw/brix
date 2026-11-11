import request from '@/utils/request'

/**
 * 小法庭接口集合。
 *
 * 陪审完全自愿、无推送；用户端一律匿名（当事人 A/B），
 * 管理端接口挂在 /admin 下（REVIEWER），可见真实身份与票数统计。
 */

/** 进行中的案件列表（举证期 + 陪审期） */
export function fetchJuryCases(params) {
  return request.get('/jury/cases', { params })
}

/** 案件匿名详情 */
export function fetchJuryCaseDetail(id) {
  return request.get(`/jury/cases/${id}`)
}

/** 陪审投票（vote: BUYER 支持买家 / SELLER 支持卖家） */
export function voteJuryCase(id, data) {
  return request.post(`/jury/cases/${id}/vote`, data)
}

/** 平台 AI 预审参考（幂等：已生成/AI 未配置/举证期中返回 false，前端静默处理） */
export function generateJuryAiPreview(id) {
  return request.post(`/jury/cases/${id}/ai-preview`)
}

/* ---------------- 管理端（REVIEWER） ---------------- */

/** 待裁决案件分页 */
export function fetchPendingJuryCases(params) {
  return request.get('/admin/refunds/jury/pending', { params })
}

/** 案件详情（真实身份 + 票数统计） */
export function fetchAdminJuryCaseDetail(id) {
  return request.get(`/admin/refunds/jury/${id}`)
}

/** 终审裁决（result: BUYER 支持买家退款 / SELLER 支持卖家关闭） */
export function ruleJuryCase(id, data) {
  return request.post(`/admin/refunds/jury/${id}/ruling`, data)
}

/** AI 起草裁决理由（按给定方向返回正文，供编辑确认；AI 未配置时后端报错提示） */
export function draftJuryRuling(id, result) {
  return request.post(`/admin/refunds/jury/${id}/ruling-draft`, { result })
}
