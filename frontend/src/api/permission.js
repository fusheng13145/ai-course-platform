import request from './request'
export const getRolePermissions = role => request.get(`/admin/permissions/role/${role}`)
export const getAllPermissions = () => request.get('/admin/permissions/all')
export const createPermission = data => request.post('/admin/permissions/create', data)
export const updatePermission = (id, data) => request.put(`/admin/permissions/update/${id}`, data)
export const deletePermission = id => request.delete(`/admin/permissions/delete/${id}`)
export const togglePermission = id => request.patch(`/admin/permissions/toggle/${id}`)
export default { getRolePermissions, getAllPermissions, createPermission, updatePermission, deletePermission, togglePermission }