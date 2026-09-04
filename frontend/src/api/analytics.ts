import request from './request'

// 基础仪表盘数据（支持 teacherId 参数获取教师端/管理端数据）
export const getDashboardStats = (params = {}) => request.get('/analytics/dashboard', { params })

// 平台综合总览（增强版，多维度运营数据）
export const getPlatformOverview = () => request.get('/analytics/overview')

// 学生个人学习数据看板
export const getStudentAnalytics = (userId) => request.get(`/analytics/students/${userId}`)

// 学习趋势数据（按日期分组的学习时长）
// params: { userId } 可选
export const getLearningTrends = (params = {}) => request.get('/analytics/trends', { params })

// 学习热力图数据
// params: { userId, startDate, endDate }  - userId 在 AnalyticsController 中是必填的
export const getHeatmap = (params = {}) => request.get('/analytics/heatmap', { params })

// 平台级热力图（无需userId，管理员使用，聚合所有用户学习数据）
export const getPlatformHeatmap = (params = {}) =>
  request.get('/analytics/heatmap/platform', { params })

// 课程整体数据分析
export const getCourseAnalytics = (courseId) => request.get(`/analytics/courses/${courseId}`)

// 课程成绩分布（基础版）
export const getGradeDistribution = (courseId) =>
  request.get(`/analytics/courses/${courseId}/grades`)

// 课程成绩分布直方图（增强版：分数段+及格率+优秀率）
export const getGradeHistogram = (courseId) =>
  request.get(`/analytics/courses/${courseId}/grades/histogram`)

// 热门课程排行（按选课人数降序 TOP20）
export const getCourseRanking = () => request.get('/analytics/courses/ranking')

// 用户增长趋势（按天统计新增注册用户数）
// params: { days } 默认30天
export const getUserGrowth = (days) =>
  request.get('/analytics/users/growth', { params: typeof days === 'number' ? { days } : days })

// 考试统计详情（参考人数、平均分、最高/最低分、及格率等）
export const getExamStatistics = (examPaperId) =>
  request.get(`/analytics/exams/${examPaperId}/statistics`)

// 学情预警学生（进度<40% 或 成绩<60 判定，支持多维下钻聚合）
// params: { groupBy, courseId, department, major, classId }
export const getAtRiskStudents = (params = {}) =>
  request.get('/analytics/at-risk/students', { params })

// 向后兼容：getTrendData 复用 getUserGrowth
export const getTrendData = (params = {}) => getUserGrowth(params)

// 导出统计报表（映射后端 GET /admin/statistics/export）
export const exportReport = () => request.get('/admin/statistics/export')

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getDashboardStats,
  getPlatformOverview,
  getStudentAnalytics,
  getLearningTrends,
  getHeatmap,
  getPlatformHeatmap,
  getCourseAnalytics,
  getGradeDistribution,
  getGradeHistogram,
  getCourseRanking,
  getUserGrowth,
  getExamStatistics,
  getAtRiskStudents,
  getTrendData,
  exportReport,
}
