import request from './request'
/**
 * 导出函数 getMyPaths：获取/查询数据
 */
export const getMyPaths = () => request.get('/learning-paths')
/**
 * 导出函数 createPath：创建/新增数据
 */
export const createPath = data => request.post('/learning-paths', data)
/**
 * 导出函数 getPathById：获取/查询数据
 */
export const getPathById = id => request.get(`/learning-paths/${id}`)
/**
 * 导出函数 updatePath：更新数据
 */
export const updatePath = (id, data) => request.put(`/learning-paths/${id}`, data)
/**
 * 导出函数 getPathNodes：获取/查询数据
 */
export const getPathNodes = pathId => request.get(`/learning-paths/${pathId}/nodes`)
/**
 * 导出函数 addPathNode：创建/新增数据
 */
export const addPathNode = (pathId, data) => request.post(`/learning-paths/${pathId}/nodes`, data)
/**
 * 导出函数 updatePathNode：更新数据
 */
export const updatePathNode = (nodeId, data) => request.put(`/learning-paths/nodes/${nodeId}`, data)
/**
 * 导出函数 generatePath：业务逻辑处理
 */
export const generatePath = courseId => request.get('/learning-paths/generate', { params: { courseId } })

// 学习路径增强 API
export const getActivePath = courseId => request.get(`/learning-paths/active/${courseId}`)
/**
 * 导出函数 advancePath：业务逻辑处理
 */
export const advancePath = id => request.post(`/learning-paths/${id}/advance`)
/**
 * 导出函数 completePath：业务逻辑处理
 */
export const completePath = id => request.post(`/learning-paths/${id}/complete`)
/**
 * 导出函数 pausePath：业务逻辑处理
 */
export const pausePath = id => request.post(`/learning-paths/${id}/pause`)
/**
 * 导出函数 resumePath：业务逻辑处理
 */
export const resumePath = id => request.post(`/learning-paths/${id}/resume`)
/**
 * 导出函数 getDailyTasks：获取/查询数据
 */
export const getDailyTasks = id => request.get(`/learning-paths/${id}/daily-tasks`)
/**
 * 导出函数 getPathDetail：获取/查询数据
 */
export const getPathDetail = id => request.get(`/learning-paths/${id}/detail`)
/**
 * 导出函数 getPathProgress：获取/查询数据
 */
export const getPathProgress = id => request.get(`/learning-paths/${id}/progress`)

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { getMyPaths, createPath, getPathById, updatePath, getPathNodes, addPathNode, updatePathNode, generatePath, getActivePath, advancePath, completePath, pausePath, resumePath, getDailyTasks, getPathDetail, getPathProgress }