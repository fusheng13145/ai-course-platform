import request from './request'
/** 获取课程列表 */
export const getCourseList = (params = {}) => request.get('/courses', { params })
/** 根据ID获取课程 */
export const getCourseById = (id) => request.get(`/courses/${id}`)
/** 创建课程 */
export const createCourse = (data) => request.post('/courses', data)
/** 更新课程 */
export const updateCourse = (id, data) => request.put(`/courses/${id}`, data)
/** 删除课程 */
export const deleteCourse = (id) => request.delete(`/courses/${id}`)
/** 审核通过课程 */
export const approveCourse = (id) => request.post(`/courses/${id}/approve`)
/** 驳回课程 */
export const rejectCourse = (id, reason) => request.post(`/courses/${id}/reject`, { reason })
/** 切换课程状态 */
export const toggleCourseStatus = (id) => request.patch(`/courses/${id}/toggle`)
/** 获取我的课程 */
export const getMyCourses = (params = {}) => request.get('/courses/my', { params })
/** 获取已选课程 */
export const getEnrolledCourses = (params = {}) => request.get('/courses/enrolled', { params })
/** 获取课程学生列表 */
export const getStudents = (id, params = {}) => request.get(`/courses/${id}/students`, { params })
/** 获取课程统计数据 */
export const getStats = (id) => request.get(`/courses/${id}/stats`)
/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getCourseList,
  getCourseById,
  createCourse,
  updateCourse,
  deleteCourse,
  approveCourse,
  rejectCourse,
  toggleCourseStatus,
  getMyCourses,
  getEnrolledCourses,
  getStudents,
  getStats,
}
