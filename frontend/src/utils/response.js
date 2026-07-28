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
 */

export function unwrap(res) {
  if (res == null) return res
  return res.data !== undefined ? res.data : res
}

export default unwrap
