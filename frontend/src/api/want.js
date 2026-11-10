import request from '@/utils/request'

/** 发布求购帖 */
export function publishWant(data) {
  return request.post('/wants', data)
}

/** 求购列表（status: OPEN / CLOSED） */
export function fetchWants(params) {
  return request.get('/wants', { params })
}

/** 关闭自己的求购帖 */
export function closeWant(id) {
  return request.delete(`/wants/${id}`)
}
