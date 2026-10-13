import request from './request'
import { fetchAllPages } from '@/utils/response'

/** 任务实体（后端 Task） */
export interface Task {
  id: number
  courseId: number
  title: string
  description?: string
  status: string
  deadline?: string
  totalScore?: number
  createdAt?: string
  updatedAt?: string
  [key: string]: string | number | undefined
}

/** 任务查询参数 */
export interface TaskQuery extends PageParams {
  courseId?: number
  keyword?: string
  status?: string
}

/**
 * 导出函数 getTaskList：获取/查询数据
 */
export const getTaskList = (params?: TaskQuery) => request.get('/tasks', { params })
/** 获取满足筛选条件的全部任务，供任务目录/聚合统计使用。 */
export const getAllTasks = (filters: Omit<TaskQuery, 'page' | 'size'> = {}) =>
  fetchAllPages((page, size) => getTaskList({ ...filters, page, size }))
/**
 * 按课程分页查询任务（后端 GET /tasks/course/{courseId}）。
 * CourseTaskPanel 一直按这个名字调用，此前 api 层缺该导出，面板取任务列表必然 TypeError。
 */
export const listByCourse = (courseId: number, params?: PageParams) =>
  request.get(`/tasks/course/${courseId}`, { params })
/**
 * 导出函数 getTaskById：获取/查询数据
 */
export const getTaskById = (id: number) => request.get(`/tasks/${id}`)
/**
 * 导出函数 createTask：创建/新增数据
 */
export const createTask = (data: Partial<Task>) => request.post('/tasks', data)
/**
 * 导出函数 updateTask：更新数据
 */
export const updateTask = (id: number, data: Partial<Task>) => request.put(`/tasks/${id}`, data)
/**
 * 导出函数 deleteTask：删除数据
 */
export const deleteTask = (id: number) => request.delete(`/tasks/${id}`)
/**
 * 导出函数 publishTask：业务逻辑处理
 */
export const publishTask = (id: number) => request.post(`/tasks/${id}/publish`)

// 任务增强 API
export const extendDeadline = (id: number, data: { deadline: string }) =>
  request.post(`/tasks/${id}/extend-deadline`, data)
/**
 * 导出函数 copyTask：业务逻辑处理
 */
export const copyTask = (id: number, data?: Record<string, unknown>) =>
  request.post(`/tasks/${id}/copy`, data)
/**
 * 导出函数 getPendingGrading：获取/查询数据
 */
export const getPendingGrading = (teacherId?: number) =>
  request.get('/tasks/pending-grading', { params: { teacherId } })
/**
 * 导出函数 getCompletionStats：获取/查询数据
 */
export const getCompletionStats = (id: number) => request.get(`/tasks/${id}/completion-stats`)

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getTaskList,
  getAllTasks,
  listByCourse,
  getTaskById,
  createTask,
  updateTask,
  deleteTask,
  publishTask,
  extendDeadline,
  copyTask,
  getPendingGrading,
  getCompletionStats,
}
