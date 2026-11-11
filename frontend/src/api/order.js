import request from '@/utils/request'

/**
 * 订单接口（E01-E09）。
 */

/** 下单（返回 UNPAID 订单，前端跳收银台） */
export function createOrder(data) {
  return request.post('/orders', data)
}

/** 收银台支付（模拟） */
export function payOrder(id, payMethod) {
  return request.post(`/orders/${id}/pay`, { payMethod })
}

/** 卖家发货 */
export function shipOrder(id) {
  return request.post(`/orders/${id}/ship`)
}

/** 买家确认收货 / 面交单确认完成 */
export function confirmOrder(id) {
  return request.post(`/orders/${id}/confirm`)
}

/** 取消订单 */
export function cancelOrder(id, reason) {
  return request.post(`/orders/${id}/cancel`, { reason })
}

/** 我买到的订单 */
export function fetchBoughtOrders(params) {
  return request.get('/orders/bought', { params })
}

/** 我卖出的订单 */
export function fetchSoldOrders(params) {
  return request.get('/orders/sold', { params })
}

/** 订单详情 */
export function fetchOrderDetail(id) {
  return request.get(`/orders/${id}`)
}

/** 支付宝沙箱预下单（返回二维码码串） */
export function precreateAlipay(id) {
  return request.post(`/orders/${id}/alipay/precreate`)
}

/** 支付宝支付状态主动查单（true=已支付并完成流转） */
export function fetchAlipayStatus(id) {
  return request.get(`/orders/${id}/alipay/status`)
}

/** 支付宝电脑网站支付（page.pay）：返回沙箱收银台跳转 URL */
export function fetchAlipayPagePay(id) {
  return request.get(`/orders/${id}/alipay/pagepay`)
}
