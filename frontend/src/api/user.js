import request from './request'
export const getUserList = (params) => request.get('/users', { params })
export const getUserById = (id) => request.get(`/users/${id}`)
export const createUser = (data) => request.post('/users', data)
export const updateUser = (id, data) => request.put(`/users/${id}`, data)
export const deleteUser = (id) => request.delete(`/users/${id}`)
export const updateUserStatus = (id, status) => request.patch(`/users/${id}/status`, { status })
export const getTeacherStudents = () => request.get('/users/teacher/students')
// 以下导出由原 userEnhanced.js 合并而来
export const getProfile = (id) => request.get(`/users/${id}/profile`)
export const getClassmates = (id) => request.get(`/users/${id}/classmates`)
export const getUserStats = (id) => request.get(`/users/${id}/stats`)
export const searchUsers = (params) => request.get('/users/search', { params })
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
