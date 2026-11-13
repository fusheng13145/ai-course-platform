import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

// 基础仪表盘数据（支持 teacherId 参数获取教师端/管理端数据）
export const getDashboardStats = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/analytics/dashboard', { params }) as Promise<ApiEnvelope<ApiData>>

// 平台综合总览（增强版，多维度运营数据）
export const getPlatformOverview = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/analytics/overview') as Promise<ApiEnvelope<ApiData>>

// 学生个人学习数据看板
export const getStudentAnalytics = (userId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/analytics/students/${userId}`) as Promise<ApiEnvelope<ApiData>>

// 学习趋势数据（按日期分组的学习时长）
// params: { userId } 可选
export const getLearningTrends = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/analytics/trends', { params }) as Promise<ApiEnvelope<ApiData>>

// 学习热力图数据
// params: { userId, startDate, endDate }  - userId 在 AnalyticsController 中是必填的
export const getHeatmap = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/analytics/heatmap', { params }) as Promise<ApiEnvelope<ApiData>>

// 平台级热力图（无需userId，管理员使用，聚合所有用户学习数据）
export const getPlatformHeatmap = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/analytics/heatmap/platform', { params }) as Promise<ApiEnvelope<ApiData>>

// 课程整体数据分析
export const getCourseAnalytics = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/analytics/courses/${courseId}`) as Promise<ApiEnvelope<ApiData>>

// 课程成绩分布（基础版）
export const getGradeDistribution = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/analytics/courses/${courseId}/grades`) as Promise<ApiEnvelope<ApiData>>

// 课程成绩分布直方图（增强版：分数段+及格率+优秀率）
export const getGradeHistogram = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/analytics/courses/${courseId}/grades/histogram`) as Promise<ApiEnvelope<ApiData>>

// 课程学生学情（含学生身份、作业均分、考试均分与学习进度）
export const getStudentCourseProgress = (
  courseId: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/analytics/courses/${courseId}/student-progress`) as Promise<ApiEnvelope<ApiData>>

// 热门课程排行（按选课人数降序 TOP20）
export const getCourseRanking = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/analytics/courses/ranking') as Promise<ApiEnvelope<ApiData>>

// 用户增长趋势（按天统计新增注册用户数）
// params: { days } 默认30天
export const getUserGrowth = (days: unknown): Promise<ApiEnvelope<ApiData>> =>
  request.get('/analytics/users/growth', {
    params: typeof days === 'number' ? { days } : days,
  }) as Promise<ApiEnvelope<ApiData>>

// 考试统计详情（参考人数、平均分、最高/最低分、及格率等）
export const getExamStatistics = (examPaperId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/analytics/exams/${examPaperId}/statistics`) as Promise<ApiEnvelope<ApiData>>

// 学情预警学生（进度<40% 或 成绩<60 判定，支持多维下钻聚合）
// params: { groupBy, courseId, department, major, classId }
export const getAtRiskStudents = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/analytics/at-risk/students', { params }) as Promise<ApiEnvelope<ApiData>>

// 向后兼容：getTrendData 复用 getUserGrowth
export const getTrendData = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  getUserGrowth(params)

// 导出统计报表（映射后端 GET /admin/statistics/export）
export const exportReport = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/statistics/export') as Promise<ApiEnvelope<ApiData>>

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
  getStudentCourseProgress,
  getCourseRanking,
  getUserGrowth,
  getExamStatistics,
  getAtRiskStudents,
  getTrendData,
  exportReport,
}
