import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'
/**
 * 导出函数 getRolePermissions：获取/查询数据
 */
export const getRolePermissions = (role: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/admin/permissions/role/${role}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getAllPermissions：获取/查询数据
 */
export const getAllPermissions = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/permissions/all') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 createPermission：创建/新增数据
 */
export const createPermission = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/admin/permissions/create', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updatePermission：更新数据
 */
export const updatePermission = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/admin/permissions/update/${id}`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 deletePermission：删除数据
 */
export const deletePermission = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/admin/permissions/delete/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 togglePermission：业务逻辑处理
 */
export const togglePermission = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.patch(`/admin/permissions/toggle/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getRolePermissions,
  getAllPermissions,
  createPermission,
  updatePermission,
  deletePermission,
  togglePermission,
}
