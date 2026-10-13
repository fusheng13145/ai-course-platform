import request from './request'
import { fetchAllPages } from '@/utils/response'
/** 获取课程列表 */
export const getCourseList = (params = {}) => request.get('/courses', { params })
/** 获取全量课程目录（保留查询筛选参数，逐页读取完整结果）。 */
export const getAllCourses = (filters = {}) =>
  fetchAllPages((page, size) => getCourseList({ ...filters, page, size }))
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
/** 获取教师全部课程；课程管理与下拉选项不应被后端默认 10 条分页静默截断。 */
export const getAllMyCourses = () => fetchAllPages((page, size) => getMyCourses({ page, size }))
/** 获取已选课程 */
export const getEnrolledCourses = (params = {}) => request.get('/courses/enrolled', { params })
/** 获取课程学生列表 */
export const getStudents = (id, params = {}) => request.get(`/courses/${id}/students`, { params })
/** 获取课程全部学生报名记录，供需要精确全班统计的页面使用。 */
export const getAllStudents = (id, filters = {}) =>
  fetchAllPages((page, size) => getStudents(id, { ...filters, page, size }))
/** 获取课程统计数据 */
export const getStats = (id) => request.get(`/courses/${id}/stats`)
/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getCourseList,
  getAllCourses,
  getAllStudents,
  getCourseById,
  createCourse,
  updateCourse,
  deleteCourse,
  approveCourse,
  rejectCourse,
  toggleCourseStatus,
  getMyCourses,
  getAllMyCourses,
  getEnrolledCourses,
  getStudents,
  getStats,
}
