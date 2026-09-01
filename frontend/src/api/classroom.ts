import request from './request'
/**
 * 导出函数 getClassroomList：获取/查询数据
 */
export const getClassroomList = (params = {}) => request.get('/classrooms', { params })
/**
 * 导出函数 getClassroomById：获取/查询数据
 */
export const getClassroomById = id => request.get(`/classrooms/${id}`)
/**
 * 导出函数 createClassroom：创建/新增数据
 */
export const createClassroom = data => request.post('/classrooms', data)
/**
 * 导出函数 updateClassroom：更新数据
 */
export const updateClassroom = (id, data) => request.put(`/classrooms/${id}`, data)
/**
 * 导出函数 deleteClassroom：删除数据
 */
export const deleteClassroom = id => request.delete(`/classrooms/${id}`)

// 班级管理增强 API
export const getDetail = id => request.get(`/classrooms/${id}/detail`)
/**
 * 导出函数 getStudents：获取/查询数据
 */
export const getStudents = (id, params = {}) => request.get(`/classrooms/${id}/students`, { params })
/**
 * 导出函数 addStudent：创建/新增数据
 */
export const addStudent = (classroomId, studentId) => request.post(`/classrooms/${classroomId}/students/${studentId}`)
/**
 * 导出函数 removeStudent：删除数据
 */
export const removeStudent = (classroomId, studentId) => request.delete(`/classrooms/${classroomId}/students/${studentId}`)
/**
 * 导出函数 getStats：获取/查询数据
 */
export const getStats = id => request.get(`/classrooms/${id}/stats`)
/**
 * 导出函数 getMyClassrooms：获取/查询数据
 */
export const getMyClassrooms = advisorId => request.get('/classrooms/my', { params: { advisorId } })
/**
 * 导出函数 search：获取/查询数据
 */
export const search = (params = {}) => request.get('/classrooms/search', { params })

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { getClassroomList, getClassroomById, createClassroom, updateClassroom, deleteClassroom, getDetail, getStudents, addStudent, removeStudent, getStats, getMyClassrooms, search }