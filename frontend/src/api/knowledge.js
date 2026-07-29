import request from './request'

// 后端 KnowledgeController @RequestMapping("/knowledge")，知识点端点合并为 /knowledge/points
export const getKnowledgePoints = courseId => request.get('/knowledge/points', { params: { courseId } })
export const createKnowledgePoint = data => request.post('/knowledge/points', data)
export const updateKnowledgePoint = (id, data) => request.put(`/knowledge/points/${id}`, data)
export const deleteKnowledgePoint = id => request.delete(`/knowledge/points/${id}`)
export const getKnowledgeGraph = courseId => request.get('/knowledge/points/graph', { params: { courseId } })
export const addRelation = data => request.post('/knowledge/relations', data)

// 知识点增强 API
export const getTree = courseId => request.get(`/knowledge/points/tree/${courseId}`)
export const getChildren = id => request.get(`/knowledge/points/${id}/children`)
export const getPrerequisites = id => request.get(`/knowledge/points/${id}/prerequisites`)
export const getSubsequent = id => request.get(`/knowledge/points/${id}/successors`)
export const getMastery = (pointId, userId) => request.get(`/knowledge/points/${pointId}/mastery/${userId}`)
export const getRootPoints = courseId => request.get(`/knowledge/points/root/${courseId}`)

export default { getKnowledgePoints, createKnowledgePoint, updateKnowledgePoint, deleteKnowledgePoint, getKnowledgeGraph, addRelation, getTree, getChildren, getPrerequisites, getSubsequent, getMastery, getRootPoints }