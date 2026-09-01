import request from './request'

// 学习记录基础 API
export const createLearningRecord = data => request.post('/learning-records', data)
/**
 * 导出函数 getMyLearningRecords：获取/查询数据
 */
export const getMyLearningRecords = () => request.get('/learning-records')

// 学习记录增强 API
export const getLearningStats = () => request.get('/learning-records/stats')
/**
 * 导出函数 getHeatmap：获取/查询数据
 */
export const getHeatmap = (params = {}) => request.get('/learning-records/calendar', { params })

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { createLearningRecord, getMyLearningRecords, getLearningStats, getHeatmap }