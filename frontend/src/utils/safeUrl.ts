/**
 * URL 协议白名单（P-12 处置清单 F-3）。
 *
 * 存在理由：把接口／上传响应里的地址绑进 `:href`／`:src` 之前必须过一次协议判定，
 * 否则 `javascript:`、`data:`、`vbscript:`、`file:` 这些伪协议会直接被浏览器当作可执行内容
 * （`javascript:` 在 `:href` 上等价于内联脚本，也是 CSP 收紧路上的固定靶子）。
 *
 * 本实现由 `v2/views/course/tabs/TasksTab.vue` 的本地 `safeUrl` 原样上提（判据本体不变：
 * 仅放行 `http:`／`https:`，其余一律回空串），上提后 `TasksTab` 与
 * `V2CreateCourseView` 的封面／附件共用同一份，避免各写各的。
 *
 * 相对地址按当前源解析（`/uploads/a.png` → `http(s)://host/uploads/a.png`），
 * 因此"合法返回空串"的只有非法值与非 http(s) 协议两类。
 */

/** 放行的协议（URL 对象带冒号） */
const ALLOWED_PROTOCOLS = ['http:', 'https:']

/**
 * @param value 待校验的地址（可能是接口字段、undefined 或空串）
 * @returns 合法时返回解析后的绝对 URL，其余一律返回空串
 */
export function safeUrl(value?: string): string {
  if (typeof value !== 'string' || !value.trim()) return ''
  try {
    const url = new URL(value.trim(), window.location.origin)
    return ALLOWED_PROTOCOLS.includes(url.protocol) ? url.href : ''
  } catch {
    return ''
  }
}

export default safeUrl
