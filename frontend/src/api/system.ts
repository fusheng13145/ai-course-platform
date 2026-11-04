import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'
/**
 * 导出函数 getSystemConfig：获取/查询数据
 */
export const getSystemConfig = (group: unknown): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/system/configs', { params: { group } }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updateSystemConfig：更新数据
 */
export const updateSystemConfig = (
  key: unknown,
  value: unknown,
  options: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/admin/system/configs', { key, value, ...options }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getSystemLogs：获取/查询数据
 */
export const getSystemLogs = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/system/logs', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getSystemHealth：获取/查询数据
 */
export const getSystemHealth = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/system/health') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 createBackup：创建/新增数据
 */
export const createBackup = (): Promise<ApiEnvelope<ApiData>> =>
  request.post('/admin/backup') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getBackupList：获取/查询数据
 */
export const getBackupList = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/backup') as Promise<ApiEnvelope<ApiData>>
/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getSystemConfig,
  updateSystemConfig,
  getSystemLogs,
  getSystemHealth,
  createBackup,
  getBackupList,
}
