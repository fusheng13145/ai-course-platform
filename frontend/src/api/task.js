import request from './request'
export const getTaskList = params => request.get('/tasks', { params })
export const getTaskById = id => request.get(`/tasks/${id}`)
export const createTask = data => request.post('/tasks', data)
export const updateTask = (id, data) => request.put(`/tasks/${id}`, data)
export const deleteTask = id => request.delete(`/tasks/${id}`)
export const publishTask = id => request.post(`/tasks/${id}/publish`)
export default { getTaskList, getTaskById, createTask, updateTask, deleteTask, publishTask }