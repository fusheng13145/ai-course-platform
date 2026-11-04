import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

// 操作审计日志（管理员端，后端 /admin/operation-logs）
export const getOperationLogs = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/operation-logs', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getOperationLogStatistics：获取/查询数据
 */
export const getOperationLogStatistics = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/operation-logs/statistics') as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { getOperationLogs, getOperationLogStatistics }
