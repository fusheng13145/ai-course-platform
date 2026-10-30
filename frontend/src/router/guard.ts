import type { RouteLocationNormalized, Router } from 'vue-router'
import NProgress from 'nprogress'
import { useAuthStore } from '@/stores/auth'

/** 守卫的三种跳转落点，由各端 router 自行决定（旧端走根路径，v2 走 /**） */
export interface GuardPaths {
  loginPath: string
  forbiddenPath: string
  unavailablePath: string
}

/** 按目标路由解析落点：同一个守卫因此可同时服务旧端与 v2 两端 */
export type ResolveGuardPaths = (to: RouteLocationNormalized) => GuardPaths

/**
 * 把鉴权守卫安装到任意 router 上，旧端 router 与 v2 自有 router 共用这一份实现。
 *
 * 落点不写死：login / 403 / 503 全部由 resolvePaths(to) 给出，v2 在旧 router 被摘除后
 * 仍能独立完成登录态校验、权限探针、角色门禁与进度条。
 */
export function installRouterGuard(router: Router, resolvePaths: ResolveGuardPaths): void {
  router.beforeEach(async (to, _from, next) => {
    NProgress.start()
    const authStore = useAuthStore()
    const requiresAuth = to.matched.some((record) => record.meta.requiresAuth !== false)
    const { loginPath, forbiddenPath, unavailablePath } = resolvePaths(to)

    if (!requiresAuth) {
      if ((to.path === '/login' || to.path === '/register') && authStore.isLoggedIn) {
        // 缺陷修复（P0-3）：已登录访问登录/注册页时跳默认落地页。
        // 若默认落地页恰好等于当前路径（历史上未知角色会返回 '/login'），
        // next('/login') 会再次进入本分支 → 自指向重定向。此处对「目标 === 当前」
        // 直接终止导航（next(false)），保证重定向链路无条件有界。
        const target = authStore.getDefaultRoute()
        if (target === to.path || target === to.fullPath) {
          next(false)
          NProgress.done()
          return
        }
        next(target)
        return
      }
      next()
      return
    }

    if (!authStore.isLoggedIn) {
      // v2 有独立登录页：/** 的深链接回 /login，旧端仍回 /login。
      next({ path: loginPath, query: { redirect: to.fullPath } })
      return
    }

    // 刷新后权限可能尚未加载：确保已拉取一次，避免细粒度菜单/按钮权限判定失效。
    // permissionsLoaded 只由一次真实的 /auth/permissions 成功响应置位，
    // 因此这里用「未加载」而不是「未缓存」作为重新拉取的条件（防本地伪造）。
    if (!authStore.permissions || !authStore.permissionsLoaded) {
      // DR-45：`isLoggedIn` 只表示本地缓存里有 user，不等于服务端认这次会话。
      // 所以必须消费 fetchPermissions 回传的结论：服务端 401 就在此终止导航、带 redirect
      // 跳登录页。原实现 await 之后无视结果照常 next()，跳转又由拦截器另起一路，
      // 两条导航互相取消 → 过期用户看到「仍已登录、点击无反应，再点才跳登录」。
      const verdict = await authStore.fetchPermissions()
      if (verdict === 'expired') {
        next({ path: loginPath, query: { redirect: to.fullPath } })
        return
      }
      // 缺陷修复（2026-10-16 启动崩溃）：服务端不可达（网络错误/5xx，典型即后端尚未
      // 监听 8080）时，原实现放行后由角色门禁 fail-closed 落到 /403——把「服务没起来」
      // 显示成「你没有权限」，用户据此去查权限配置，排查方向完全被带偏（本次用户实测：
      // start-all 启动即见 ECONNREFUSED + 403 页）。
      // 不可达 ≠ 无权限：改落 /503 连接失败页（自带轮询重试，后端就绪后自动回到原目标），
      // 角色判定只在服务端真正给出结论（ok/expired）之后才进行。
      if (verdict === 'unavailable') {
        next({ path: unavailablePath, query: { redirect: to.fullPath } })
        return
      }
    }

    const requiredRole = to.matched.find((r) => r.meta.role)?.meta.role
    if (requiredRole) {
      // 角色判定一律走 effectiveRole：优先服务端回传角色，本地可篡改的 user.role
      // 仅在服务端确认过的会话里作为回退（ADMIN 还额外要求 permissionsLoaded）。
      // 因此手工把 localStorage 的 user.role 改成 ADMIN 并不能进入管理端。
      if (authStore.effectiveRole !== requiredRole) {
        next(forbiddenPath)
        return
      }
    }

    next()
  })

  router.afterEach(() => {
    NProgress.done()
  })
}
