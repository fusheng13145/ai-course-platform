import request from './request'
export const getClassroomList = params => request.get('/classrooms', { params })
export const getClassroomById = id => request.get(`/classrooms/${id}`)
export const createClassroom = data => request.post('/classrooms', data)
export const updateClassroom = (id, data) => request.put(`/classrooms/${id}`, data)
export const deleteClassroom = id => request.delete(`/classrooms/${id}`)

// 班级管理增强 API
export const getDetail = id => request.get(`/classrooms/${id}/detail`)
export const getStudents = (id, params) => request.get(`/classrooms/${id}/students`, { params })
export const addStudent = (classroomId, studentId) => request.post(`/classrooms/${classroomId}/students/${studentId}`)
export const removeStudent = (classroomId, studentId) => request.delete(`/classrooms/${classroomId}/students/${studentId}`)
export const getStats = id => request.get(`/classrooms/${id}/stats`)
export const getMyClassrooms = advisorId => request.get('/classrooms/my', { params: { advisorId } })
export const search = params => request.get('/classrooms/search', { params })

export default { getClassroomList, getClassroomById, createClassroom, updateClassroom, deleteClassroom, getDetail, getStudents, addStudent, removeStudent, getStats, getMyClassrooms, search }