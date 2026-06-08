import request from './request'
export const getMyPaths = () => request.get('/learning-paths')
export const createPath = data => request.post('/learning-paths', data)
export const getPathById = id => request.get(`/learning-paths/${id}`)
export const updatePath = (id, data) => request.put(`/learning-paths/${id}`, data)
export const getPathNodes = pathId => request.get(`/learning-paths/${pathId}/nodes`)
export const addPathNode = (pathId, data) => request.post(`/learning-paths/${pathId}/nodes`, data)
export const updatePathNode = (nodeId, data) => request.put(`/learning-paths/nodes/${nodeId}`, data)
export const generatePath = courseId => request.get('/learning-paths/generate', { params: { courseId } })

// 学习路径增强 API
export const getActivePath = courseId => request.get(`/learning-paths/active/${courseId}`)
export const advancePath = id => request.post(`/learning-paths/${id}/advance`)
export const completePath = id => request.post(`/learning-paths/${id}/complete`)
export const pausePath = id => request.post(`/learning-paths/${id}/pause`)
export const resumePath = id => request.post(`/learning-paths/${id}/resume`)
export const getDailyTasks = id => request.get(`/learning-paths/${id}/daily-tasks`)
export const getPathDetail = id => request.get(`/learning-paths/${id}/detail`)
export const getPathProgress = id => request.get(`/learning-paths/${id}/progress`)

export default { getMyPaths, createPath, getPathById, updatePath, getPathNodes, addPathNode, updatePathNode, generatePath, getActivePath, advancePath, completePath, pausePath, resumePath, getDailyTasks, getPathDetail, getPathProgress }