import request from './request'
export const getUserList = params => request.get('/users', { params })
export const getUserById = id => request.get(`/users/${id}`)
export const createUser = data => request.post('/users', data)
export const updateUser = (id, data) => request.put(`/users/${id}`, data)
export const deleteUser = id => request.delete(`/users/${id}`)
export const updateUserStatus = (id, status) => request.patch(`/users/${id}/status`, { status })
export default { getUserList, getUserById, createUser, updateUser, deleteUser, updateUserStatus }