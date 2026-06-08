import request from './request'

// 视频学习记录 API
export const videoRecordApi = {
  // 上报视频观看进度
  updateProgress(data) { return request.post('/video-records/progress', data) },
  // 获取视频播放位置（断点续播）
  getPosition(resourceId) { return request.get(`/video-records/resource/${resourceId}`) },
  // 获取课程视频学习列表
  getCourseVideos(courseId) { return request.get(`/video-records/course/${courseId}`) },
  // 视频学习统计
  getVideoStats() { return request.get('/video-records/stats') },
}
