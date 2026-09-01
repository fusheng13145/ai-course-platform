import request from './request'
/**
 * 导出函数 getResourceList：获取/查询数据
 */
export const getResourceList = courseId => request.get(`/resources/course/${courseId}`)
/**
 * 导出函数 getResourceById：获取/查询数据
 */
export const getResourceById = id => request.get(`/resources/${id}`)
/**
 * 导出函数 uploadResource：创建/新增数据
 */
export const uploadResource = data => request.post('/resources/upload', data, { headers: { "Content-Type": "multipart/form-data" } })
/**
 * 导出函数 downloadResource：业务逻辑处理
 */
export const downloadResource = id => request.get(`/resources/download/${id}`, { responseType: "blob" })
/**
 * 导出函数 deleteResource：删除数据
 */
export const deleteResource = id => request.delete(`/resources/${id}`)

// 资源管理增强 API
export const getResourceTree = courseId => request.get(`/resources/tree/${courseId}`)
/**
 * 导出函数 searchResources：获取/查询数据
 */
export const searchResources = (params = {}) => request.get('/resources/search', { params })
/**
 * 导出函数 getResourceStats：获取/查询数据
 */
export const getResourceStats = courseId => request.get(`/resources/stats/course/${courseId}`)
/**
 * 导出函数 toggleVisibility：业务逻辑处理
 */
export const toggleVisibility = (id, visible) => request.patch(`/resources/${id}/visibility`, null, { params: { visibleToAll: visible } })
/**
 * 导出函数 getChapterResources：获取/查询数据
 */
export const getChapterResources = chapterId => request.get(`/resources/chapter/${chapterId}`)

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { getResourceList, getResourceById, uploadResource, downloadResource, deleteResource, getResourceTree, searchResources, getResourceStats, toggleVisibility, getChapterResources }