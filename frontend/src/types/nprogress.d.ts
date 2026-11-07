/**
 * nprogress 的最小模块声明。
 *
 * 包本身不携带 d.ts，`@types/nprogress` 是一次依赖新增（§3.2 需授权），
 * 而本仓只用 start/done 两个入口（src/router/guard.ts），故先落本地声明。
 * 若日后用到 set/configure 等更多 API，再同步扩充本文件或改走 @types。
 */
declare module 'nprogress' {
  interface NProgress {
    start(): void
    done(): void
    set(n: number): NProgress
    remove(): void
    isStarted(): boolean
    configure(options: {
      showSpinner?: boolean
      speed?: number
      minimum?: number
      easing?: string
      trickle?: boolean
      trickleSpeed?: number
      template?: string
    }): NProgress
    status: number | null
  }
  const NProgress: NProgress
  export default NProgress
}
