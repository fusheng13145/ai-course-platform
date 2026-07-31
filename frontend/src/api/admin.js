import request from './request'
import { exportReport } from './analytics'
import { createBackup } from './system'

// re-export：与 analytics.js / system.js 保持一致，避免重复定义
export { exportReport, createBackup }

// 管理员端 - 用户管理
export const getUsers = (params) => request.get('/admin/users', { params })
export const getUserDetail = (userId) => request.get(`/admin/users/${userId}`)
export const createUser = (data) => request.post('/admin/users', data)
export const updateUser = (userId, data) => request.put(`/admin/users/${userId}`, data)
export const deleteUser = (userId) => request.delete(`/admin/users/${userId}`)
export const resetUserPassword = (userId, data) =>
  request.put(`/admin/users/${userId}/reset-password`, data)
export const importUsers = (data) => request.post('/admin/users/import', data)

// 管理员端 - 系统配置
export const getConfigs = (params) => request.get('/admin/configs', { params })
export const updateConfig = (configKey, data) => request.put(`/admin/configs/${configKey}`, data)
export const getLogs = () => request.get('/admin/logs')
export const getHealth = () => request.get('/admin/health')

// 管理员端 - 数据统计
export const getOverview = () => request.get('/admin/statistics/overview')
export const getUserGrowth = (params) => request.get('/admin/statistics/user-growth', { params })
export const getCourseStats = () => request.get('/admin/statistics/courses')
export const getAIUsage = () => request.get('/admin/statistics/ai-usage')
export const getAIMonitor = () => request.get('/admin/ai-monitor')

export default {
  getUsers,
  getUserDetail,
  createUser,
  updateUser,
  deleteUser,
  resetUserPassword,
  importUsers,
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
