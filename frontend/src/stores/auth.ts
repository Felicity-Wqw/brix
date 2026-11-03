import { defineStore } from 'pinia'
import request from '@/utils/request'

/** 用户信息（id 为字符串：后端 Long → String） */
export interface UserInfo {
  id: string
  username: string
  nickname: string
  avatar: string
  school?: string
  campus?: string
  phone?: string
  bio?: string
  role: 'USER'
  roleDesc?: string
  status: 'NORMAL' | 'DISABLED'
  statusDesc?: string
  creditScore: number
  createdAt?: string
  stats?: {
    itemCount: number
    onSaleCount: number
    soldCount: number
  }
}

/** 登录返回体 */
interface LoginResult {
  token: string
  user: UserInfo
}

/** 登录入参 */
export interface LoginPayload {
  username: string
  password: string
}

const TOKEN_KEY = 'brix_token'
const USER_KEY = 'brix_user'
/** 游客访问标记：登录页点「游客逛一逛」后写入，登录/注册/退出时清除 */
const GUEST_KEY = 'brix_guest_mode'

/**
 * 只保留一个 store：认证态。
 * token 与 user 持久化到 localStorage（刷新保持登录）；unreadCount 不持久化。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: (localStorage.getItem(TOKEN_KEY) || '') as string,
    user: null as UserInfo | null,
    unreadCount: 0,
    /** 我发布的商品上收到的未读留言数（导航红点） */
    commentUnread: 0
  }),

  getters: {
    /** 是否已登录 */
    isLogin: (state): boolean => !!state.token,

    /** 当前角色（未登录为空串；本平台仅 USER） */
    role: (state): string => state.user?.role || '',

    /** 是否处于游客浏览模式（未登录但选择先逛逛） */
    isGuestMode: (): boolean => localStorage.getItem(GUEST_KEY) === '1'
  },

  actions: {
    /**
     * 登录。
     * @param payload 账号密码
     */
    async login(payload: LoginPayload): Promise<void> {
      const data = (await request.post('/auth/login', payload)) as LoginResult
      this.applyLogin(data)
    },

    /**
     * 注册（注册即登录）。
     * @param payload 注册信息
     */
    async register(payload: Record<string, unknown>): Promise<void> {
      const data = (await request.post('/auth/register', payload)) as LoginResult
      this.applyLogin(data)
    },

    /**
     * 写入登录态并持久化。
     * @param data 登录返回体
     */
    applyLogin(data: LoginResult): void {
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      localStorage.removeItem(GUEST_KEY) // 登录/注册成功即退出游客模式
    },

    /** 进入游客浏览模式（仅可浏览页，发布/下单仍需登录） */
    enterGuestMode(): void {
      localStorage.setItem(GUEST_KEY, '1')
    },

    /** 拉取当前用户信息（含个人数据统计） */
    async fetchMe(): Promise<void> {
      const data = (await request.get('/auth/me')) as UserInfo
      this.user = data
      localStorage.setItem(USER_KEY, JSON.stringify(data))
    },

    /** 设置全局未读数 */
    setUnread(count: number): void {
      this.unreadCount = Math.max(0, Number(count) || 0)
    },

    /** 设置商品留言未读数（导航红点） */
    setCommentUnread(count: number): void {
      this.commentUnread = Math.max(0, Number(count) || 0)
    },

    /** 未读数 +1 */
    incUnread(): void {
      this.unreadCount += 1
    },

    /** 清除登录态 */
    clear(): void {
      this.token = ''
      this.user = null
      this.unreadCount = 0
      this.commentUnread = 0
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      localStorage.removeItem(GUEST_KEY)
    },

    /** 退出登录 */
    logout(): void {
      this.clear()
    }
  }
})
