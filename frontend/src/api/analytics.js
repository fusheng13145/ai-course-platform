import request from './request'
export const getDashboardStats = params => request.get('/analytics/dashboard', { params })
export const getCourseAnalytics = courseId => request.get(`/analytics/courses/${courseId}`)
export const getStudentAnalytics = studentId => request.get(`/analytics/students/${studentId}`)
export const getTrendData = (type, days) => request.get('/analytics/trends', { params: { type, days } })
export default { getDashboardStats, getCourseAnalytics, getStudentAnalytics, getTrendData }