import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import { RESULT_CODE } from '@/constants'

/**
 * 唯一的 axios 实例。
 *
 * 关键点（架构 8.4）：业务失败时后端 HTTP 仍是 200，必须靠 res.code 判断；
 * 只有 Spring Security 的 401/403 才会走 axios 的 rejected 分支。
 */
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 10000
})

service.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

let redirecting = false

/** 清登录态并跳登录页（同一时刻只跳一次） */
function toLogin() {
  if (redirecting) return
  redirecting = true
  const auth = useAuthStore()
  const current = router.currentRoute.value.fullPath
  // 后台会话过期要回后台登录页，不能把审核员丢到用户登录页
  const inAdmin = current.startsWith('/admin')
  auth.clear()
  router.replace(inAdmin ? '/admin/login' : { path: '/login', query: { redirect: current } })
  setTimeout(() => {
    redirecting = false
  }, 1000)
}

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (!res || typeof res.code === 'undefined') {
      return res
    }
    // ① 业务成功：剥壳直接返回 data（分页接口拿到的是 IPage：records/total/pages）
    if (res.code === RESULT_CODE.SUCCESS) {
      return res.data ?? null
    }
    // ② 业务失败：HTTP 200 + code≠200
    if (res.code === RESULT_CODE.UNAUTHORIZED) {
      toLogin()
      return Promise.reject(Object.assign(new Error(res.msg), { code: res.code }))
    }
    if (!response.config.skipErrorTip) {
      ElMessage.error(res.msg || '操作失败')
    }
    return Promise.reject(Object.assign(new Error(res.msg), { code: res.code }))
  },
  (error) => {
    const cfg = error.config || {}
    const res = error.response?.data

    if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请重试')
      return Promise.reject(error)
    }
    if (!error.response) {
      ElMessage.error('网络异常，请检查后端服务是否启动')
      return Promise.reject(error)
    }

    const status = error.response.status
    if (status === 401 || res?.code === RESULT_CODE.UNAUTHORIZED) {
      ElMessage.warning('登录已过期，请重新登录')
      toLogin()
      return Promise.reject(error)
    }
    if (status === 403) {
      if (!cfg.skipErrorTip) ElMessage.error(res?.msg || '没有访问权限')
      return Promise.reject(error)
    }
    if (status === 404) {
      if (!cfg.skipErrorTip) ElMessage.error('请求资源不存在')
      return Promise.reject(error)
    }
    if (status >= 500) {
      ElMessage.error('系统繁忙，请稍后再试')
      return Promise.reject(error)
    }
    if (!cfg.skipErrorTip) ElMessage.error(res?.msg || '请求失败')
    return Promise.reject(error)
  }
)

export default service
