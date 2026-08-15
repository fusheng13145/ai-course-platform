import request from './request'
/**
 * 导出函数 enrollCourse：业务逻辑处理
 */
export const enrollCourse = (data) => request.post('/enrollments', data)
/**
 * 导出函数 unenrollCourse：业务逻辑处理
 */
export const unenrollCourse = (id) => request.delete(`/enrollments/${id}`)
/**
 * 导出函数 getMyEnrollments：获取/查询数据
 */
export const getMyEnrollments = () => request.get('/enrollments/my')
/**
 * 导出函数 approveEnrollment：业务逻辑处理
 */
export const approveEnrollment = (id) => request.post(`/enrollments/${id}/approve`)
/**
 * 导出函数 rejectEnrollment：业务逻辑处理
 */
export const rejectEnrollment = (id) => request.post(`/enrollments/${id}/reject`)
/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  enrollCourse,
  unenrollCourse,
  getMyEnrollments,
  approveEnrollment,
  rejectEnrollment,
}
