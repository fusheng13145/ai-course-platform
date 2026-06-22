import request from './request'

// 旧版知识图谱API（/knowledge-points）
export const getKnowledgePoints = courseId => request.get('/knowledge-points', { params: { courseId } })
export const createKnowledgePoint = data => request.post('/knowledge-points', data)
export const updateKnowledgePoint = (id, data) => request.put(`/knowledge-points/${id}`, data)
export const deleteKnowledgePoint = id => request.delete(`/knowledge-points/${id}`)
export const getKnowledgeGraph = courseId => request.get('/knowledge-points/graph', { params: { courseId } })
export const addRelation = data => request.post('/knowledge-points/relations', data)

// 知识点增强 API
export const getTree = courseId => request.get(`/knowledge-points/tree/${courseId}`)
export const getChildren = id => request.get(`/knowledge-points/${id}/children`)
export const getPrerequisites = id => request.get(`/knowledge-points/${id}/prerequisites`)
export const getSubsequent = id => request.get(`/knowledge-points/${id}/subsequent`)
export const getMastery = (pointId, userId) => request.get(`/knowledge-points/${pointId}/mastery/${userId}`)
export const getRootPoints = courseId => request.get(`/knowledge-points/root/${courseId}`)

// 新版知识图谱API（需求文档 /api/knowledge/*，request.js baseURL已包含/api）
export const getKnowledgePointsV2 = params => request.get('/knowledge/points', { params })
export const getKnowledgePointDetail = pointId => request.get(`/knowledge/points/${pointId}`)
export const createKnowledgePointV2 = data => request.post('/knowledge/points', data)
export const updateKnowledgePointV2 = (pointId, data) => request.put(`/knowledge/points/${pointId}`, data)
export const deleteKnowledgePointV2 = pointId => request.delete(`/knowledge/points/${pointId}`)
export const getPointRelations = pointId => request.get(`/knowledge/points/${pointId}/relations`)
export const getPointPrerequisites = pointId => request.get(`/knowledge/points/${pointId}/prerequisites`)
export const getPointSuccessors = pointId => request.get(`/knowledge/points/${pointId}/successors`)
export const createKnowledgeRelation = data => request.post('/knowledge/relations', data)
export const planKnowledgePath = data => request.post('/knowledge/path/plan', data)
export const searchKnowledge = params => request.get('/knowledge/search', { params })
export const getKnowledgeOverview = params => request.get('/knowledge/overview', { params })
export const extractKnowledgePoints = data => request.post('/knowledge/extract', data)

export default {
  getKnowledgePoints, createKnowledgePoint, updateKnowledgePoint, deleteKnowledgePoint,
  getKnowledgeGraph, addRelation, getTree, getChildren, getPrerequisites, getSubsequent,
  getMastery, getRootPoints,
  getKnowledgePointsV2, getKnowledgePointDetail, createKnowledgePointV2, updateKnowledgePointV2,
  deleteKnowledgePointV2, getPointRelations, getPointPrerequisites, getPointSuccessors,
  createKnowledgeRelation, planKnowledgePath, searchKnowledge, getKnowledgeOverview, extractKnowledgePoints
}