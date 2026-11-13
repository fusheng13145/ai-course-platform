import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

// ============================================================
// 教师端统一 API（已合并原 TeacherController + CourseTeachingController）
// 完整路径 = /api + 下列相对路径（前端 baseURL 已处理 /api 前缀）
// 说明：所有课程/作业/题库/学情接口均已由服务端做课程归属校验（仅授课教师或管理员可访问）。
// ============================================================

// ---------- 课程管理（CRUD） ----------
export const createCourse = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/teacher/courses', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getMyCourses：获取/查询数据
 */
export const getMyCourses = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/teacher/courses', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getCourseDetail：获取/查询数据
 */
export const getCourseDetail = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teacher/courses/${courseId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updateCourse：更新数据
 */
export const updateCourse = (
  courseId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/teacher/courses/${courseId}`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 deleteCourse：删除数据
 */
export const deleteCourse = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/teacher/courses/${courseId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 addChapter：创建/新增数据
 */
export const addChapter = (
  courseId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/teacher/courses/${courseId}/chapters`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updateChapter：更新数据
 */
export const updateChapter = (
  courseId: number | string,
  chapterId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/teacher/courses/${courseId}/chapters/${chapterId}`, data) as Promise<
    ApiEnvelope<ApiData>
  >
/**
 * 导出函数 deleteChapter：删除数据
 */
export const deleteChapter = (
  courseId: number | string,
  chapterId: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/teacher/courses/${courseId}/chapters/${chapterId}`) as Promise<
    ApiEnvelope<ApiData>
  >

// ---------- 作业管理 ----------
export const createAssignment = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/teacher/assignments', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getAssignments：获取/查询数据
 */
export const getAssignments = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/teacher/assignments', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getAssignmentDetail：获取/查询数据
 */
export const getAssignmentDetail = (assignmentId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teacher/assignments/${assignmentId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updateAssignment：更新数据
 */
export const updateAssignment = (
  assignmentId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/teacher/assignments/${assignmentId}`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 deleteAssignment：删除数据
 */
export const deleteAssignment = (assignmentId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/teacher/assignments/${assignmentId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getSubmissions：获取/查询数据
 */
export const getSubmissions = (
  assignmentId: number | string,
  params: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teacher/assignments/${assignmentId}/submissions`, { params }) as Promise<
    ApiEnvelope<ApiData>
  >
/**
 * 导出函数 gradeSubmission：业务逻辑处理
 */
export const gradeSubmission = (
  assignmentId: number | string,
  submissionId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/teacher/assignments/${assignmentId}/submissions/${submissionId}`, data) as Promise<
    ApiEnvelope<ApiData>
  >
/**
 * 导出函数 publishAssignment：业务逻辑处理
 */
export const publishAssignment = (assignmentId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/teacher/assignments/${assignmentId}/publish`) as Promise<ApiEnvelope<ApiData>>

// ---------- 题库管理 ----------
export const createQuestion = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/teacher/questions', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getQuestions：获取/查询数据
 */
export const getQuestions = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/teacher/questions', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getQuestionDetail：获取/查询数据
 */
export const getQuestionDetail = (questionId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teacher/questions/${questionId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updateQuestion：更新数据
 */
export const updateQuestion = (
  questionId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/teacher/questions/${questionId}`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 deleteQuestion：删除数据
 */
export const deleteQuestion = (questionId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/teacher/questions/${questionId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 importQuestions：业务逻辑处理
 */
export const importQuestions = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/teacher/questions/import', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 exportQuestions：业务逻辑处理
 */
export const exportQuestions = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/teacher/questions/export', { params }) as Promise<ApiEnvelope<ApiData>>

// ---------- 学情分析 ----------
export const getClassAnalytics = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teacher/analytics/class/${courseId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getStudentAnalytics：获取/查询数据
 */
export const getStudentAnalytics = (studentId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teacher/analytics/students/${studentId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getAssignmentAnalytics：获取/查询数据
 */
export const getAssignmentAnalytics = (
  assignmentId: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teacher/analytics/assignments/${assignmentId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getKnowledgePointAnalytics：获取/查询数据
 */
export const getKnowledgePointAnalytics = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/teacher/analytics/knowledge-points', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 exportReport：业务逻辑处理
 */
export const exportReport = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teacher/analytics/report/export?courseId=${courseId}`) as Promise<
    ApiEnvelope<ApiData>
  >

// ---------- 课程建设 / 学情驾驶舱（原 CourseTeachingController，已并入本模块） ----------
// 教师增强仪表盘 ：courseCount/studentCount/pendingGrading/avgScore + courseCards/atRiskStudents/taskPointOverview/platformComparison
// AI 教学总览已拆到独立端点 getTeacherDashboardAiOverview（异步补数，主聚合不再同步调 LLM）
export const getTeacherDashboard = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/teacher/dashboard/overview') as Promise<ApiEnvelope<ApiData>>
// 教师首页 AI 教学总览：{ summary ... }
export const getTeacherDashboardAiOverview = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/teacher/dashboard/ai-overview') as Promise<ApiEnvelope<ApiData>>
// 我的课程聚合指标卡片：List<{ courseId,name,code,coverImage,status,category,studentCount,avgProgress,completionRate,pendingGrading }>
export const getMyCourseSummaries = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/teacher/courses/summaries') as Promise<ApiEnvelope<ApiData>>
// 单课程教学驾驶舱：overview/content/learning/grading/trend/atRiskStudents/chapterTree/aiInsight
export const getCourseDashboard = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teacher/courses/${courseId}/dashboard`) as Promise<ApiEnvelope<ApiData>>
// 完整章节树（含任务点）：List<{ id,title,content,orderIndex,duration,isPublished,parentId,taskCount,submittedCount,completionRate,taskPoints[],children[] }>
export const getChapterTree = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teacher/courses/${courseId}/chapters/tree`) as Promise<ApiEnvelope<ApiData>>
// AI 智能建课大纲：POST 查询 topic(必填),category,level,chapterCount(默认6) → { success, outline:{title,description,chapters[]}, applicableCourseCategory }
export const generateOutline = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/teacher/courses/ai/generate-outline', null, { params }) as Promise<
    ApiEnvelope<ApiData>
  >
// 克隆课程（结构+章节，状态置草稿）：POST ?newName= → { newCourseId,newCourseName,clonedChapters }
export const cloneCourse = (
  courseId: number | string,
  newName: unknown,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/teacher/courses/${courseId}/clone`, null, { params: { newName } }) as Promise<
    ApiEnvelope<ApiData>
  >

// 课程驾驶舱：按课程获取班级维度（用于切换班级维度的教学概览）。
// 真实数据来源：后端 GET /classrooms/by-course/{courseId}（Classroom 实体已新增 courseId 关联字段，
// 绑定课程的班级将返回）。映射驾驶舱结构 { key←id, name, students←studentCount, pending, schedule:'' }。
// pending（待批改作业数）已真实化：并行请求 GET /classrooms/by-course/{courseId}/pending
// （按 submissions.status=SUBMITTED 聚合，见 ClassroomService.countPendingByCourse）；
// 该端点不可用/未绑定时回退 0（真实零值，非占位）。
// schedule（课表）数据模型尚未沉淀，如实占位 ''（非伪造数据）。
// 若某课程未绑定任何班级（返回空或 404），调用方 CourseCockpit 不渲染班级页签，
// 头部如实显示「未绑定行政班」，学生数改用 GET /courses/{id}/stats 的 totalStudents。
export const getCourseClasses = async (courseId: number | string): Promise<ApiData[]> => {
  const [classesRes, pendingRes] = await Promise.all([
    request.get(`/classrooms/by-course/${courseId}`),
    request.get(`/classrooms/by-course/${courseId}/pending`).catch(() => ({ data: [] })),
  ])
  const list = Array.isArray(classesRes?.data) ? classesRes.data : []
  const pendingMap = new Map(
    (Array.isArray(pendingRes?.data) ? pendingRes.data : []).map((p: ApiData) => [
      p.classroomId,
      p.pendingCount || 0,
    ]),
  )
  return list.map((c: ApiData) => ({
    key: c.id,
    name: c.name,
    students: c.studentCount || 0,
    pending: pendingMap.get(c.id) || 0,
    schedule: '',
  }))
}

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  createCourse,
  getMyCourses,
  getCourseDetail,
  updateCourse,
  deleteCourse,
  addChapter,
  updateChapter,
  deleteChapter,
  createAssignment,
  getAssignments,
  getAssignmentDetail,
  updateAssignment,
  deleteAssignment,
  getSubmissions,
  gradeSubmission,
  publishAssignment,
  createQuestion,
  getQuestions,
  getQuestionDetail,
  updateQuestion,
  deleteQuestion,
  importQuestions,
  exportQuestions,
  getClassAnalytics,
  getStudentAnalytics,
  getAssignmentAnalytics,
  getKnowledgePointAnalytics,
  exportReport,
  getTeacherDashboard,
  getTeacherDashboardAiOverview,
  getMyCourseSummaries,
  getCourseDashboard,
  getChapterTree,
  generateOutline,
  cloneCourse,
  getCourseClasses,
}
