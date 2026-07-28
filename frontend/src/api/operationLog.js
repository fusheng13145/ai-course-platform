import request from './request'

// 操作审计日志（管理员端，后端 /admin/operation-logs）
export const getOperationLogs = (params) => request.get('/admin/operation-logs', { params })
export const getOperationLogStatistics = () => request.get('/admin/operation-logs/statistics')

export default { getOperationLogs, getOperationLogStatistics }
