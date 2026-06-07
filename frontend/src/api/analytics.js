import request from './request'
export const getDashboardStats = params => request.get('/analytics/dashboard', { params })
export const getCourseAnalytics = courseId => request.get(`/analytics/courses/${courseId}`)
export const getStudentAnalytics = userId => request.get(`/analytics/students/${userId}`)
export const getTrendData = userId => request.get(`/analytics/trends`, { params: { userId } })
export default { getDashboardStats, getCourseAnalytics, getStudentAnalytics, getTrendData }