import request from '@/utils/request'

/**
 * 分类接口（B09）。
 */

/** 前台分类列表（按 sort 升序，无需登录） */
export function fetchCategories() {
  return request.get('/categories')
}
