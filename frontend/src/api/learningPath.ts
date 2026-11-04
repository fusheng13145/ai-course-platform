import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'
/**
 * 导出函数 getMyPaths：获取/查询数据
 */
export const getMyPaths = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/learning-paths') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 createPath：创建/新增数据
 */
export const createPath = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/learning-paths', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getPathById：获取/查询数据
 */
export const getPathById = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/learning-paths/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updatePath：更新数据
 */
export const updatePath = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/learning-paths/${id}`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getPathNodes：获取/查询数据
 */
export const getPathNodes = (pathId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/learning-paths/${pathId}/nodes`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 addPathNode：创建/新增数据
 */
export const addPathNode = (
  pathId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/learning-paths/${pathId}/nodes`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updatePathNode：更新数据
 */
export const updatePathNode = (
  nodeId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/learning-paths/nodes/${nodeId}`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 generatePath：业务逻辑处理
 */
export const generatePath = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get('/learning-paths/generate', { params: { courseId } }) as Promise<ApiEnvelope<ApiData>>

// 学习路径增强 API
export const getActivePath = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/learning-paths/active/${courseId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 advancePath：业务逻辑处理
 */
export const advancePath = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/learning-paths/${id}/advance`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 completePath：业务逻辑处理
 */
export const completePath = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/learning-paths/${id}/complete`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 pausePath：业务逻辑处理
 */
export const pausePath = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/learning-paths/${id}/pause`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 resumePath：业务逻辑处理
 */
export const resumePath = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/learning-paths/${id}/resume`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getDailyTasks：获取/查询数据
 */
export const getDailyTasks = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/learning-paths/${id}/daily-tasks`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getPathDetail：获取/查询数据
 */
export const getPathDetail = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/learning-paths/${id}/detail`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getPathProgress：获取/查询数据
 */
export const getPathProgress = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/learning-paths/${id}/progress`) as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getMyPaths,
  createPath,
  getPathById,
  updatePath,
  getPathNodes,
  addPathNode,
  updatePathNode,
  generatePath,
  getActivePath,
  advancePath,
  completePath,
  pausePath,
  resumePath,
  getDailyTasks,
  getPathDetail,
  getPathProgress,
}
