import request from './request'

// 学生端 - 学习中心
export const getMyCourses = (params = {}) => request.get('/student/courses', { params })
/**
 * 导出函数 getCourseDetail：获取/查询数据
 */
export const getCourseDetail = (courseId) => request.get(`/student/courses/${courseId}`)
/**
 * 导出函数 enrollCourse：业务逻辑处理
 */
export const enrollCourse = (courseId) => request.post(`/student/courses/${courseId}/enroll`)
/**
 * 导出函数 unenrollCourse：业务逻辑处理
 */
export const unenrollCourse = (courseId) => request.delete(`/student/courses/${courseId}/enroll`)

// 学生端 - 作业管理
export const getPendingAssignments = () => request.get('/student/assignments/pending')
/**
 * 导出函数 getCompletedAssignments：获取/查询数据
 */
export const getCompletedAssignments = () => request.get('/student/assignments/completed')
/**
 * 导出函数 getAssignmentDetail：获取/查询数据
 */
export const getAssignmentDetail = (assignmentId) =>
  request.get(`/student/assignments/${assignmentId}`)
/**
 * 导出函数 submitAssignment：发起请求/调用
 */
export const submitAssignment = (assignmentId, data) =>
  request.post(`/student/assignments/${assignmentId}/submit`, data)
/**
 * 导出函数 getAssignmentResult：获取/查询数据
 */
export const getAssignmentResult = (assignmentId) =>
  request.get(`/student/assignments/${assignmentId}/result`)

// 学生端 - 错题本
export const getWrongQuestions = (params = {}) => request.get('/student/wrong-questions', { params })
/**
 * 导出函数 addWrongQuestion：创建/新增数据
 */
export const addWrongQuestion = (data) => request.post('/student/wrong-questions', data)
/**
 * 导出函数 markMastered：业务逻辑处理
 */
export const markMastered = (id) => request.put(`/student/wrong-questions/${id}/mastered`)
/**
 * 导出函数 removeWrongQuestion：删除数据
 */
export const removeWrongQuestion = (id) => request.delete(`/student/wrong-questions/${id}`)
/**
 * 导出函数 generatePractice：业务逻辑处理
 */
export const generatePractice = (data) => request.post('/student/wrong-questions/practice', data)

// 学生端 - 学习进度
export const getProgressOverview = () => request.get('/student/progress/overview')
/**
 * 导出函数 getProgressCalendar：获取/查询数据
 */
export const getProgressCalendar = (params = {}) => request.get('/student/progress/calendar', { params })
/**
 * 导出函数 getProgressStatistics：获取/查询数据
 */
export const getProgressStatistics = (params = {}) =>
  request.get('/student/progress/statistics', { params })

// 学生端 - 学习核心（对标超星学习通，聚合端点）
// 首页/学习空间驾驶舱
export const getStudentDashboard = () => request.get('/student/dashboard')
// 课程章节任务点学习中心（含闯关解锁态）
export const getLearningCenter = (courseId) =>
  request.get(`/student/courses/${courseId}/learning-center`)
// 课程各章节任务点完成度统计（X/Y）
export const getProgressSummary = (courseId) =>
  request.get(`/student/courses/${courseId}/progress-summary`)
// 标记任务点完成（视频/文档/阅读/测验非提交类）
export const markTaskComplete = (courseId, taskId, completionType) =>
  request.post(
    `/student/courses/${courseId}/tasks/${taskId}/complete`,
    completionType ? { completionType } : {},
  )
// 获取视频任务点续播状态
export const getVideoState = (courseId, taskId) =>
  request.get(`/student/courses/${courseId}/tasks/${taskId}/video-state`)
// 上报视频观看心跳（累计观看时长 + 当前位置；达阈值自动判定完成）
export const reportVideoHeartbeat = (courseId, taskId, data) =>
  request.post(`/student/courses/${courseId}/tasks/${taskId}/video-heartbeat`, data)
// 测验任务点即时判分
export const submitQuiz = (courseId, taskId, answers) =>
  request.post(`/student/courses/${courseId}/tasks/${taskId}/quiz-submit`, answers)
// 学生成绩总览（按课程聚合作业/考试/总评）
export const getGrades = () => request.get('/student/grades')
// 学生端学情可视化数据（D7：学习时长趋势 / 课程薄弱度雷达 / 学习活跃热力图）
export const getVisualizationData = () => request.get('/student/analytics/visualization')

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getMyCourses,
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
