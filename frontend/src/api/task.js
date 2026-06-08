import request from './request'
export const getTaskList = params => request.get('/tasks', { params })
export const getTaskById = id => request.get(`/tasks/${id}`)
export const createTask = data => request.post('/tasks', data)
export const updateTask = (id, data) => request.put(`/tasks/${id}`, data)
export const deleteTask = id => request.delete(`/tasks/${id}`)
export const publishTask = id => request.post(`/tasks/${id}/publish`)

// 任务增强 API
export const extendDeadline = (id, data) => request.post(`/tasks/${id}/extend-deadline`, data)
export const copyTask = (id, data) => request.post(`/tasks/${id}/copy`, data)
export const getPendingGrading = teacherId => request.get('/tasks/pending-grading', { params: { teacherId } })
export const getCompletionStats = id => request.get(`/tasks/${id}/completion-stats`)

export default { getTaskList, getTaskById, createTask, updateTask, deleteTask, publishTask, extendDeadline, copyTask, getPendingGrading, getCompletionStats }