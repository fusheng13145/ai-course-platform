import request from './request'

// 后端 KnowledgeController @RequestMapping("/knowledge")，知识点端点合并为 /knowledge/points
export const getKnowledgePoints = courseId => request.get('/knowledge/points', { params: { courseId } })
/**
 * 导出函数 createKnowledgePoint：创建/新增数据
 */
export const createKnowledgePoint = data => request.post('/knowledge/points', data)
/**
 * 导出函数 updateKnowledgePoint：更新数据
 */
export const updateKnowledgePoint = (id, data) => request.put(`/knowledge/points/${id}`, data)
/**
 * 导出函数 deleteKnowledgePoint：删除数据
 */
export const deleteKnowledgePoint = id => request.delete(`/knowledge/points/${id}`)
/**
 * 导出函数 getKnowledgeGraph：获取/查询数据
 */
export const getKnowledgeGraph = courseId => request.get('/knowledge/points/graph', { params: { courseId } })
/**
 * 导出函数 addRelation：创建/新增数据
 */
export const addRelation = data => request.post('/knowledge/relations', data)

// 知识点增强 API
export const getTree = courseId => request.get(`/knowledge/points/tree/${courseId}`)
/**
 * 导出函数 getChildren：获取/查询数据
 */
export const getChildren = id => request.get(`/knowledge/points/${id}/children`)
/**
 * 导出函数 getPrerequisites：获取/查询数据
 */
export const getPrerequisites = id => request.get(`/knowledge/points/${id}/prerequisites`)
/**
 * 导出函数 getSubsequent：获取/查询数据
 */
export const getSubsequent = id => request.get(`/knowledge/points/${id}/successors`)
/**
 * 导出函数 getMastery：获取/查询数据
 */
export const getMastery = (pointId, userId) => request.get(`/knowledge/points/${pointId}/mastery/${userId}`)
/**
 * 导出函数 getRootPoints：获取/查询数据
 */
export const getRootPoints = courseId => request.get(`/knowledge/points/root/${courseId}`)

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { getKnowledgePoints, createKnowledgePoint, updateKnowledgePoint, deleteKnowledgePoint, getKnowledgeGraph, addRelation, getTree, getChildren, getPrerequisites, getSubsequent, getMastery, getRootPoints }