import request from './request'
export const getSystemConfig = key => request.get('/admin/system/configs', { params: { key } })
export const updateSystemConfig = (key, value) => request.post('/admin/system/configs', { key, value })
export const getSystemLogs = params => request.get('/admin/system/logs', { params })
export const getSystemHealth = () => request.get('/admin/system/health')
export const createBackup = () => request.post('/admin/backup')
export const getBackupList = () => request.get('/admin/backup')
export default { getSystemConfig, updateSystemConfig, getSystemLogs, getSystemHealth, createBackup, getBackupList }
