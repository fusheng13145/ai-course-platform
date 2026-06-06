import request from './request'
export const login = data => request.post('/auth/login', data)
export const register = data => request.post('/auth/register', data)
export const logout = () => request.post('/auth/logout')
export const getCurrentUser = () => request.get('/auth/me')
export const updateCurrentUser = data => request.put('/auth/me', data)
export const changePassword = data => request.post('/auth/change-password', data)
export const getPermissions = () => request.get('/auth/permissions')
export default { login, register, logout, getCurrentUser, updateCurrentUser, changePassword, getPermissions }