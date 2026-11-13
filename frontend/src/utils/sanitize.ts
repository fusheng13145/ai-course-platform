import DOMPurify from 'dompurify'
import { type ApiData } from '@/utils/response'

/**
 * 展示类标签基表
 * 仅允许纯展示类标签，禁止 script/iframe/object/embed/form/input/button/textarea 等
 * 可执行/可交互标签（可信域内钓鱼表单的载体就在 form＋input 这一对里）
 */
const BASE_ALLOWED_TAGS = [
  // 块级结构
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'p',
  'div',
  'span',
  'br',
  'hr',
  'pre',
  'code',
  'blockquote',
  // 列表
  'ul',
  'ol',
  'li',
  // 表格（简化）
  'table',
  'thead',
  'tbody',
  'tr',
  'th',
  'td',
  // 文本语义
  'strong',
  'em',
  'b',
  'i',
  'u',
  's',
  'del',
  'ins',
  'small',
  'sub',
  'sup',
  'mark',
  // 链接与图片（href/src 由 ALLOWED_ATTR 控制协议白名单）
  'a',
  'img',
  // SVG 图标（图标/图表渲染场景；图形属性见 ALLOWED_ATTR）
  'svg',
  'g',
  'path',
  'circle',
  'rect',
  'line',
  'polyline',
  'polygon',
  'text',
  'title',
  'defs',
  'use',
]

/**
 * 安全属性基表
 * 仅允许展示类属性，禁止 on* 事件、javascript: 伪协议等。
 * **故意不含 `style`**：内联样式是 CSS 数据外带（`background:url(...)`）与界面覆盖的
 * 唯一残留通道，也是 CSP `style-src 'unsafe-inline'` 的真实依赖之一（P-12 §F-1／F-5）。
 * 三档白名单一律经 `FORBID_ATTR: ['style']` 双保险排除它。
 */
const BASE_ALLOWED_ATTR = [
  // 链接与图片
  'href',
  'src',
  'alt',
  'title',
  // 样式类标识（只有 class/id，没有 style）
  'class',
  'id',
  // 表格
  'colspan',
  'rowspan',
  // 通用
  'target',
  // SVG 图形属性（不含任何 on* 事件属性；DOMPurify 会拦截
  // url(...) 形式的危险引用，fill/stroke 仅允许颜色与 CSS 变量值）
  'd',
  'viewBox',
  'width',
  'height',
  'fill',
  'stroke',
  'stroke-width',
  'stroke-linecap',
  'stroke-linejoin',
  'cx',
  'cy',
  'r',
  'x',
  'y',
  'transform',
  'text-anchor',
  'font-size',
  'font-weight',
  'stroke-dasharray',
  'stroke-dashoffset',
  'points',
  'x1',
  'y1',
  'x2',
  'y2',
  'fill-rule',
  'clip-rule',
]

/**
 * DOMPurify 3.x ESM 默认导出为命名空间对象（含 sanitize 方法），不可作为函数调用。
 * 直接引用该对象即可，避免 TypeError: default is not a function。
 */
const purify = DOMPurify

/**
 * 三处 `v-html` 位点共用的净化档位（P-12 处置清单 F-1）。
 *
 * 存在理由：本仓的 `v-html` 只有三个位点，处置前它们各写各的配置——`ReaderTab` 用宽
 * profile `USE_PROFILES:{html:true}`（实测放行 `<form action>`＋`<input name>` 与
 * `style="background:url(...)"`），`Table`／`LegalView` 用本地内联白名单，而这份
 * 统一配置全仓零消费方＝治理是死配置。自此**白名单只在这里定义**，位点只选档位，
 * 配置由 `src/ui/__tests__/xssSurface.test.ts` 静态扫描钉住（新增位点或位点里
 * 重新 import dompurify 即判红）。
 */
interface Profile {
  /** 展示类标签白名单 */
  tags: string[]
  /** 展示类属性白名单（一律不含 style） */
  attrs: string[]
}

