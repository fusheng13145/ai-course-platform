import request from './request'
/**
 * 导出函数 getSystemConfig：获取/查询数据
 */
export const getSystemConfig = (group) =>
  request.get('/admin/system/configs', { params: { group } })
/**
 * 导出函数 updateSystemConfig：更新数据
 */
export const updateSystemConfig = (key, value, options = {}) =>
  request.post('/admin/system/configs', { key, value, ...options })
/**
 * 导出函数 getSystemLogs：获取/查询数据
 */
export const getSystemLogs = (params) => request.get('/admin/system/logs', { params })
/**
 * 导出函数 getSystemHealth：获取/查询数据
 */
export const getSystemHealth = () => request.get('/admin/system/health')
/**
 * 导出函数 createBackup：创建/新增数据
 */
export const createBackup = () => request.post('/admin/backup')
/**
 * 导出函数 getBackupList：获取/查询数据
 */
export const getBackupList = () => request.get('/admin/backup')
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
