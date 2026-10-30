import logger from '@/utils/logger'

/** 与 Element Plus 的 ElMessage 调用形状对齐，旧端接入时可直接原物注入 */
export interface OverlayMessageHost {
  info: (message: string) => void
  success: (message: string) => void
  warning: (message: string) => void
  error: (message: string) => void
}

/**
 * 确认框选项：只表达语义，不出现宿主专有类名。
 * 危险操作主按钮着色用 `danger` 表达，由适配器翻译成具体宿主样式。
 */
export interface OverlayConfirmOptions {
  type?: 'success' | 'warning' | 'info' | 'error'
  confirmButtonText?: string
  cancelButtonText?: string
  /** 危险操作：适配器据此给主按钮上危险色 */
  danger?: boolean
}

/**
 * 浮层宿主：提示与确认两件事。
 *
 * 抽出来的原因是六个共用件被旧端与 v2 同时消费，却静态 `import { ElMessage, ElMessageBox }`，
 * 使 v2 依赖闭包传递依赖 Element Plus——「摘掉旧端就能摘 EP」因此不成立。
 * 宿主改为注入后，摘除点收敛到 `bootstrap/apiHostEp.ts` 一处。
 */
export interface OverlayHost {
  message: OverlayMessageHost
  /** 用户确认 → true；取消 / 关闭 / 弹框异常 → false（绝不 reject，调用方无需区分） */
  confirm: (message: string, title: string, options?: OverlayConfirmOptions) => Promise<boolean>
}

/**
 * 未注册宿主时的降级：提示落日志，确认按「取消」处理。
 * 故意不静默——无人确认时危险操作（删除、交卷、退出）不执行，但不能让调用方
 * 误以为用户点了取消；warn/error 在非 dev 也可见，便于定位漏注册。
 */
const fallback: OverlayHost = {
  message: {
    info: (message) => logger.log(`[overlay:info] ${message}`),
    success: (message) => logger.log(`[overlay:success] ${message}`),
    warning: (message) => logger.warn(`[overlay:warning] ${message}`),
    error: (message) => logger.error(`[overlay:error] ${message}`),
  },
  confirm: (message, title) => {
    logger.warn(`[overlay:confirm:no-host] ${title}｜${message}（未注册宿主，按取消处理）`)
    return Promise.resolve(false)
  },
}

let host: OverlayHost = fallback

/** 逐键覆盖，便于测试只注入其中一件外设 */
export function setOverlayHost(next: Partial<OverlayHost>) {
  host = { ...host, ...next }
}

export function overlayHost(): OverlayHost {
  return host
}

/** 供入口与测试确认宿主是否已被真实实现接管（仍指向降级实现即为未接管） */
export function isFallbackOverlayHost(): boolean {
  return host.confirm === fallback.confirm
}
