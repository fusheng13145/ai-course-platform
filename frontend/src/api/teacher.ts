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
export const getMyCourses = (params = {}) => request.get('/teacher/courses', { params })
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
export const getAssignments = (params = {}) => request.get('/teacher/assignments', { params })
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
export const getQuestions = (params = {}) => request.get('/teacher/questions', { params })
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
export const exportQuestions = (params = {}) => request.get('/teacher/questions/export', { params })

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
export const getKnowledgePointAnalytics = (params = {}) =>
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
export const generateOutline = (params = {}) =>
  request.post('/teacher/courses/ai/generate-outline', null, { params })
// 克隆课程（结构+章节，状态置草稿）：POST ?newName= → { newCourseId,newCourseName,clonedChapters }
export const cloneCourse = (courseId, newName) =>
  request.post(`/teacher/courses/${courseId}/clone`, null, { params: { newName } })

// 课程驾驶舱：按课程获取班级维度（用于切换班级维度的教学概览）。
// 真实数据来源：后端 GET /classrooms/by-course/{courseId}（Classroom 实体已新增 courseId 关联字段，
// 绑定课程的班级将返回）。映射驾驶舱结构 { key←id, name, students←studentCount, pending, schedule:'' }。
// pending（待批改作业数）已真实化：并行请求 GET /classrooms/by-course/{courseId}/pending
// （按 submissions.status=SUBMITTED 聚合，见 ClassroomService.countPendingByCourse）；
// 该端点不可用/未绑定时回退 0（真实零值，非占位）。
// schedule（课表）数据模型尚未沉淀，如实占位 ''（非伪造数据）。
// 若某课程未绑定任何班级（返回空或 404），调用方 CourseCockpit 不渲染班级页签，
// 头部如实显示「未绑定行政班」，学生数改用 GET /courses/{id}/stats 的 totalStudents。
export const getCourseClasses = async (courseId) => {
  const [classesRes, pendingRes] = await Promise.all([
    request.get(`/classrooms/by-course/${courseId}`),
    request.get(`/classrooms/by-course/${courseId}/pending`).catch(() => ({ data: [] })),
  ])
  const list = Array.isArray(classesRes?.data) ? classesRes.data : []
  const pendingMap = new Map(
    (Array.isArray(pendingRes?.data) ? pendingRes.data : []).map((p) => [
      p.classroomId,
      p.pendingCount || 0,
    ]),
  )
  return list.map((c) => ({
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
  getTeacherDashboardV2,
  getMyCourseSummaries,
  getCourseDashboard,
  getChapterTree,
  generateOutline,
  cloneCourse,
  getCourseClasses,
}
