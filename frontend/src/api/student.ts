import request from './request'
import { fetchAllPages, type ApiEnvelope, type ApiData } from '@/utils/response'

// 学生端 - 学习中心
export const getMyCourses = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/student/courses', { params }) as Promise<ApiEnvelope<ApiData>>
/** 获取全部已选课程，避免侧边栏课程目录被后端默认 10 条分页截断。 */
export const getAllMyCourses = (): Promise<ApiData[]> =>
  fetchAllPages((page: number, size: number) => getMyCourses({ page, size })) as Promise<ApiData[]>
/**
 * 导出函数 getCourseDetail：获取/查询数据
 */
export const getCourseDetail = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/student/courses/${courseId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 enrollCourse：业务逻辑处理
 */
export const enrollCourse = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/student/courses/${courseId}/enroll`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 unenrollCourse：业务逻辑处理
 */
export const unenrollCourse = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/student/courses/${courseId}/enroll`) as Promise<ApiEnvelope<ApiData>>

// 学生端 - 作业管理
export const getPendingAssignments = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/student/assignments/pending') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getCompletedAssignments：获取/查询数据
 */
export const getCompletedAssignments = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/student/assignments/completed') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getAssignmentDetail：获取/查询数据
 */
export const getAssignmentDetail = (assignmentId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/student/assignments/${assignmentId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 submitAssignment：发起请求/调用
 */
export const submitAssignment = (
  assignmentId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/student/assignments/${assignmentId}/submit`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getAssignmentResult：获取/查询数据
 */
export const getAssignmentResult = (assignmentId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/student/assignments/${assignmentId}/result`) as Promise<ApiEnvelope<ApiData>>

// 学生端 - 错题本
export const getWrongQuestions = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/student/wrong-questions', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 addWrongQuestion：创建/新增数据
 */
export const addWrongQuestion = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/student/wrong-questions', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 markMastered：业务逻辑处理
 */
export const markMastered = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/student/wrong-questions/${id}/mastered`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 removeWrongQuestion：删除数据
 */
export const removeWrongQuestion = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/student/wrong-questions/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 generatePractice：业务逻辑处理
 */
export const generatePractice = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/student/wrong-questions/practice', data) as Promise<ApiEnvelope<ApiData>>

// 学生端 - 学习进度
export const getProgressOverview = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/student/progress/overview') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getProgressCalendar：获取/查询数据
 */
export const getProgressCalendar = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/student/progress/calendar', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getProgressStatistics：获取/查询数据
 */
export const getProgressStatistics = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/student/progress/statistics', { params }) as Promise<ApiEnvelope<ApiData>>

// 学生端 - 学习核心（对标超星学习通，聚合端点）
// 首页/学习空间驾驶舱
export const getStudentDashboard = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/student/dashboard') as Promise<ApiEnvelope<ApiData>>
// 课程章节任务点学习中心（含闯关解锁态）
export const getLearningCenter = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/student/courses/${courseId}/learning-center`) as Promise<ApiEnvelope<ApiData>>
// 课程各章节任务点完成度统计（X/Y）
export const getProgressSummary = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/student/courses/${courseId}/progress-summary`) as Promise<ApiEnvelope<ApiData>>
// 标记任务点完成（视频/文档/阅读/测验非提交类）
export const markTaskComplete = (
  courseId: number | string,
  taskId: number | string,
  completionType?: unknown,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(
    `/student/courses/${courseId}/tasks/${taskId}/complete`,
    completionType ? { completionType } : {},
  ) as Promise<ApiEnvelope<ApiData>>
// 获取视频任务点续播状态
export const getVideoState = (
  courseId: number | string,
  taskId: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/student/courses/${courseId}/tasks/${taskId}/video-state`) as Promise<
    ApiEnvelope<ApiData>
  >
// 上报视频观看心跳（累计观看时长 + 当前位置；达阈值自动判定完成）
export const reportVideoHeartbeat = (
  courseId: number | string,
  taskId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/student/courses/${courseId}/tasks/${taskId}/video-heartbeat`, data) as Promise<
    ApiEnvelope<ApiData>
  >
// 测验任务点即时判分
export const submitQuiz = (
  courseId: number | string,
  taskId: number | string,
  answers: unknown,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/student/courses/${courseId}/tasks/${taskId}/quiz-submit`, answers) as Promise<
    ApiEnvelope<ApiData>
  >
// 学生成绩总览（按课程聚合作业/考试/总评）
export const getGrades = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/student/grades') as Promise<ApiEnvelope<ApiData>>
// 学生端学情可视化数据（D7：学习时长趋势 / 课程薄弱度雷达 / 学习活跃热力图）
export const getVisualizationData = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/student/analytics/visualization') as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getMyCourses,
  getAllMyCourses,
  getCourseDetail,
  enrollCourse,
  unenrollCourse,
  getPendingAssignments,
  getCompletedAssignments,
  getAssignmentDetail,
  submitAssignment,
  getAssignmentResult,
  getWrongQuestions,
  addWrongQuestion,
  markMastered,
  removeWrongQuestion,
  generatePractice,
  getProgressOverview,
  getProgressCalendar,
  getProgressStatistics,
  // 学习核心聚合端点
  getStudentDashboard,
  getLearningCenter,
  getProgressSummary,
  markTaskComplete,
  getVideoState,
  reportVideoHeartbeat,
  submitQuiz,
  getGrades,
  getVisualizationData,
}
