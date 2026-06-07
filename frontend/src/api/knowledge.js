import request from './request'
export const getKnowledgePoints = courseId => request.get('/knowledge-points', { params: { courseId } })
export const createKnowledgePoint = data => request.post('/knowledge-points', data)
export const updateKnowledgePoint = (id, data) => request.put(`/knowledge-points/${id}`, data)
export const deleteKnowledgePoint = id => request.delete(`/knowledge-points/${id}`)
export const getKnowledgeGraph = courseId => request.get('/knowledge-points/graph', { params: { courseId } })
export const addRelation = data => request.post('/knowledge-points/relations', data)
export default { getKnowledgePoints, createKnowledgePoint, updateKnowledgePoint, deleteKnowledgePoint, getKnowledgeGraph, addRelation }