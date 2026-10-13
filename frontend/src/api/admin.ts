import request from './request'
import { fetchAllPages } from '@/utils/response'
import { exportReport } from './analytics'
import { createBackup } from './system'

// re-export：与 analytics.js / system.js 保持一致，避免重复定义
export { exportReport, createBackup }

// 管理员端 - 用户管理
export const getUsers = (params = {}) => request.get('/admin/users', { params })
/** 获取筛选条件下的全部用户，适用于需要覆盖整个角色群体的批量操作。 */
export const getAllUsers = (filters = {}) =>
  fetchAllPages((page, size) => getUsers({ ...filters, page, size }))
/**
 * 导出函数 getUserDetail：获取/查询数据
 */
export const getUserDetail = (userId) => request.get(`/admin/users/${userId}`)
/**
 * 导出函数 createUser：创建/新增数据
 */
export const createUser = (data) => request.post('/admin/users', data)
/**
 * 导出函数 updateUser：更新数据
 */
export const updateUser = (userId, data) => request.put(`/admin/users/${userId}`, data)
/**
 * 导出函数 deleteUser：删除数据
 */
export const deleteUser = (userId) => request.delete(`/admin/users/${userId}`)
/**
 * 导出函数 resetUserPassword：业务逻辑处理
 */
export const resetUserPassword = (userId, data) =>
  request.put(`/admin/users/${userId}/reset-password`, data)
/**
 * 导出函数 importUsers：业务逻辑处理
 */
export const importUsers = (data) => request.post('/admin/users/import', data)
// Excel 批量导入（multipart 上传 .xlsx 文件）
export const importUsersExcel = (data) => request.post('/admin/users/import-excel', data)

// 管理员端 - 内容审核与违规处理（角色矩阵 ③）
/** 讨论回复审核列表（params: {page,size,status,keyword}） */
export const getModerationPosts = (params = {}) =>
  request.get('/admin/moderation/discussion-posts', { params })
/** 资源审核列表（平台全部资源；params 同讨论回复） */
export const getModerationResources = (params = {}) =>
  request.get('/admin/moderation/resources', { params })
/**
 * 对内容执行处置。`target` 取 `discussion-posts` / `resources`。
 * @param {'discussion-posts'|'resources'} target
 * @param {number|string} id
 * @param {{ action: 'HIDE'|'RESTORE'|'DELETE', reason?: string, banAuthor?: boolean }} data
 */
export const applyModerationAction = (target, id, data) =>
  request.post(`/admin/moderation/${target}/${id}/action`, data)
/** 经审核封禁的作者列表 */
export const getBannedAuthors = () => request.get('/admin/moderation/banned-authors')
/** 封禁作者（= 停用该账号，立即无法登录） */
export const banAuthor = (userId, data = {}) =>
  request.post(`/admin/moderation/users/${userId}/ban`, data)
/** 解除封禁（只对经审核封禁的账号有效） */
export const unbanAuthor = (userId) => request.post(`/admin/moderation/users/${userId}/unban`)

// 管理员端 - AI 出题治理（角色矩阵 ④ 修订：逐人限制名单，后端 V10 表）
/** 限制名单（含被限制教师的登录名与姓名） */
export const getAiRestrictions = () => request.get('/admin/ai-governance/restrictions')
/**
 * 把教师加入限制名单（幂等：已在名单内则更新原因，保留首次加入时间）。
 * @param {number|string} userId 教师用户ID
 * @param {{ reason?: string }} data
 */
export const restrictAiGeneration = (userId, data = {}) =>
  request.post('/admin/ai-governance/restrictions', { userId, ...data })
/** 解除限制（不在名单内后端返回 404，不静默成功） */
export const unrestrictAiGeneration = (userId) =>
  request.delete(`/admin/ai-governance/restrictions/${userId}`)

// 管理员端 - 学期管理（角色矩阵 ①②：创建学期 / 归档过期学期）
/**
 * 学期列表；可按状态过滤（ACTIVE / ARCHIVED，省略为全部）。
 * @param {{ status?: string }} params
 */
export const getSemesters = (params = {}) => request.get('/admin/semesters', { params })
/**
 * 创建学期（学期名唯一；结束日期不得早于开始日期）。
 * @param {{ name: string, startDate: string, endDate: string }} data
 */
export const createSemester = (data) => request.post('/admin/semesters', data)
/** 归档指定学期（幂等：已归档再次调用返回 archived=false） */
export const archiveSemester = (id) => request.post(`/admin/semesters/${id}/archive`)
/** 批量归档所有「状态=ACTIVE 且 结束日期<今天」的学期，返回本次条数 */
export const archiveExpiredSemesters = () => request.post('/admin/semesters/archive-expired')

// 管理员端 - 系统配置
export const getConfigs = (params = {}) => request.get('/admin/configs', { params })
/**
 * 导出函数 updateConfig：更新数据
 */
export const updateConfig = (configKey, data) => request.put(`/admin/configs/${configKey}`, data)
/**
 * 导出函数 getLogs：获取/查询数据
 */
export const getLogs = () => request.get('/admin/logs')
/**
 * 导出函数 getHealth：获取/查询数据
 */
export const getHealth = () => request.get('/admin/health')

// 管理员端 - 数据统计
export const getOverview = () => request.get('/admin/statistics/overview')
/**
 * 导出函数 getUserGrowth：获取/查询数据
 */
export const getUserGrowth = (params = {}) =>
  request.get('/admin/statistics/user-growth', { params })
/**
 * 导出函数 getCourseStats：获取/查询数据
 */
export const getCourseStats = () => request.get('/admin/statistics/courses')
/**
 * 导出函数 getAIUsage：获取/查询数据
 */
export const getAIUsage = () => request.get('/admin/statistics/ai-usage')
/**
 * 跨课程 AI 统计（按课程聚合，数据源 ai_call_logs）。
 * <p>⚠️ 与上方 getAIUsage 是两个不同口径：后者无逐次留痕、字段多为平台概况；
 * 本接口只统计自 2026-10-13 埋点上线后的调用，历史不可回填（响应内 collectionNote 已声明）。</p>
 * @param {{ days?: number }} params 统计天数（默认 30，后端收敛到 1–365）
 */
export const getAIUsageByCourse = (params = {}) =>
  request.get('/admin/statistics/ai-usage/by-course', { params })
/**
 * 导出函数 getAIMonitor：获取/查询数据
 */
export const getAIMonitor = () => request.get('/admin/ai-monitor')

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
  exportReport,
  createBackup,
  getAIMonitor,
}
