import request from './request'
export const getClassroomList = params => request.get('/classrooms', { params })
export const getClassroomById = id => request.get(`/classrooms/${id}`)
export const createClassroom = data => request.post('/classrooms', data)
export const updateClassroom = (id, data) => request.put(`/classrooms/${id}`, data)
export const deleteClassroom = id => request.delete(`/classrooms/${id}`)
export default { getClassroomList, getClassroomById, createClassroom, updateClassroom, deleteClassroom }