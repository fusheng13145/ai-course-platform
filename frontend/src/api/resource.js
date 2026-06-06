import request from './request'
export const getResourceList = params => request.get('/resources', { params })
export const uploadResource = (chapterId, data) => request.post(`/chapters/${chapterId}/resources`, data, { headers: { "Content-Type": "multipart/form-data" } })
export const downloadResource = id => request.get(`/resources/${id}/download`, { responseType: "blob" })
export const deleteResource = id => request.delete(`/resources/${id}`)
export const getResourceVersions = id => request.get(`/resources/${id}/versions`)
export default { getResourceList, uploadResource, downloadResource, deleteResource, getResourceVersions }