/**
 * 全局认证路由守卫。
 *
 * <p>仅客户端生效（SSR 阶段无法读取 localStorage，避免误判登录态）：
 * 未登录访问受保护页面跳转登录页；已登录访问登录页跳回首页；
 * 校验 Token 有效性失败时登出并回登录页；管理后台额外校验管理员身份。</p>
 */
export default defineNuxtRouteMiddleware(async (to) => {
  // SSR 阶段不拦截：保证刷新时先渲染页面，再由客户端水合后判定
  if (import.meta.server) {
    return
  }

  const auth = useAuthStore()
  // SSR 水合后 Pinia 状态中的 token 为空，先从 localStorage 恢复登录态
  auth.initToken()

  // 首页（/）公开，不拦截；聊天与后台需登录
  const isPublic = to.path === '/' || to.path === '/login'
  if (to.path === '/login') {
    if (auth.isLoggedIn) {
      return navigateTo('/chat')
    }
    return
  }
  if (isPublic) {
    return
  }

  // 未登录 → 登录页
  if (!auth.isLoggedIn) {
    return navigateTo(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
  }

  // 已登录：校验 Token 有效性并恢复用户信息
  try {
    await auth.fetchMe()
  } catch {
    auth.logout()
    return navigateTo('/login')
  }

  // 管理后台：非管理员跳回首页
  if (to.path.startsWith('/admin') && !auth.isAdmin) {
    return navigateTo('/')
  }
})
