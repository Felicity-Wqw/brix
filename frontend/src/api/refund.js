import request from '@/utils/request'

/**
 * 售后退款接口集合。
 *
 * 权限：全部需登录，归属校验在后端 Service 层（买家/卖家各自操作自己的单子）。
 */

/** 买家申请售后（type: REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款） */
export function applyRefund(data) {
  return request.post('/refunds', data)
}

/** 买家撤销售后（卖家处理中可撤） */
export function cancelRefund(id) {
  return request.post(`/refunds/${id}/cancel`)
}

/** 买家修改重提（被拒绝后 5 日内） */
export function reviseRefund(id, data) {
  return request.post(`/refunds/${id}/revise`, data)
}

/** 卖家处理（action: AGREE 同意 / REJECT 拒绝） */
export function handleRefund(id, data) {
  return request.post(`/refunds/${id}/handle`, data)
}

/** 买家填写退货运单号 */
export function submitRefundLogistics(id, data) {
  return request.post(`/refunds/${id}/logistics`, data)
}

/** 卖家确认收到退货 */
export function confirmRefundReceive(id) {
  return request.post(`/refunds/${id}/receive`)
}

/** 申请平台介入（小法庭），data: { content: 纠纷陈述 } */
export function initiateJury(id, data) {
  return request.post(`/refunds/${id}/jury`, data)
}

/** 小法庭提交证据（举证期内，每方最多 5 条） */
export function submitJuryEvidence(id, data) {
  return request.post(`/refunds/${id}/evidence`, data)
}

/** 我申请的售后（买家视角） */
export function fetchMyRefunds(params) {
  return request.get('/refunds/mine', { params })
}

/** 我收到的售后（卖家视角） */
export function fetchReceivedRefunds(params) {
  return request.get('/refunds/received', { params })
}

/** 售后详情 */
export function fetchRefundDetail(id) {
  return request.get(`/refunds/${id}`)
}
