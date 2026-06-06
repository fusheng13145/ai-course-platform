import request from './request'
export const getClassroomList = params => request.get('/classrooms', { params })
export const getClassroomById = id => request.get(`/classrooms/${id}`)
export const createClassroom = data => request.post('/classrooms', data)
export const updateClassroom = (id, data) => request.put(`/classrooms/${id}`, data)
export const deleteClassroom = id => request.delete(`/classrooms/${id}`)
export const getClassroomStudents = id => request.get(`/classrooms/${id}/students`)
export const addStudent = (id, studentId) => request.post(`/classrooms/${id}/students`, { studentId })
export const removeStudent = (id, studentId) => request.delete(`/classrooms/${id}/students/${studentId}`)
export default { getClassroomList, getClassroomById, createClassroom, updateClassroom, deleteClassroom, getClassroomStudents, addStudent, removeStudent }