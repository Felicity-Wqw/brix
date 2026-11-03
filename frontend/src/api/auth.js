import request from '@/utils/request'

/**
 * 认证与用户相关接口（A01-A06、G02）。
 */

/** 注册（注册即登录） */
export function register(data) {
  return request.post('/auth/register', data)
}

/** 登录 */
export function login(data) {
  return request.post('/auth/login', data)
}

/** 当前登录用户（含发布数 / 在售数 / 卖出数） */
export function fetchMe() {
  return request.get('/auth/me')
}

/** 修改个人资料 */
export function updateProfile(data) {
  return request.put('/users/me', data)
}

/** 修改密码 */
export function changePassword(data) {
  return request.put('/users/me/password', data)
}

/** 上传头像（同时写库） */
export function uploadAvatar(file) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/users/me/avatar', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 某卖家的评价列表（含平均分 / 总数） */
export function fetchUserReviews(userId, params) {
  return request.get(`/users/${userId}/reviews`, { params })
}
