import request from './request'
export const getResourceList = courseId => request.get(`/resources/course/${courseId}`)
export const getResourceById = id => request.get(`/resources/${id}`)
export const uploadResource = data => request.post('/resources/upload', data, { headers: { "Content-Type": "multipart/form-data" } })
export const downloadResource = id => request.get(`/resources/download/${id}`, { responseType: "blob" })
export const deleteResource = id => request.delete(`/resources/${id}`)
export default { getResourceList, getResourceById, uploadResource, downloadResource, deleteResource }