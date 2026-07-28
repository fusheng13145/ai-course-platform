import request from './request'
export const enrollCourse = (data) => request.post('/enrollments', data)
export const unenrollCourse = (id) => request.delete(`/enrollments/${id}`)
export const getMyEnrollments = () => request.get('/enrollments/my')
export const approveEnrollment = (id) => request.post(`/enrollments/${id}/approve`)
export const rejectEnrollment = (id) => request.post(`/enrollments/${id}/reject`)
export default {
  enrollCourse,
  unenrollCourse,
  getMyEnrollments,
  approveEnrollment,
  rejectEnrollment,
}
