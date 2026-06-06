import request from './request'
export const getMyPaths = () => request.get('/learning-paths')
export const getPathById = id => request.get(`/learning-paths/${id}`)
export const getPathNodes = pathId => request.get(`/learning-paths/${pathId}/nodes`)
export const generatePath = courseId => request.get('/learning-paths/generate', { params: { courseId } })
export default { getMyPaths, getPathById, getPathNodes, generatePath }