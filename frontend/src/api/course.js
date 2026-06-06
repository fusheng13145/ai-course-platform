import request from './request'
export const getCourseList = params => request.get('/courses', { params })
export const getCourseById = id => request.get(`/courses/${id}`)
export const createCourse = data => request.post('/courses', data)
export const updateCourse = (id, data) => request.put(`/courses/${id}`, data)
export const deleteCourse = id => request.delete(`/courses/${id}`)
export const approveCourse = id => request.post(`/courses/${id}/approve`)
export const rejectCourse = (id, reason) => request.post(`/courses/${id}/reject`, { reason })
export const toggleCourseStatus = id => request.patch(`/courses/${id}/toggle`)
export default { getCourseList, getCourseById, createCourse, updateCourse, deleteCourse, approveCourse, rejectCourse, toggleCourseStatus }