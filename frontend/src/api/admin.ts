import request from './request'
import { fetchAllPages, type ApiEnvelope, type ApiData } from '@/utils/response'
import { exportReport } from './analytics'
import { createBackup } from './system'

// re-export：与 analytics.js / system.js 保持一致，避免重复定义
export { exportReport, createBackup }

// 管理员端 - 用户管理
export const getUsers = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/users', { params }) as Promise<ApiEnvelope<ApiData>>
/** 获取筛选条件下的全部用户，适用于需要覆盖整个角色群体的批量操作。 */
export const getAllUsers = (filters: Record<string, unknown> = {}): Promise<ApiData[]> =>
  fetchAllPages((page: number, size: number) => getUsers({ ...filters, page, size })) as Promise<
    ApiData[]
  >
/**
 * 导出函数 getUserDetail：获取/查询数据
 */
export const getUserDetail = (userId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/admin/users/${userId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 createUser：创建/新增数据
 */
export const createUser = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/admin/users', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updateUser：更新数据
 */
export const updateUser = (
  userId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/admin/users/${userId}`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 deleteUser：删除数据
 */
export const deleteUser = (userId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/admin/users/${userId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 resetUserPassword：业务逻辑处理
 */
export const resetUserPassword = (
  userId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/admin/users/${userId}/reset-password`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 importUsers：业务逻辑处理
 */
export const importUsers = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/admin/users/import', data) as Promise<ApiEnvelope<ApiData>>
// Excel 批量导入（multipart 上传 .xlsx 文件）
export const importUsersExcel = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/admin/users/import-excel', data) as Promise<ApiEnvelope<ApiData>>

// 管理员端 - 内容审核与违规处理（角色矩阵 ③）
/** 讨论回复审核列表（params: {page,size,status,keyword}） */
export const getModerationPosts = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/moderation/discussion-posts', { params }) as Promise<ApiEnvelope<ApiData>>
/** 资源审核列表（平台全部资源；params 同讨论回复） */
export const getModerationResources = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/moderation/resources', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 对内容执行处置。`target` 取 `discussion-posts` / `resources`。
 * @param {'discussion-posts'|'resources'} target
 * @param {number|string} id
 * @param {{ action: 'HIDE'|'RESTORE'|'DELETE', reason?: string, banAuthor?: boolean }} data
 */
export const applyModerationAction = (
  target: number | string,
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/admin/moderation/${target}/${id}/action`, data) as Promise<ApiEnvelope<ApiData>>
/** 经审核封禁的作者列表 */
export const getBannedAuthors = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/moderation/banned-authors') as Promise<ApiEnvelope<ApiData>>
/** 封禁作者（= 停用该账号，立即无法登录） */
export const banAuthor = (
  userId: number | string,
  data: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/admin/moderation/users/${userId}/ban`, data) as Promise<ApiEnvelope<ApiData>>
/** 解除封禁（只对经审核封禁的账号有效） */
export const unbanAuthor = (userId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/admin/moderation/users/${userId}/unban`) as Promise<ApiEnvelope<ApiData>>

// 管理员端 - AI 出题治理（角色矩阵 ④ 修订：逐人限制名单，后端 V10 表）
/** 限制名单（含被限制教师的登录名与姓名） */
export const getAiRestrictions = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/ai-governance/restrictions') as Promise<ApiEnvelope<ApiData>>
/**
 * 把教师加入限制名单（幂等：已在名单内则更新原因，保留首次加入时间）。
 * @param {number|string} userId 教师用户ID
 * @param {{ reason?: string }} data
 */
export const restrictAiGeneration = (
  userId: number | string,
  data: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/admin/ai-governance/restrictions', { userId, ...data }) as Promise<
    ApiEnvelope<ApiData>
  >
/** 解除限制（不在名单内后端返回 404，不静默成功） */
export const unrestrictAiGeneration = (userId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/admin/ai-governance/restrictions/${userId}`) as Promise<ApiEnvelope<ApiData>>

// 管理员端 - 学期管理（角色矩阵 ①②：创建学期 / 归档过期学期）
/**
 * 学期列表；可按状态过滤（ACTIVE / ARCHIVED，省略为全部）。
 * @param {{ status?: string }} params
 */
export const getSemesters = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/semesters', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 创建学期（学期名唯一；结束日期不得早于开始日期）。
 * @param {{ name: string, startDate: string, endDate: string }} data
 */
export const createSemester = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/admin/semesters', data) as Promise<ApiEnvelope<ApiData>>
/** 归档指定学期（幂等：已归档再次调用返回 archived=false） */
export const archiveSemester = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/admin/semesters/${id}/archive`) as Promise<ApiEnvelope<ApiData>>
/** 批量归档所有「状态=ACTIVE 且 结束日期<今天」的学期，返回本次条数 */
export const archiveExpiredSemesters = (): Promise<ApiEnvelope<ApiData>> =>
  request.post('/admin/semesters/archive-expired') as Promise<ApiEnvelope<ApiData>>

// 管理员端 - 组织架构（V12：学校 → 学院 → 专业；2026-11-07 全量实现批接入）
export const getOrgTree = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/org/tree') as Promise<ApiEnvelope<ApiData>>
export const getColleges = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/org/colleges') as Promise<ApiEnvelope<ApiData>>
export const createCollege = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/admin/org/colleges', data) as Promise<ApiEnvelope<ApiData>>
export const updateCollege = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/admin/org/colleges/${id}`, data) as Promise<ApiEnvelope<ApiData>>
export const archiveCollege = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/admin/org/colleges/${id}`) as Promise<ApiEnvelope<ApiData>>
export const getMajors = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/org/majors', { params }) as Promise<ApiEnvelope<ApiData>>
export const createMajor = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/admin/org/majors', data) as Promise<ApiEnvelope<ApiData>>
export const updateMajor = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/admin/org/majors/${id}`, data) as Promise<ApiEnvelope<ApiData>>
export const archiveMajor = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/admin/org/majors/${id}`) as Promise<ApiEnvelope<ApiData>>
export const getOrgAdminClasses = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/org/admin-classes', { params }) as Promise<ApiEnvelope<ApiData>>

// 管理员端 - 通知公告（WP8，2026-11-07 全量实现批接入）
/** 全量公告分页（管理端治理视图，不受受众过滤） */
export const getAllAnnouncements = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/announcements/admin/all', { params }) as Promise<ApiEnvelope<ApiData>>
/** 发布公告：courseId / teachingClassId / adminClassId 三目标互斥 */
export const publishAnnouncement = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/announcements', data) as Promise<ApiEnvelope<ApiData>>

// 管理员端 - 选课与名单（2026-11-07 全量实现批接入）
/** 全量报名记录分页；status: PENDING/APPROVED/REJECTED */
export const listAllEnrollments = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/enrollments', { params }) as Promise<ApiEnvelope<ApiData>>

// 管理员端 - 教学班监管（WP4/WP5 读面；写面为教学班主讲，见 TeachingClassController）
/** 课程下教学班列表 */
export const getTeachingClassesByCourse = (
  courseId: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teaching-classes/course/${courseId}`) as Promise<ApiEnvelope<ApiData>>
