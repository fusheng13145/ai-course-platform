import request from './request'
import { fetchAllPages, type ApiEnvelope, type ApiData } from '@/utils/response'

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
export const getTaskList = (params?: TaskQuery): Promise<ApiEnvelope<ApiData>> =>
  request.get('/tasks', { params }) as Promise<ApiEnvelope<ApiData>>
/** 获取满足筛选条件的全部任务，供任务目录/聚合统计使用。 */
export const getAllTasks = (filters: Omit<TaskQuery, 'page' | 'size'> = {}): Promise<ApiData[]> =>
  fetchAllPages((page: number, size: number) => getTaskList({ ...filters, page, size })) as Promise<
    ApiData[]
  >
/**
 * 按课程分页查询任务（后端 GET /tasks/course/{courseId}）。
 * CourseTaskPanel 一直按这个名字调用，此前 api 层缺该导出，面板取任务列表必然 TypeError。
 */
export const listByCourse = (
  courseId: number,
  params?: PageParams,
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/tasks/course/${courseId}`, { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getTaskById：获取/查询数据
 */
export const getTaskById = (id: number): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/tasks/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 createTask：创建/新增数据
 * <p>body 为后端 TaskDTO 形态（taskType/dueDate 等，与前端 Task 实体不同形），保持未建模口径。</p>
 */
export const createTask = (data: ApiData): Promise<ApiEnvelope<ApiData>> =>
  request.post('/tasks', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updateTask：更新数据
 */
export const updateTask = (id: number, data: ApiData): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/tasks/${id}`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 deleteTask：删除数据
 */
export const deleteTask = (id: number): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/tasks/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 publishTask：业务逻辑处理
 */
export const publishTask = (id: number): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/tasks/${id}/publish`) as Promise<ApiEnvelope<ApiData>>

// 任务增强 API
// 契约＝后端 TaskController extendDeadline：TaskCreateDTO 的 newDueDate + reason（原误标为 deadline）
export const extendDeadline = (
  id: number,
  data: { newDueDate: string; reason?: string },
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/tasks/${id}/extend-deadline`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 copyTask：业务逻辑处理
 */
export const copyTask = (
  id: number,
  data?: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/tasks/${id}/copy`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getPendingGrading：获取/查询数据
 */
export const getPendingGrading = (teacherId?: number): Promise<ApiEnvelope<ApiData>> =>
  request.get('/tasks/pending-grading', { params: { teacherId } }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getCompletionStats：获取/查询数据
 */
export const getCompletionStats = (id: number): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/tasks/${id}/completion-stats`) as Promise<ApiEnvelope<ApiData>>

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
