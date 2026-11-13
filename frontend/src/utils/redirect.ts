/**
 * 登录后跳转目标的安全校验（防开放重定向 Open Redirect）。
 *
 * 背景（P0-6）：`Login.vue` / `Register.vue` 此前直接
 * `router.push(route.query.redirect)`，而 `redirect` 来自 URL 查询参数、完全由攻击者控制。
 * 若不做校验，`/login?redirect=//evil.example` 或 `?redirect=https://evil.example`
 * 会把用户在登录成功的那一刻送去外部站点（配合仿冒页面即可窃取凭据）。
 *
 * 判定口径：只接受**站内绝对路径**。
 * - `/student/dashboard` ✅
 * - `/student/course/1?tab=exam` ✅（保留 query/hash）
 * - `//evil.example` ❌ 协议相对 URL，浏览器会补上当前协议后跳出站点
 * - `https://evil.example` ❌ 绝对 URL
 * - `javascript:alert(1)` ❌ 伪协议
 * - `/\evil.example` ❌ 反斜杠会被部分浏览器归一化为 `/`，等价于协议相对
 */

/**
 * 控制字符与换行可用于绕过前缀检查（如 `/\r\n//evil`），一律拒绝。
 * 用码位判断而非正则：匹配 \x00-\x1f 的字面量会被 eslint no-control-regex 拦下。
 */
function hasControlChar(value: string) {
  for (let i = 0; i < value.length; i++) {
    const code = value.charCodeAt(i)
    if (code <= 31 || code === 127) return true
  }
  return false
}

/**
 * 判断给定字符串是否为安全的站内跳转目标。
 * @param {unknown} target 待校验的跳转目标（通常来自 route.query.redirect）
 * @returns {boolean} true 表示可作为 router.push 的目标
 */
export function isSafeInternalRedirect(target: unknown) {
  if (typeof target !== 'string' || target.length === 0) return false
  // 必须以单个 '/' 开头；'//' 为协议相对 URL，直接跳出站点
  if (target.charCodeAt(0) !== 47 /* '/' */) return false
  if (target.charCodeAt(1) === 47 /* '/' */) return false
  // 反斜杠归一化风险 + 伪协议（含 ':' 即视为带协议，如 'javascript:'）
  if (target.includes('\\')) return false
  if (target.includes(':')) return false
  if (hasControlChar(target)) return false
  return true
}

/**
 * 解析登录/注册成功后的最终跳转路径。
 *
 * @param {unknown} redirectQuery URL 上的 redirect 查询参数
 * @param {string} fallback 参数缺失或非法时的兜底（一般是 auth.getDefaultRoute()）
 * @returns {string} 保证为站内路径
 */
export function resolvePostAuthRedirect(redirectQuery: unknown, fallback: string): string {
  return isSafeInternalRedirect(redirectQuery) ? (redirectQuery as string) : fallback
}
