import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

// 版本历史
export const getVersionHistory = (resourceId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/resource-versions/resource/${resourceId}`) as Promise<ApiEnvelope<ApiData>>
// 创建新版本
export const createVersion = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/resource-versions', data) as Promise<ApiEnvelope<ApiData>>
// 回滚版本
export const rollbackVersion = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/resource-versions/${id}/rollback`) as Promise<ApiEnvelope<ApiData>>
// 当前版本
export const getActiveVersion = (resourceId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/resource-versions/active/${resourceId}`) as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { getVersionHistory, createVersion, rollbackVersion, getActiveVersion }
