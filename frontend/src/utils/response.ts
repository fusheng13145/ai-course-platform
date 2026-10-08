/**
 * 统一响应解包工具。
 *
 * 响应拦截器（api/request.ts）在请求成功时返回**整个业务信封**
 * 即 { code, message, data, timestamp }（仅当 code !== 200 时 reject），
 * 因此业务字段要从 res.data 里读。
 * 历史上部分视图为兼容「裸响应」写了防御式双取值（res.data || res、
 * r?.data ?? r 等）。unwrap 将这些写法收敛为单一语义，集中维护。
 *
 * 适用：标准 ApiResponse 信封的一层解包。
 * 不适用：分页多字段兼容（res.records || res.list）或双层 data 等接口特例，
 * 此类保留原写法。
 *
 * @template T 解包后的业务数据类型（缺省 ApiPayload，调用方可按上下文收窄）
 */

/** 标准业务信封（与后端 ApiResponse 对齐，供响应解包类型化使用） */
export interface ApiEnvelope<T = unknown> {
  code: number
  message: string
  data: T
  timestamp?: number
}

/**
 * 业务数据的缺省类型：可索引对象。
 * 后端返回体为对象（分页 records/list/total、实体字段等），以带索引签名的 Record
 * 描述既保留任意字段访问能力，又不同于 any——误用点仍需显式收窄。
 */
export type ApiPayload = Record<string, unknown>

/**
 * 解包业务信封：res.data 存在时返回业务数据，否则原样返回。
 * 输入放宽为 unknown：调用方可能传入业务信封、已解包数据或裸值；
 * 输出由 T 决定，缺省 ApiPayload，调用方可传入具体类型（如 PagedResponse<Course>）收窄。
 * @param res 请求结果（可为业务信封、已解包业务数据或裸数据）
 */
export function unwrap<T = ApiPayload>(res: unknown): T | null | undefined {
  if (res == null) return res as T | null | undefined
  const envelope = res as ApiEnvelope<T>
  return envelope.data !== undefined ? envelope.data : (res as T)
}

/**
 * 还原后端展示层转义（AIController 对 answer/sources 统一做了 HTML 转义）。
 *
 * 视图侧要么用 {{ }} 插值、要么经 renderMarkdown 再转义一次后 sanitize，
 * 因此这里的还原只用于展示，不会把内容重新注入 DOM。
 *
 * @param text 后端下发的已转义文本
 */
export function decodeHtml(text) {
  if (typeof text !== 'string') return ''
  return text
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&amp;/g, '&')
}

export default unwrap

/**
 * 从若干候选值中按优先级取第一个**非空数组**。
 *
 * 缺陷修复（P1-10）：`res.data?.list || res.data?.records || []` 这类「多字段兼容」
 * 写法有一个致命细节 —— **空数组 `[]` 是 truthy**。所以后端把数据放在 `records`、
 * 而 `list` 字段存在但为 `[]` 时，`||` 短路取到 `[]`，`records` 的真实数据被永久吞掉，
 * 界面显示为空列表且毫无报错。
 *
 * 本函数显式以「是不是非空数组」为判定条件，逐个候选检查，取到即返回。
 *
 * @param {...unknown} candidates 按优先级排列的候选值
 * @returns {Array} 第一个非空数组；全不满足时返回空数组
 */
export function firstNonEmptyArray(...candidates) {
  for (const c of candidates) {
    if (Array.isArray(c) && c.length > 0) return c
  }
  return []
}

/**
 * 判断候选中是否存在非空数组（用于分支判定，与 firstNonEmptyArray 同口径）。
 * @param {...unknown} candidates 候选值
 */
export function hasNonEmptyArray(...candidates) {
  return candidates.some((c) => Array.isArray(c) && c.length > 0)
}

/**
 * 从「分页信封或裸数组」业务数据中取出列表：records > list > items > 自身。
 *
 * 与 firstNonEmptyArray 同一判定口径（空数组不算命中），因此
 * `{ records: [...], list: [] }` 这类 MyBatis-Plus Page 与 PagedResponse 混用形状
 * 不会再被前面的空字段吞掉。
 *
 * @param {unknown} payload unwrap(res) 之后的业务数据
 * @returns {Array}
 */
export function pickList(payload) {
  return firstNonEmptyArray(payload?.records, payload?.list, payload?.items, payload)
}

/**
 * 读取分页总数：total > count > totalCount，均缺失（或非法）时用回退值。
 *
 * 缺陷修复（2026-10-04）：`pagination.total = rawData.length` 在响应为裸数组时会把
 * 「本页条数」当总数，分页控件因此只显示 1 页；反之直接取 `?? 0` 又会在后端不给
 * total 时把总数清零。这里显式区分两种情况，回退值由调用方决定。
 *
 * @param {unknown} payload unwrap(res) 之后的业务数据
 * @param {number} fallback 后端未提供总数时使用的回退值
 * @returns {number}
 */
export function readTotal(payload, fallback = 0) {
  const raw = payload?.total ?? payload?.count ?? payload?.totalCount
  const num = Number(raw)
  return Number.isFinite(num) && num >= 0 ? num : fallback
}

/**
 * 判断是否为分页信封（含 list/records/total 任一字段）。
 *
 * 用途：区分「后端返回了一个空分页信封」与「后端返回了单个实体对象」。
 * 前者应解析为空列表，后者才需要包成单元素数组——否则空信封会被包成幽灵节点。
 *
 * @param {unknown} payload unwrap 之后的业务数据
 */
export function isPageEnvelope(payload) {
  if (!payload || Array.isArray(payload) || typeof payload !== 'object') return false
  return (
    Array.isArray(payload.list) ||
    Array.isArray(payload.records) ||
    Array.isArray(payload.items) ||
    payload.total !== undefined
  )
}
