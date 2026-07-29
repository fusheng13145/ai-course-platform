import request from './request'

// 版本历史
export const getVersionHistory = resourceId => request.get(`/resource-versions/resource/${resourceId}`)
// 创建新版本
export const createVersion = data => request.post('/resource-versions', data)
// 回滚版本
export const rollbackVersion = id => request.post(`/resource-versions/${id}/rollback`)
// 当前版本
export const getActiveVersion = resourceId => request.get(`/resource-versions/active/${resourceId}`)

export default { getVersionHistory, createVersion, rollbackVersion, getActiveVersion }
