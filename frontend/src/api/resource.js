import request from './request'
export const getResourceList = courseId => request.get(`/resources/course/${courseId}`)
export const getResourceById = id => request.get(`/resources/${id}`)
export const uploadResource = data => request.post('/resources/upload', data, { headers: { "Content-Type": "multipart/form-data" } })
export const downloadResource = id => request.get(`/resources/download/${id}`, { responseType: "blob" })
export const deleteResource = id => request.delete(`/resources/${id}`)

// 资源管理增强 API
export const getResourceTree = courseId => request.get(`/resources/tree/${courseId}`)
export const searchResources = params => request.get('/resources/search', { params })
export const getResourceStats = courseId => request.get(`/resources/stats/course/${courseId}`)
export const toggleVisibility = (id, visible) => request.patch(`/resources/${id}/visibility`, null, { params: { visibleToAll: visible } })
export const getChapterResources = chapterId => request.get(`/resources/chapter/${chapterId}`)

export default { getResourceList, getResourceById, uploadResource, downloadResource, deleteResource, getResourceTree, searchResources, getResourceStats, toggleVisibility, getChapterResources }