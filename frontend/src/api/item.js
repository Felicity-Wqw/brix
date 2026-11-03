import request from '@/utils/request'

/**
 * 商品相关接口（B01-B08）。
 */

/** 发布商品（返回 PENDING 状态） */
export function createItem(data) {
  return request.post('/items', data)
}

/** 前台商品列表（仅 ON_SALE） */
export function fetchItems(params) {
  return request.get('/items', { params })
}

/** 商品详情（后端浏览量 +1） */
export function fetchItemDetail(id) {
  return request.get(`/items/${id}`)
}

/** 同类推荐（同子类优先，同父类补足，最多 4 件） */
export function fetchSimilarItems(id) {
  return request.get(`/items/${id}/similar`)
}

/** 我的商品（全部状态） */
export function fetchMyItems(params) {
  return request.get('/items/mine', { params })
}

/** 编辑商品（编辑后回到待审核） */
export function updateItem(id, data) {
  return request.put(`/items/${id}`, data)
}

/** 删除商品 */
export function deleteItem(id) {
  return request.delete(`/items/${id}`)
}

/** 上架 / 下架（action: ON_SALE | OFF_SHELF） */
export function updateItemStatus(id, action) {
  return request.patch(`/items/${id}/status`, { action })
}
