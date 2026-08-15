import request from './request'
/**
 * 导出函数 getUserList：获取/查询数据
 */
export const getUserList = (params) => request.get('/users', { params })
/**
 * 导出函数 getUserById：获取/查询数据
 */
export const getUserById = (id) => request.get(`/users/${id}`)
/**
 * 导出函数 createUser：创建/新增数据
 */
export const createUser = (data) => request.post('/users', data)
/**
 * 导出函数 updateUser：更新数据
 */
export const updateUser = (id, data) => request.put(`/users/${id}`, data)
/**
 * 导出函数 deleteUser：删除数据
 */
export const deleteUser = (id) => request.delete(`/users/${id}`)
/**
 * 导出函数 updateUserStatus：更新数据
 */
export const updateUserStatus = (id, status) => request.patch(`/users/${id}/status`, { status })
/**
 * 导出函数 getTeacherStudents：获取/查询数据
 */
export const getTeacherStudents = () => request.get('/users/teacher/students')
// 以下导出由原 userEnhanced.js 合并而来
export const getProfile = (id) => request.get(`/users/${id}/profile`)
/**
 * 导出函数 getClassmates：获取/查询数据
 */
export const getClassmates = (id) => request.get(`/users/${id}/classmates`)
/**
 * 导出函数 getUserStats：获取/查询数据
 */
export const getUserStats = (id) => request.get(`/users/${id}/stats`)
/**
 * 导出函数 searchUsers：获取/查询数据
 */
export const searchUsers = (params) => request.get('/users/search', { params })
/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getUserList,
  getUserById,
  createUser,
  updateUser,
  deleteUser,
  updateUserStatus,
  getTeacherStudents,
  getProfile,
  getClassmates,
  getUserStats,
  searchUsers,
}
