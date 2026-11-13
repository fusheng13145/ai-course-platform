import request from './request'
import { fetchAllPages, type ApiEnvelope, type ApiData } from '@/utils/response'
/** 获取课程列表 */
export const getCourseList = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/courses', { params }) as Promise<ApiEnvelope<ApiData>>
/** 获取全量课程目录（保留查询筛选参数，逐页读取完整结果）。 */
export const getAllCourses = (filters: Record<string, unknown> = {}): Promise<ApiData[]> =>
  fetchAllPages<ApiData>((page: number, size: number) => getCourseList({ ...filters, page, size }))
/** 根据ID获取课程 */
export const getCourseById = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/courses/${id}`) as Promise<ApiEnvelope<ApiData>>
/** 创建课程 */
export const createCourse = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/courses', data) as Promise<ApiEnvelope<ApiData>>
/** 更新课程 */
export const updateCourse = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/courses/${id}`, data) as Promise<ApiEnvelope<ApiData>>
/** 删除课程 */
export const deleteCourse = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/courses/${id}`) as Promise<ApiEnvelope<ApiData>>
/** 审核通过课程 */
export const approveCourse = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/courses/${id}/approve`) as Promise<ApiEnvelope<ApiData>>
/** 驳回课程（reason 可缺省：后端按空驳回意见处理） */
export const rejectCourse = (id: number | string, reason?: string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/courses/${id}/reject`, { reason }) as Promise<ApiEnvelope<ApiData>>
/** 切换课程状态 */
export const toggleCourseStatus = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.patch(`/courses/${id}/toggle`) as Promise<ApiEnvelope<ApiData>>
/** 获取我的课程 */
export const getMyCourses = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/courses/my', { params }) as Promise<ApiEnvelope<ApiData>>
/** 获取教师全部课程；课程管理与下拉选项不应被后端默认 10 条分页静默截断。 */
export const getAllMyCourses = (): Promise<ApiData[]> =>
  fetchAllPages<ApiData>((page: number, size: number) => getMyCourses({ page, size }))
/** 获取已选课程 */
export const getEnrolledCourses = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/courses/enrolled', { params }) as Promise<ApiEnvelope<ApiData>>
/** 获取课程学生列表 */
export const getStudents = (
  id: number | string,
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/courses/${id}/students`, { params }) as Promise<ApiEnvelope<ApiData>>
/** 获取课程全部学生报名记录，供需要精确全班统计的页面使用。 */
export const getAllStudents = (
  id: number | string,
  filters: Record<string, unknown> = {},
): Promise<ApiData[]> =>
  fetchAllPages<ApiData>((page: number, size: number) =>
    getStudents(id, { ...filters, page, size }),
  )
/** 获取课程统计数据 */
export const getStats = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/courses/${id}/stats`) as Promise<ApiEnvelope<ApiData>>
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
