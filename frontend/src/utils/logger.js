/* eslint-disable no-console */
/**
 * 统一日志工具（前端唯一被允许直接使用 console 的出口）。
 *
 * 设计目标：
 * 1. 消除散落全站的裸 console.*：调试类日志（log/info/debug）仅在开发环境输出，
 *    生产环境静默，避免泄露内部状态、污染控制台、影响性能与专业度。
 * 2. 保留 warn/error 的可见性（开发与生产都调用），为将来接入前端监控/上报（如 Sentry）
 *    预留统一切面——只需在此处改一次即可全站生效。
 * 3. 与 vite 生产构建的 `drop: ['console']` 形成纵深：即便某处遗漏，生产产物也不含 console。
 *
 * 本文件是全站唯一 eslint-disable no-console 的合规豁免点，而非关闭规则「放水」。
 */

// import.meta.env.DEV 由 Vite 注入；在非 Vite 环境（如单测）下安全回退为 false
const isDev = Boolean(import.meta && import.meta.env && import.meta.env.DEV)

export const logger = {
  /**
   * 通用日志输出（仅开发环境）
   * 生产环境静默，避免泄露内部状态
   * @param {...*} args - 任意日志参数，直接透传至 console.log
   */
  log: (...args) => {
    if (isDev) console.log(...args)
  },
  /**
   * 信息级别日志输出（仅开发环境）
   * @param {...*} args - 任意日志参数，直接透传至 console.info
   */
  info: (...args) => {
    if (isDev) console.info(...args)
  },
  /**
   * 调试级别日志输出（仅开发环境）
   * @param {...*} args - 任意日志参数，直接透传至 console.debug
   */
  debug: (...args) => {
    if (isDev) console.debug(...args)
  },
  /**
   * 警告级别日志输出（开发与生产均输出）
   * 保留全环境可见性，便于定位非致命异常；未来可在此接入远端监控上报（如 Sentry）
   * @param {...*} args - 任意日志参数，直接透传至 console.warn
   */
  warn: (...args) => {
    console.warn(...args)
  },
  /**
   * 错误级别日志输出（开发与生产均输出）
   * 保留全环境可见性，用于捕获致命异常；未来可在此接入远端错误上报
   * @param {...*} args - 任意日志参数，直接透传至 console.error
   */
  error: (...args) => {
    console.error(...args)
  },
}

export default logger
