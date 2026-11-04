import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'
/** 用户登录 */
export const login = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/auth/login', data) as Promise<ApiEnvelope<ApiData>>
/** 用户注册 */
export const register = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/auth/register', data) as Promise<ApiEnvelope<ApiData>>
/** 用户登出 */
export const logout = (): Promise<ApiEnvelope<ApiData>> =>
  request.post('/auth/logout') as Promise<ApiEnvelope<ApiData>>
/** 获取当前用户信息 */
export const getCurrentUser = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/auth/me') as Promise<ApiEnvelope<ApiData>>
/** 更新当前用户信息 */
export const updateCurrentUser = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.put('/auth/me', data) as Promise<ApiEnvelope<ApiData>>
/** 修改密码 */
export const changePassword = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.put('/auth/password', data) as Promise<ApiEnvelope<ApiData>>
/** 刷新令牌 */
export const refreshToken = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/auth/refresh', data) as Promise<ApiEnvelope<ApiData>>
/** 获取用户权限 */
export const getPermissions = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/auth/permissions') as Promise<ApiEnvelope<ApiData>>
/** 发送验证码 */
export const sendVerificationCode = (
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/auth/send-code', data) as Promise<ApiEnvelope<ApiData>>
/** 校验验证码（仅校验不消费，供找回密码「验证邮箱」步骤使用） */
export const verifyResetCode = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/auth/verify-code', data) as Promise<ApiEnvelope<ApiData>>
/** 重置密码 */
export const resetPassword = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/auth/reset-password', data) as Promise<ApiEnvelope<ApiData>>
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
  verifyResetCode,
  resetPassword,
}
