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
  exportReport,
  createBackup,
  getAIMonitor,
}
