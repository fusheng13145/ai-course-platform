import request from './request'
export const getKnowledgePoints = courseId => request.get(`/courses/${courseId}/knowledge-points`)
export const createKnowledgePoint = (courseId, data) => request.post(`/courses/${courseId}/knowledge-points`, data)
export const updateKnowledgePoint = (id, data) => request.put(`/knowledge-points/${id}`, data)
export const deleteKnowledgePoint = id => request.delete(`/knowledge-points/${id}`)
export const getKnowledgeGraph = courseId => request.get(`/knowledge-points/graph/${courseId}`)
export const addRelation = data => request.post('/knowledge-points/relations', data)
export default { getKnowledgePoints, createKnowledgePoint, updateKnowledgePoint, deleteKnowledgePoint, getKnowledgeGraph, addRelation }