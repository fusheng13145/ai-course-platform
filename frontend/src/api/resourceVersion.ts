import request from './request'

// 版本历史
export const getVersionHistory = (resourceId) =>
  request.get(`/resource-versions/resource/${resourceId}`)
// 创建新版本
export const createVersion = (data) => request.post('/resource-versions', data)
// 回滚版本
export const rollbackVersion = (id) => request.post(`/resource-versions/${id}/rollback`)
// 当前版本
export const getActiveVersion = (resourceId) =>
  request.get(`/resource-versions/active/${resourceId}`)

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { getVersionHistory, createVersion, rollbackVersion, getActiveVersion }
