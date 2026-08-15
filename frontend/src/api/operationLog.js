import request from './request'

// 操作审计日志（管理员端，后端 /admin/operation-logs）
export const getOperationLogs = (params) => request.get('/admin/operation-logs', { params })
/**
 * 导出函数 getOperationLogStatistics：获取/查询数据
 */
export const getOperationLogStatistics = () => request.get('/admin/operation-logs/statistics')

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { getOperationLogs, getOperationLogStatistics }