/** 教学班成员（选课记录形态） */
export const getTeachingClassMembers = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teaching-classes/${id}/members`) as Promise<ApiEnvelope<ApiData>>

// 管理员端 - 系统配置
export const getConfigs = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/configs', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updateConfig：更新数据
 */
export const updateConfig = (
  configKey: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/admin/configs/${configKey}`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getLogs：获取/查询数据
 */
export const getLogs = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/logs') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getHealth：获取/查询数据
 */
export const getHealth = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/health') as Promise<ApiEnvelope<ApiData>>

// 管理员端 - 数据统计
export const getOverview = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/statistics/overview') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getUserGrowth：获取/查询数据
 */
export const getUserGrowth = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/statistics/user-growth', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getCourseStats：获取/查询数据
 */
export const getCourseStats = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/statistics/courses') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getAIUsage：获取/查询数据
 */
export const getAIUsage = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/statistics/ai-usage') as Promise<ApiEnvelope<ApiData>>
/**
 * 跨课程 AI 统计（按课程聚合，数据源 ai_call_logs）。
 * <p>⚠️ 与上方 getAIUsage 是两个不同口径：后者无逐次留痕、字段多为平台概况；
 * 本接口只统计自 2026-10-13 埋点上线后的调用，历史不可回填（响应内 collectionNote 已声明）。</p>
 * @param {{ days?: number }} params 统计天数（默认 30，后端收敛到 1–365）
 */
export const getAIUsageByCourse = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/statistics/ai-usage/by-course', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getAIMonitor：获取/查询数据
 */
export const getAIMonitor = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/admin/ai-monitor') as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getUsers,
  getAllUsers,
  getUserDetail,
  createUser,
  updateUser,
  deleteUser,
  resetUserPassword,
  importUsers,
  importUsersExcel,
  getConfigs,
  updateConfig,
  getLogs,
  getHealth,
  getOverview,
  getUserGrowth,
  getCourseStats,
  getAIUsage,
  getAIUsageByCourse,
  getModerationPosts,
  getModerationResources,
  applyModerationAction,
  getBannedAuthors,
  banAuthor,
  unbanAuthor,
  getAiRestrictions,
  restrictAiGeneration,
  unrestrictAiGeneration,
  getSemesters,
  createSemester,
  archiveSemester,
  archiveExpiredSemesters,
  getOrgTree,
  getColleges,
  createCollege,
  updateCollege,
  archiveCollege,
  getMajors,
  createMajor,
  updateMajor,
  archiveMajor,
  getOrgAdminClasses,
  getAllAnnouncements,
  publishAnnouncement,
  listAllEnrollments,
  getTeachingClassesByCourse,
  getTeachingClassMembers,
  exportReport,
  createBackup,
  getAIMonitor,
}
