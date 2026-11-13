import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

// 学习记录基础 API
export const createLearningRecord = (
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/learning-records', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getMyLearningRecords：获取/查询数据
 */
export const getMyLearningRecords = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/learning-records') as Promise<ApiEnvelope<ApiData>>

// 学习记录增强 API
export const getLearningStats = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/learning-records/stats') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getHeatmap：获取/查询数据
 */
export const getHeatmap = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/learning-records/calendar', { params }) as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { createLearningRecord, getMyLearningRecords, getLearningStats, getHeatmap }
