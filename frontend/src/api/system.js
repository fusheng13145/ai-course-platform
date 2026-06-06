import request from './request'
export const getSystemConfig = key => request.get('/system/config', { params: { key } })
export const updateSystemConfig = (key, value) => request.put('/system/config', { key, value })
export const getSystemLogs = params => request.get('/system/logs', { params })
export const getSystemHealth = () => request.get('/system/health')
export const createBackup = () => request.post('/system/backup')
export const getBackupList = () => request.get('/system/backups')
export default { getSystemConfig, updateSystemConfig, getSystemLogs, getSystemHealth, createBackup, getBackupList }