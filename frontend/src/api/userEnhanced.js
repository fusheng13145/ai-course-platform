import request from './request'

// 用户增强 API
export const userEnhancedApi = {
  getProfile(id) { return request.get(`/users/${id}/profile`) },
  getClassmates(id) { return request.get(`/users/${id}/classmates`) },
  getUserStats(id) { return request.get(`/users/${id}/stats`) },
  searchUsers(params) { return request.get('/users/search', { params }) },
  getTeacherStudents() { return request.get('/users/teacher/students') },
}

export default userEnhancedApi
