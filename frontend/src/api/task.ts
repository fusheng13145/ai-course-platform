import request from './request'

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
