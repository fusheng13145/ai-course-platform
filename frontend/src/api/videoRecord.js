import request from './request'

// 视频学习记录 API — 对接后端 LearningRecordController (/learning-records)
export const videoRecordApi = {
  // 上报视频观看进度/学习进度
  updateProgress(data) { return request.put('/learning-records/progress', data) },
  // 获取视频播放位置（断点续播）
  getPosition(resourceId) { return request.get(`/learning-records/resource/${resourceId}`) },
  // 获取课程视频学习列表
  getCourseVideos(courseId) { return request.get(`/learning-records/course/${courseId}`) },
  // 视频学习统计
  getVideoStats() { return request.get('/learning-records/stats') },
  // 获取章节学习进度
  getChapterProgress(courseId) { return request.get(`/learning-records/course/${courseId}/chapters`) },
  // 学习热力图数据
  getHeatmap(params) { return request.get('/learning-records/heatmap', { params }) },
}

export default videoRecordApi
