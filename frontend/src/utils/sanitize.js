import DOMPurify from 'dompurify'

/**
 * Markdown 渲染相关的安全 HTML 标签白名单
 * 仅允许纯展示类标签，禁止 script/iframe/object/embed/form/input 等可执行/交互标签
 */
const ALLOWED_TAGS = [
  // 块级结构
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'p', 'div', 'span', 'br', 'hr',
  'pre', 'code', 'blockquote',
  // 列表
  'ul', 'ol', 'li',
  // 表格（简化）
  'table', 'thead', 'tbody', 'tr', 'th', 'td',
  // 文本语义
  'strong', 'em', 'b', 'i', 'u', 's', 'del', 'ins',
  'small', 'sub', 'sup', 'mark',
  // 链接与图片（href/src 由 ALLOWED_ATTR 控制协议白名单）
  'a', 'img',
  // SVG 图标（图标/图表渲染场景；图形属性见 ALLOWED_ATTR）
  'svg', 'g', 'path', 'circle', 'rect', 'line', 'polyline', 'polygon',
  'text', 'title', 'defs', 'use',
]

/**
 * 安全属性白名单
 * 仅允许展示类属性，禁止 on* 事件、javascript: 伪协议等
 */
const ALLOWED_ATTR = [
  // 链接与图片
  'href', 'src', 'alt', 'title',
  // 样式（受限于内联样式，不包含 position/behavior 等危险属性）
  'class', 'id',
  // 表格
  'colspan', 'rowspan',
  // 通用
  'target',
  // SVG 图形属性（不含任何 on* 事件属性；DOMPurify 会拦截
  // url(...) 形式的危险引用，fill/stroke 仅允许颜色与 CSS 变量值）
  'd', 'viewBox', 'width', 'height', 'fill', 'stroke',
  'stroke-width', 'stroke-linecap', 'stroke-linejoin',
  'cx', 'cy', 'r', 'x', 'y', 'transform',
  'text-anchor', 'font-size', 'font-weight',
  'stroke-dasharray', 'stroke-dashoffset',
  'points', 'x1', 'y1', 'x2', 'y2',
  'fill-rule', 'clip-rule',
]

/**
 * DOMPurify 3.x ESM 默认导出为命名空间对象（含 sanitize 方法），不可作为函数调用。
 * 直接引用该对象即可，避免 TypeError: default is not a function。
 */
const purify = DOMPurify

/**
 * HTML 净化函数
 * 使用 DOMPurify 移除 XSS 向量，仅保留白名单内的安全标签和属性
 *
 * @param {string} html - 待净化的 HTML 字符串
 * @returns {string} 净化后的安全 HTML
 */
export function sanitizeHtml(html) {
  if (!html || typeof html !== 'string') return ''
  return purify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
  })
}

/**
 * 深度递归净化对象中所有字符串属性
 * 适用于 API 响应体中可能包含 HTML 的场景
 *
 * @param {*} obj - 任意类型值
 * @returns {*} 净化后的同构结构
 */
export function sanitizeObject(obj) {
  if (obj === null || obj === undefined) return obj
  if (typeof obj === 'string') return sanitizeHtml(obj)
  if (Array.isArray(obj)) return obj.map(sanitizeObject)
  if (typeof obj === 'object') {
    const result = {}
    for (const key of Object.keys(obj)) {
      result[key] = sanitizeObject(obj[key])
    }
    return result
  }
  return obj
}

export default { sanitizeHtml, sanitizeObject }