/** 基表档位：接口正文（教师撰写的章节 HTML）——可信域外的输入，最要紧的是不留表单与内联样式 */
const READER: Profile = { tags: BASE_ALLOWED_TAGS, attrs: BASE_ALLOWED_ATTR }

/**
 * 表格单元格档位（`Table`）。
 * 与处置前该位点的本地配置逐条对照：标签集**一字未动**
 * （`b/small/span/i/strong/br/time`，其中 `time` 不在基表内，按原样保留，不借"统一"放大）；
 * 属性由 `class,style` 收到只剩 `class`——`style` 是被排除的项，不是被放大的项，
 * 且实测 `src/ui/data/**` 与 `nav.ts` 的单元格内容里 `style=` 命中为 0，收掉不改变任何渲染。
 */
const TABLE: Profile = {
  tags: ['b', 'small', 'span', 'i', 'strong', 'br', 'time'],
  attrs: ['class'],
}

/**
 * 法务文案档位（`LegalView`）。
 * 与处置前该位点的本地配置逐条对照：标签与属性**完全同集**
 * （`section/h2/p/strong/ul/li/br/span/em` ＋ `class`）。`section` 不在基表内，
 * 是这一档独有的承载元素（`.contact-section` 卡片靠它上样式），按原样保留，
 * 不借"统一"把 `a/img/svg` 之类放进这个纯静态页面。
 */
const LEGAL: Profile = {
  tags: ['section', 'h2', 'p', 'strong', 'ul', 'li', 'br', 'span', 'em'],
  attrs: ['class'],
}

/**
 * 档位执行器：三档共用的调用口径。
 * `FORBID_ATTR: ['style']` 是显式的双保险——即使日后有人把 `style` 误加回属性基表，
 * 内联样式仍然进不了 DOM（F-1 的判据本体）。
 */
function sanitizeByProfile(html: unknown, profile: Profile): string {
  if (!html || typeof html !== 'string') return ''
  return purify.sanitize(html, {
    ALLOWED_TAGS: profile.tags,
    ALLOWED_ATTR: profile.attrs,
    FORBID_ATTR: ['style'],
  })
}

/**
 * 接口正文净化（Reader 档位）。
 * 使用 DOMPurify 移除 XSS 向量，仅保留白名单内的安全标签和属性；`style` 属性与
 * `form`／`input` 等可交互标签一律剥离。
 *
 * @param html 待净化的 HTML 字符串
 * @returns 净化后的安全 HTML
 */
export function sanitizeHtml(html: unknown) {
  return sanitizeByProfile(html, READER)
}

/**
 * 表格原型快照单元格净化（Table 档位）——`Table` 的唯一入口
 *
 * @param html 单元格文本（可能是内联标签片段）
 */
export function sanitizeTableCellHtml(html: unknown) {
  return sanitizeByProfile(html, TABLE)
}

/**
 * 法务静态文案净化（Legal 档位）——`LegalView` 的唯一入口
 *
 * @param html `data/legal.json` 里的条款正文
 */
export function sanitizeLegalHtml(html: unknown) {
  return sanitizeByProfile(html, LEGAL)
}

/**
 * 深度递归净化对象中所有字符串属性
 * 适用于 API 响应体中可能包含 HTML 的场景
 *
 * @param {*} obj - 任意类型值
 * @returns {*} 净化后的同构结构
 */
export function sanitizeObject(obj: ApiData): ApiData {
  if (obj === null || obj === undefined) return obj
  if (typeof obj === 'string') return sanitizeHtml(obj)
  if (Array.isArray(obj)) return obj.map(sanitizeObject)
  if (typeof obj === 'object') {
    const result: Record<string, ApiData> = {}
    for (const key of Object.keys(obj)) {
      result[key] = sanitizeObject(obj[key])
    }
    return result
  }
  return obj
}

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  sanitizeHtml,
  sanitizeTableCellHtml,
  sanitizeLegalHtml,
  sanitizeObject,
}
