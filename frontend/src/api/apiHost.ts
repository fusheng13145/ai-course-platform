import logger from '@/utils/logger'

/** 与 Element Plus 的 ElMessage 调用形状对齐，旧端接入时可直接原物注入 */
export interface ApiMessageHost {
  error: (message: string) => void
  warning: (message: string) => void
  info: (message: string) => void
  success: (message: string) => void
}

/**
 * HTTP 层的外设宿主：提示、读当前路径、跳登录页三件事。
 *
 * 抽出来的原因是 `api/request.ts` 被两端共用，而它原先静态 `import { ElMessage }`
 * 与 `import router`，使 v2 每个页面都传递依赖 Element Plus 与旧端路由——
 * 旧前端下线时 EP 那 709.25 KB 就摘不干净。宿主改为注入后，摘除点收敛到入口一处。
 */
export interface ApiHost {
  message: ApiMessageHost
  /** 与 router.push 同形：既接受路径字符串，也接受 { path, query } */
  push: (target: string | { path: string; query?: Record<string, string> }) => void
  currentPath: () => string
}

/** 降级导航的目标地址（纯函数，便于单测；jsdom 里 location.assign 不可重定义） */
export function fallbackLocationFor(
  target: string | { path: string; query?: Record<string, string> },
): string {
  const path = typeof target === 'string' ? target : target.path
  const redirect = typeof target === 'string' ? '' : target.query?.redirect
  return redirect ? `${path}?redirect=${encodeURIComponent(redirect)}` : path
}

/**
 * 未注册宿主时的降级：提示落 console，导航走浏览器整页跳转。
 * 故意不静默——401 若什么都不做，用户会被留在一个看似登录实则失效的页面上。
 */
const fallback: ApiHost = {
  message: {
    error: (message) => logger.error(`[api:error] ${message}`),
    warning: (message) => logger.warn(`[api:warning] ${message}`),
    info: (message) => logger.log(`[api:info] ${message}`),
    success: (message) => logger.log(`[api:success] ${message}`),
  },
  push: (target) => {
    if (typeof window === 'undefined') return
    window.location.assign(fallbackLocationFor(target))
  },
  currentPath: () => (typeof window === 'undefined' ? '' : window.location.pathname),
}

let host: ApiHost = fallback

/** 逐键覆盖，便于测试只注入其中一件外设 */
export function setApiHost(next: Partial<ApiHost>) {
  host = { ...host, ...next }
}

export function apiHost(): ApiHost {
  return host
}

/** 供入口与测试确认宿主是否已被真实实现接管（仍指向降级实现即为未接管） */
export function isFallbackHost(): boolean {
  return host.push === fallback.push
}
