/**
 * 认证状态（Pinia Store）。
 *
 * <p>负责登录、注册、当前用户信息与登出。Token 持久化于 localStorage，
 * 由 {@link utils/request} 自动附加到请求头。</p>
 */
import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister, me } from '@/api/traceqa/renzheng'
import { TOKEN_KEY } from '@/utils/request'
import type { UserInfo } from '@/utils/api-types'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    /** JWT 令牌（初始化时从 localStorage 恢复，刷新页面后保持登录态） */
    token:
      (typeof window !== 'undefined' && window.localStorage.getItem(TOKEN_KEY)) || '',
    /** 当前用户信息 */
    userInfo: null as UserInfo | null
  }),

  getters: {
    /** 是否已登录 */
    isLoggedIn: (state): boolean => Boolean(state.token),
    /** 是否管理员（拥有 all 权限） */
    isAdmin: (state): boolean => Boolean(state.userInfo?.permissions?.includes('all'))
  },

  actions: {
    /** 登录 */
    async login(username: string, password: string): Promise<void> {
      const res = await apiLogin({ username, password })
      const data = res.data
      if (!data) {
        throw new Error('登录响应异常')
      }
      this.token = data.token
      this.userInfo = data.userInfo
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(TOKEN_KEY, data.token)
      }
    },

    /** 注册 */
    async register(username: string, password: string, nickname?: string): Promise<void> {
      const res = await apiRegister({ username, password, nickname })
      this.userInfo = res.data ?? null
    },

        /** 从 localStorage 恢复令牌（SSR 水合后 token 为空，需在客户端守卫中调用） */
    initToken(): void {
      if (!this.token && typeof window !== 'undefined') {
        this.token = window.localStorage.getItem(TOKEN_KEY) || ''
      }
    },

    /** 拉取当前用户信息（刷新页面后恢复登录态） */
    async fetchMe(): Promise<void> {
      if (!this.token && typeof window !== 'undefined') {
        this.token = window.localStorage.getItem(TOKEN_KEY) || ''
      }
      if (!this.token) {
        return
      }
      const res = await me()
      this.userInfo = res.data ?? null
    },

    /** 登出（清除本地令牌） */
    logout(): void {
      this.token = ''
      this.userInfo = null
      if (typeof window !== 'undefined') {
        window.localStorage.removeItem(TOKEN_KEY)
      }
    }
  }
})
