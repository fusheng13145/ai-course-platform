import request from './request'

// ============================================================
// 教师端统一 API（已合并原 TeacherController + CourseTeachingController）
// 完整路径 = /api + 下列相对路径（前端 baseURL 已处理 /api 前缀）
// 说明：所有课程/作业/题库/学情接口均已由服务端做课程归属校验（仅授课教师或管理员可访问）。
// ============================================================

// ---------- 课程管理（CRUD） ----------
export const createCourse = (data) => request.post('/teacher/courses', data)
export const getMyCourses = (params) => request.get('/teacher/courses', { params })
export const getCourseDetail = (courseId) => request.get(`/teacher/courses/${courseId}`)
export const updateCourse = (courseId, data) => request.put(`/teacher/courses/${courseId}`, data)
export const deleteCourse = (courseId) => request.delete(`/teacher/courses/${courseId}`)
export const addChapter = (courseId, data) =>
  request.post(`/teacher/courses/${courseId}/chapters`, data)
export const updateChapter = (courseId, chapterId, data) =>
  request.put(`/teacher/courses/${courseId}/chapters/${chapterId}`, data)
export const deleteChapter = (courseId, chapterId) =>
  request.delete(`/teacher/courses/${courseId}/chapters/${chapterId}`)

// ---------- 作业管理 ----------
export const createAssignment = (data) => request.post('/teacher/assignments', data)
export const getAssignments = (params) => request.get('/teacher/assignments', { params })
export const getAssignmentDetail = (assignmentId) =>
  request.get(`/teacher/assignments/${assignmentId}`)
export const updateAssignment = (assignmentId, data) =>
  request.put(`/teacher/assignments/${assignmentId}`, data)
export const deleteAssignment = (assignmentId) =>
  request.delete(`/teacher/assignments/${assignmentId}`)
export const getSubmissions = (assignmentId, params) =>
  request.get(`/teacher/assignments/${assignmentId}/submissions`, { params })
export const gradeSubmission = (assignmentId, submissionId, data) =>
  request.put(`/teacher/assignments/${assignmentId}/submissions/${submissionId}`, data)
export const publishAssignment = (assignmentId) =>
  request.put(`/teacher/assignments/${assignmentId}/publish`)

// ---------- 题库管理 ----------
export const createQuestion = (data) => request.post('/teacher/questions', data)
export const getQuestions = (params) => request.get('/teacher/questions', { params })
export const getQuestionDetail = (questionId) => request.get(`/teacher/questions/${questionId}`)
export const updateQuestion = (questionId, data) =>
  request.put(`/teacher/questions/${questionId}`, data)
export const deleteQuestion = (questionId) => request.delete(`/teacher/questions/${questionId}`)
export const importQuestions = (data) => request.post('/teacher/questions/import', data)
export const exportQuestions = (params) => request.get('/teacher/questions/export', { params })

// ---------- 学情分析 ----------
export const getClassAnalytics = (courseId) => request.get(`/teacher/analytics/class/${courseId}`)
export const getStudentAnalytics = (studentId) =>
  request.get(`/teacher/analytics/students/${studentId}`)
export const getAssignmentAnalytics = (assignmentId) =>
  request.get(`/teacher/analytics/assignments/${assignmentId}`)
export const getKnowledgePointAnalytics = (params) =>
  request.get('/teacher/analytics/knowledge-points', { params })
export const exportReport = (courseId) =>
  request.get(`/teacher/analytics/report/export?courseId=${courseId}`)

// ---------- 课程建设 / 学情驾驶舱（原 CourseTeachingController，已并入本模块） ----------
// 教师增强仪表盘 v2：courseCount/studentCount/pendingGrading/avgScore + courseCards/atRiskStudents/taskPointOverview/platformComparison/aiOverview
export const getTeacherDashboardV2 = () => request.get('/teacher/dashboard/v2')
// 我的课程聚合指标卡片：List<{ courseId,name,code,coverImage,status,category,studentCount,avgProgress,completionRate,pendingGrading }>
export const getMyCourseSummaries = () => request.get('/teacher/courses/summaries')
// 单课程教学驾驶舱：overview/content/learning/grading/trend/atRiskStudents/chapterTree/aiInsight
export const getCourseDashboard = (courseId) =>
  request.get(`/teacher/courses/${courseId}/dashboard`)
// 完整章节树（含任务点）：List<{ id,title,content,orderIndex,duration,isPublished,parentId,taskCount,submittedCount,completionRate,taskPoints[],children[] }>
export const getChapterTree = (courseId) =>
  request.get(`/teacher/courses/${courseId}/chapters/tree`)
// AI 智能建课大纲：POST 查询 topic(必填),category,level,chapterCount(默认6) → { success, outline:{title,description,chapters[]}, applicableCourseCategory }
export const generateOutline = (params) =>
  request.post('/teacher/courses/ai/generate-outline', null, { params })
// 克隆课程（结构+章节，状态置草稿）：POST ?newName= → { newCourseId,newCourseName,clonedChapters }
export const cloneCourse = (courseId, newName) =>
  request.post(`/teacher/courses/${courseId}/clone`, null, { params: { newName } })

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
  getTeacherDashboardV2,
  getMyCourseSummaries,
  getCourseDashboard,
  getChapterTree,
  generateOutline,
  cloneCourse,
}
