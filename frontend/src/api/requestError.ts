/**
 * 响应错误处理的纯函数层（FE-07）。
 *
 * 背景：request.ts 的响应错误拦截器单函数约 180 行，状态码分类、文案映射、
 * 重试判定与网络错误归类耦合在一起，逐分支回归只能靠整实例级的拦截器测试。
 * 本模块收拢其中**无副作用、可独立断言**的部分；重试 / 401 单飞刷新等
 * 依赖模块态（retryCache、refreshPromise、service 实例）的控制流仍留在
 * request.ts，不在此处复制。
 *
 * 行为等价约束：迁移前后的文案与判定逐字一致，由 request.test.ts 的
 * 拦截器用例与本模块的单测双向钉住。
 */

/** 状态码 → 用户可读文案（与 request.ts 原表逐字一致） */
export const STATUS_MESSAGES: Record<number, string> = {
  400: '请求参数错误，请检查输入',
  401: '登录已过期，请重新登录',
  403: '抱歉，您没有权限执行此操作',
  404: '请求的资源不存在',
  405: '请求方法不被允许',
  408: '请求超时，请稍后重试',
  409: '数据冲突，请刷新后重试',
  410: '资源已被删除',
  413: '上传文件过大',
  415: '不支持的媒体类型',
  422: '数据校验失败',
  423: '资源已被锁定',
  429: '操作过于频繁，请稍后再试',
  500: '服务器内部错误，请联系管理员',
  501: '服务器不支持此功能',
  502: '网关错误，服务暂时不可用',
  503: '服务维护中，请稍后访问',
  504: '网关超时，请稍后重试',
}

/** 根据状态码获取友好提示信息；未知状态码回退为「请求失败 (status)」 */
export function getStatusMessage(status?: number): string {
  if (status === undefined || status === null) return ''
  return STATUS_MESSAGES[status] || `请求失败 (${status})`
}

/**
 * 网络错误（无响应）的消息归类：按 axios / 浏览器错误消息的关键词映射为
 * 用户可读文案，未命中关键词时回退「网络异常」。
 */
export function resolveNetworkErrorMessage(errorMessage: string): string {
  if (errorMessage.includes('timeout') || errorMessage.includes('Timeout')) {
    return '请求超时，请检查网络连接'
  }
  if (errorMessage.includes('Network Error')) {
    return '网络不可用，请检查网络设置'
  }
  if (errorMessage.includes('ERR_INTERNET_DISCONNECTED')) {
    return '网络已断开，请连接后重试'
  }
  return '网络异常'
}

/* ============================================
 * 幂等性判定（P1-7）
 * ============================================ */
/**
 * 仅 HTTP 语义上「可安全重复」的方法才允许自动重试。
 *
 * 缺陷修复（P1-7）：原实现对所有请求（含 POST/PUT/DELETE）在网络抖动时自动重试一次。
 * 交卷（POST /exam-sessions/{id}/submit）、上传、批改等请求并非真正幂等——
 * 首次请求可能已被服务端处理但响应在回程丢失，重试会产生**重复提交/重复记录**。
 * 故重试白名单只保留 GET/HEAD/OPTIONS。
 */
export const IDEMPOTENT_METHODS = ['get', 'head', 'options']

export function isIdempotentMethod(method?: string): boolean {
  const m = (method || 'get').toLowerCase()
  return IDEMPOTENT_METHODS.includes(m)
}

/**
 * 422 校验失败的字段级错误摘要（Spring WebFlux detail 数组 → 分号连接）。
 * 无可提取字段时返回空串，由调用方回退通用文案。
 */
export function extractFieldErrorSummary(detail: unknown): string {
  if (!Array.isArray(detail) || detail.length === 0) return ''
  return detail
    .map(
      (d) => (d as { msg?: string; message?: string })?.msg || (d as { message?: string })?.message,
    )
    .filter(Boolean)
    .join('; ')
}
