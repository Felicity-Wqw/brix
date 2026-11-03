import request from '@/utils/request'

/**
 * 个人买卖可视化分析（G05）。
 *
 * @returns {Promise<{ buy: object, sell: object }>} 买入/卖出两维度统计
 */
export function getMyAnalysis() {
  return request.get('/users/me/analysis')
}

/**
 * 我的浏览历史分页（按最近浏览倒序）。
 *
 * @param {object} params 分页参数 { currentPage, pageSize }
 * @returns {Promise<object>} IPage<BrowseHistoryVO>
 */
export function getBrowseHistory(params) {
  return request.get('/users/me/browse-history', { params })
}
