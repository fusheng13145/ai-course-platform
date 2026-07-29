import request from './request'
export const getChapters = (courseId) => request.get(`/chapters/course/${courseId}`)
export const getChapterById = (id) => request.get(`/chapters/${id}`)
export const createChapter = (data) => request.post('/chapters', data)
export const updateChapter = (id, data) => request.put(`/chapters/${id}`, data)
export const deleteChapter = (id) => request.delete(`/chapters/${id}`)
export const batchReorder = (data) => request.put('/batch-reorder', data)
export default {
  getChapters,
  getChapterById,
  createChapter,
  updateChapter,
  deleteChapter,
  batchReorder,
}
