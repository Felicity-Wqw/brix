import request from '@/utils/request'

/**
 * 管理后台接口集合。
 *
 * 约定：
 *   · 所有路径都挂在 /admin 下，权限由后端 SecurityConfig 的 URL 规则强制；
 *   · 前端不做权限判断（除了隐藏菜单），任何越权请求都会被 403 挡回。
 */

/** 后台登录（服务端二次校验角色，非 REVIEWER/OPERATOR 一律拒绝） */
export function adminLogin(data) {
  return request.post('/admin/login', data)
}

/** 侧栏待办角标：待审商品 / 待处理举报 */
export function fetchAdminBadges() {
  return request.get('/admin/badges')
}

/** 数据看板（days = 7 / 30） */
export function fetchDashboard(days = 7) {
  return request.get('/admin/dashboard/overview', { params: { days } })
}

/** AI 运营数据分析报告（days: 7=周报 / 30=月报 / 90=季度汇报） */
export function fetchAiReport(days = 7) {
  return request.get('/admin/dashboard/ai-report', { params: { days } })
}

/** 商品审核队列 / 检索 */
export function fetchAdminItems(params) {
  return request.get('/admin/items', { params })
}

/** 商品详情（审核上下文，不计浏览量） */
export function fetchAdminItemDetail(id) {
  return request.get(`/admin/items/${id}`)
}

/** 人工审核处置：PASS / REJECT / OFFLINE */
export function auditItem(id, data) {
  return request.post(`/admin/items/${id}/audit`, data)
}

/** 审核日志检索（完整留痕：商品快照 + 卖家快照 + 两道闸结果） */
export function fetchAuditLogs(params) {
  return request.get('/admin/audit-logs', { params })
}

/** 某件商品的完整审核轨迹（时间倒序） */
export function fetchItemAuditTrail(itemId) {
  return request.get(`/admin/audit-logs/by-item/${itemId}`)
}

/** 举报队列 */
export function fetchAdminReports(params) {
  return request.get('/admin/reports', { params })
}

/** 处理举报：OFFLINE 成立 / REJECT 不成立 */
export function handleReport(id, data) {
  return request.post(`/admin/reports/${id}/handle`, data)
}

/** 用户列表 */
export function fetchAdminUsers(params) {
  return request.get('/admin/users', { params })
}

/** 封禁 / 解封用户 */
export function updateUserStatus(id, data) {
  return request.put(`/admin/users/${id}/status`, data)
}

/** 全平台订单列表（只读） */
export function fetchAdminOrders(params) {
  return request.get('/admin/orders', { params })
}

/** ==================== 平台公告（运营端） ==================== */

/** 公告全量列表（含下架） */
export function fetchAdminAnnouncements() {
  return request.get('/admin/announcements')
}

/** 新增公告 */
export function createAnnouncement(data) {
  return request.post('/admin/announcements', data)
}

/** 修改公告 */
export function updateAnnouncement(id, data) {
  return request.put(`/admin/announcements/${id}`, data)
}

/** 删除公告 */
export function deleteAnnouncement(id) {
  return request.delete(`/admin/announcements/${id}`)
}

/** 用户侧公告（首页轮播，游客可读） */
export function fetchAnnouncements() {
  return request.get('/announcements')
}

/** 数据可视化大屏聚合数据（30s 轮询） */
export function fetchScreen() {
  return request.get('/admin/dashboard/screen')
}
