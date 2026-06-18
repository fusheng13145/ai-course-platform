import request from './request'

// 教师端 - 课程管理
export const createCourse = data => request.post('/teacher/courses', data)
export const getMyCourses = params => request.get('/teacher/courses', { params })
export const getCourseDetail = courseId => request.get(`/teacher/courses/${courseId}`)
export const updateCourse = (courseId, data) => request.put(`/teacher/courses/${courseId}`, data)
export const deleteCourse = courseId => request.delete(`/teacher/courses/${courseId}`)
export const addChapter = (courseId, data) => request.post(`/teacher/courses/${courseId}/chapters`, data)
export const updateChapter = (courseId, chapterId, data) => request.put(`/teacher/courses/${courseId}/chapters/${chapterId}`, data)
export const deleteChapter = (courseId, chapterId) => request.delete(`/teacher/courses/${courseId}/chapters/${chapterId}`)

// 教师端 - 作业管理
export const createAssignment = data => request.post('/teacher/assignments', data)
export const getAssignments = params => request.get('/teacher/assignments', { params })
export const getAssignmentDetail = assignmentId => request.get(`/teacher/assignments/${assignmentId}`)
export const updateAssignment = (assignmentId, data) => request.put(`/teacher/assignments/${assignmentId}`, data)
export const deleteAssignment = assignmentId => request.delete(`/teacher/assignments/${assignmentId}`)
export const getSubmissions = (assignmentId, params) => request.get(`/teacher/assignments/${assignmentId}/submissions`, { params })
export const gradeSubmission = (assignmentId, submissionId, data) => request.put(`/teacher/assignments/${assignmentId}/submissions/${submissionId}`, data)
export const publishAssignment = assignmentId => request.put(`/teacher/assignments/${assignmentId}/publish`)

// 教师端 - 题库管理
export const createQuestion = data => request.post('/teacher/questions', data)
export const getQuestions = params => request.get('/teacher/questions', { params })
export const getQuestionDetail = questionId => request.get(`/teacher/questions/${questionId}`)
export const updateQuestion = (questionId, data) => request.put(`/teacher/questions/${questionId}`, data)
export const deleteQuestion = questionId => request.delete(`/teacher/questions/${questionId}`)
export const importQuestions = data => request.post('/teacher/questions/import', data)
export const exportQuestions = params => request.get('/teacher/questions/export', { params })

// 教师端 - 学情分析
export const getClassAnalytics = courseId => request.get(`/teacher/analytics/class/${courseId}`)
export const getStudentAnalytics = studentId => request.get(`/teacher/analytics/students/${studentId}`)
export const getAssignmentAnalytics = assignmentId => request.get(`/teacher/analytics/assignments/${assignmentId}`)
export const getKnowledgePointAnalytics = params => request.get('/teacher/analytics/knowledge-points', { params })
export const exportReport = courseId => request.get(`/teacher/analytics/report/export?courseId=${courseId}`)

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
  exportReport
}