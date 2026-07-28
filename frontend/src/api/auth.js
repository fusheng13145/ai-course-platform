import request from './request'
export const login = (data) => request.post('/auth/login', data)
export const register = (data) => request.post('/auth/register', data)
export const logout = () => request.post('/auth/logout')
export const getCurrentUser = () => request.get('/auth/me')
export const updateCurrentUser = (data) => request.put('/auth/me', data)
export const changePassword = (data) => request.put('/auth/password', data)
export const refreshToken = (data) => request.post('/auth/refresh', data)
export const getPermissions = () => request.get('/auth/permissions')
export const sendVerificationCode = (data) => request.post('/auth/send-code', data)
export const resetPassword = (data) => request.post('/auth/reset-password', data)
export default {
  login,
  register,
  logout,
  getCurrentUser,
  updateCurrentUser,
  changePassword,
  refreshToken,
  getPermissions,
  sendVerificationCode,
  resetPassword,
}
