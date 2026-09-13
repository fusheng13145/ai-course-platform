/**
 * 统一响应解包工具。
 *
 * 响应拦截器（api/request.ts）在请求成功时返回已解包的业务数据，
 * 即 { code, message, data } 信封中的 data 部分。
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

export default unwrap
