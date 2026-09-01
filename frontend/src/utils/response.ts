/**
 * 统一响应解包工具。
 *
 * 响应拦截器（api/request.js）在请求成功时返回整个业务信封
 * { code, message, data, timestamp }，业务数据恒位于 res.data。
 * 历史上部分视图为兼容「裸响应」写了防御式双取值（res.data || res、
 * r?.data ?? r 等）。unwrap 将这些写法收敛为单一语义，集中维护。
 *
 * 适用：标准 ApiResponse 信封的一层解包。
 * 不适用：分页多字段兼容（res.data?.records || res.data?.list）或
 * 双层 data（res.data?.data）等接口特例，此类保留原写法。
 *
 * @template T 解包后的业务数据类型（缺省为 any，调用方可按上下文收窄）
 */

/** 标准业务信封（与后端 ApiResponse 对齐，供响应解包类型化使用） */
export interface ApiEnvelope<T = any> {
  code: number
  message: string
  data: T
  timestamp?: number
}

/**
 * 解包业务信封：res.data 存在时返回业务数据，否则原样返回。
 * 参数放宽为 any：api 模块（机械迁移未标返回类型）调用结果为 AxiosResponse 类型，
 * 与拦截器解包后的实际业务数据不一致；放行输入、由调用方按上下文收窄，避免
 * 误把 AxiosResponse 当业务类型推导（composables 迁移 .ts 时的主要错误来源）。
 * @param res 请求结果（可为业务信封、Axios 响应或裸数据）
 */
export function unwrap<T = any>(res: any): T | null | undefined {
  if (res == null) return res as T | null | undefined
  const envelope = res as ApiEnvelope<T>
  return envelope.data !== undefined ? envelope.data : (res as T)
}

export default unwrap
