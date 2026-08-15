import request from './request'
/** 用户登录 */
export const login = (data) => request.post('/auth/login', data)
/** 用户注册 */
export const register = (data) => request.post('/auth/register', data)
/** 用户登出 */
export const logout = () => request.post('/auth/logout')
/** 获取当前用户信息 */
export const getCurrentUser = () => request.get('/auth/me')
/** 更新当前用户信息 */
export const updateCurrentUser = (data) => request.put('/auth/me', data)
/** 修改密码 */
export const changePassword = (data) => request.put('/auth/password', data)
/** 刷新令牌 */
export const refreshToken = (data) => request.post('/auth/refresh', data)
/** 获取用户权限 */
export const getPermissions = () => request.get('/auth/permissions')
/** 发送验证码 */
export const sendVerificationCode = (data) => request.post('/auth/send-code', data)
/** 重置密码 */
export const resetPassword = (data) => request.post('/auth/reset-password', data)
/**
 * 默认导出对象：模块/组件配置与导出内容
 */
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
