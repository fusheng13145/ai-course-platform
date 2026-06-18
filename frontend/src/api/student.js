import request from './request'

// 学生端 - 学习中心
export const getMyCourses = params => request.get('/student/courses', { params })
export const getCourseDetail = courseId => request.get(`/student/courses/${courseId}`)
export const updateProgress = (courseId, data) => request.post(`/student/courses/${courseId}/progress`, data)
export const enrollCourse = courseId => request.post(`/student/courses/${courseId}/enroll`)
export const unenrollCourse = courseId => request.delete(`/student/courses/${courseId}/enroll`)

// 学生端 - 作业管理
export const getPendingAssignments = () => request.get('/student/assignments/pending')
export const getCompletedAssignments = () => request.get('/student/assignments/completed')
export const getAssignmentDetail = assignmentId => request.get(`/student/assignments/${assignmentId}`)
export const submitAssignment = (assignmentId, data) => request.post(`/student/assignments/${assignmentId}/submit`, data)
export const getAssignmentResult = assignmentId => request.get(`/student/assignments/${assignmentId}/result`)

// 学生端 - 错题本
export const getWrongQuestions = params => request.get('/student/wrong-questions', { params })
export const addWrongQuestion = data => request.post('/student/wrong-questions', data)
export const markMastered = id => request.put(`/student/wrong-questions/${id}/mastered`)
export const removeWrongQuestion = id => request.delete(`/student/wrong-questions/${id}`)
export const generatePractice = data => request.post('/student/wrong-questions/practice', data)

// 学生端 - 学习进度
export const getProgressOverview = () => request.get('/student/progress/overview')
export const getProgressCalendar = params => request.get('/student/progress/calendar', { params })
export const getProgressStatistics = params => request.get('/student/progress/statistics', { params })

export default {
  getMyCourses,
  getCourseDetail,
  updateProgress,
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
  getProgressStatistics
}