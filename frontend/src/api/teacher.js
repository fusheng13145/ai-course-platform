import request from './request'

// ============================================================
// 教师端统一 API（已合并原 TeacherController + CourseTeachingController）
// 完整路径 = /api + 下列相对路径（前端 baseURL 已处理 /api 前缀）
// 说明：所有课程/作业/题库/学情接口均已由服务端做课程归属校验（仅授课教师或管理员可访问）。
// ============================================================

// ---------- 课程管理（CRUD） ----------
export const createCourse = (data) => request.post('/teacher/courses', data)
/**
 * 导出函数 getMyCourses：获取/查询数据
 */
export const getMyCourses = (params) => request.get('/teacher/courses', { params })
/**
 * 导出函数 getCourseDetail：获取/查询数据
 */
export const getCourseDetail = (courseId) => request.get(`/teacher/courses/${courseId}`)
/**
 * 导出函数 updateCourse：更新数据
 */
export const updateCourse = (courseId, data) => request.put(`/teacher/courses/${courseId}`, data)
/**
 * 导出函数 deleteCourse：删除数据
 */
export const deleteCourse = (courseId) => request.delete(`/teacher/courses/${courseId}`)
/**
 * 导出函数 addChapter：创建/新增数据
 */
export const addChapter = (courseId, data) =>
  request.post(`/teacher/courses/${courseId}/chapters`, data)
/**
 * 导出函数 updateChapter：更新数据
 */
export const updateChapter = (courseId, chapterId, data) =>
  request.put(`/teacher/courses/${courseId}/chapters/${chapterId}`, data)
/**
 * 导出函数 deleteChapter：删除数据
 */
export const deleteChapter = (courseId, chapterId) =>
  request.delete(`/teacher/courses/${courseId}/chapters/${chapterId}`)

// ---------- 作业管理 ----------
export const createAssignment = (data) => request.post('/teacher/assignments', data)
/**
 * 导出函数 getAssignments：获取/查询数据
 */
export const getAssignments = (params) => request.get('/teacher/assignments', { params })
/**
 * 导出函数 getAssignmentDetail：获取/查询数据
 */
export const getAssignmentDetail = (assignmentId) =>
  request.get(`/teacher/assignments/${assignmentId}`)
/**
 * 导出函数 updateAssignment：更新数据
 */
export const updateAssignment = (assignmentId, data) =>
  request.put(`/teacher/assignments/${assignmentId}`, data)
/**
 * 导出函数 deleteAssignment：删除数据
 */
export const deleteAssignment = (assignmentId) =>
  request.delete(`/teacher/assignments/${assignmentId}`)
/**
 * 导出函数 getSubmissions：获取/查询数据
 */
export const getSubmissions = (assignmentId, params) =>
  request.get(`/teacher/assignments/${assignmentId}/submissions`, { params })
/**
 * 导出函数 gradeSubmission：业务逻辑处理
 */
export const gradeSubmission = (assignmentId, submissionId, data) =>
  request.put(`/teacher/assignments/${assignmentId}/submissions/${submissionId}`, data)
/**
 * 导出函数 publishAssignment：业务逻辑处理
 */
export const publishAssignment = (assignmentId) =>
  request.put(`/teacher/assignments/${assignmentId}/publish`)

// ---------- 题库管理 ----------
export const createQuestion = (data) => request.post('/teacher/questions', data)
/**
 * 导出函数 getQuestions：获取/查询数据
 */
export const getQuestions = (params) => request.get('/teacher/questions', { params })
/**
 * 导出函数 getQuestionDetail：获取/查询数据
 */
export const getQuestionDetail = (questionId) => request.get(`/teacher/questions/${questionId}`)
/**
 * 导出函数 updateQuestion：更新数据
 */
export const updateQuestion = (questionId, data) =>
  request.put(`/teacher/questions/${questionId}`, data)
/**
 * 导出函数 deleteQuestion：删除数据
 */
export const deleteQuestion = (questionId) => request.delete(`/teacher/questions/${questionId}`)
/**
 * 导出函数 importQuestions：业务逻辑处理
 */
export const importQuestions = (data) => request.post('/teacher/questions/import', data)
/**
 * 导出函数 exportQuestions：业务逻辑处理
 */
export const exportQuestions = (params) => request.get('/teacher/questions/export', { params })

// ---------- 学情分析 ----------
export const getClassAnalytics = (courseId) => request.get(`/teacher/analytics/class/${courseId}`)
/**
 * 导出函数 getStudentAnalytics：获取/查询数据
 */
export const getStudentAnalytics = (studentId) =>
  request.get(`/teacher/analytics/students/${studentId}`)
/**
 * 导出函数 getAssignmentAnalytics：获取/查询数据
 */
export const getAssignmentAnalytics = (assignmentId) =>
  request.get(`/teacher/analytics/assignments/${assignmentId}`)
/**
 * 导出函数 getKnowledgePointAnalytics：获取/查询数据
 */
export const getKnowledgePointAnalytics = (params) =>
  request.get('/teacher/analytics/knowledge-points', { params })
/**
 * 导出函数 exportReport：业务逻辑处理
 */
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
  getTeacherDashboardV2,
  getMyCourseSummaries,
  getCourseDashboard,
  getChapterTree,
  generateOutline,
  cloneCourse,
}
