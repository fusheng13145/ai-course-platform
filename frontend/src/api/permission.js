import request from './request'
/**
 * 导出函数 getRolePermissions：获取/查询数据
 */
export const getRolePermissions = (role) => request.get(`/admin/permissions/role/${role}`)
/**
 * 导出函数 getAllPermissions：获取/查询数据
 */
export const getAllPermissions = () => request.get('/admin/permissions/all')
/**
 * 导出函数 createPermission：创建/新增数据
 */
export const createPermission = (data) => request.post('/admin/permissions/create', data)
/**
 * 导出函数 updatePermission：更新数据
 */
export const updatePermission = (id, data) => request.put(`/admin/permissions/update/${id}`, data)
/**
 * 导出函数 deletePermission：删除数据
 */
export const deletePermission = (id) => request.delete(`/admin/permissions/delete/${id}`)
/**
 * 导出函数 togglePermission：业务逻辑处理
 */
export const togglePermission = (id) => request.patch(`/admin/permissions/toggle/${id}`)
/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getRolePermissions,
  getAllPermissions,
  createPermission,
  updatePermission,
  deletePermission,
  togglePermission,
}
