import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'
/**
 * 导出函数 getUserList：获取/查询数据
 */
export const getUserList = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/users', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getUserById：获取/查询数据
 */
export const getUserById = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/users/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 createUser：创建/新增数据
 */
export const createUser = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/users', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updateUser：更新数据
 */
export const updateUser = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/users/${id}`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 deleteUser：删除数据
 */
export const deleteUser = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/users/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updateUserStatus：更新数据
 */
export const updateUserStatus = (
  id: number | string,
  status: unknown,
): Promise<ApiEnvelope<ApiData>> =>
  request.patch(`/users/${id}/status`, { status }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getTeacherStudents：获取/查询数据
 */
export const getTeacherStudents = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/users/teacher/students') as Promise<ApiEnvelope<ApiData>>
// 以下导出由原 userEnhanced.js 合并而来
export const getProfile = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/users/${id}/profile`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getClassmates：获取/查询数据
 */
export const getClassmates = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/users/${id}/classmates`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getUserStats：获取/查询数据
 */
export const getUserStats = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/users/${id}/stats`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 searchUsers：获取/查询数据
 */
export const searchUsers = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/users/search', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getUserList,
  getUserById,
  createUser,
  updateUser,
  deleteUser,
  updateUserStatus,
  getTeacherStudents,
  getProfile,
  getClassmates,
  getUserStats,
  searchUsers,
}
