import request from './request'

// 查重检测 API
export const plagiarismCheckApi = {
  createCheck(data) { return request.post('/plagiarism-checks', data) },
  executeCheck(id) { return request.post(`/plagiarism-checks/${id}/execute`) },
  getResult(id) { return request.get(`/plagiarism-checks/${id}`) },
  getMatches(id, threshold) { return request.get(`/plagiarism-checks/${id}/matches`, { params: { threshold } }) },
  getSubmissionChecks(submissionId) { return request.get(`/plagiarism-checks/submission/${submissionId}`) },
}
