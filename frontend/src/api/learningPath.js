import request from './request'
export const getMyPaths = () => request.get('/learning-paths')
export const createPath = data => request.post('/learning-paths', data)
export const getPathById = id => request.get(`/learning-paths/${id}`)
export const updatePath = (id, data) => request.put(`/learning-paths/${id}`, data)
export const getPathNodes = pathId => request.get(`/learning-paths/${pathId}/nodes`)
export const addPathNode = (pathId, data) => request.post(`/learning-paths/${pathId}/nodes`, data)
export const updatePathNode = (nodeId, data) => request.put(`/learning-paths/nodes/${nodeId}`, data)
export const generatePath = courseId => request.get('/learning-paths/generate', { params: { courseId } })
export default { getMyPaths, createPath, getPathById, updatePath, getPathNodes, addPathNode, updatePathNode, generatePath }