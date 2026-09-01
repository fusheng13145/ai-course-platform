import request from './request'
/**
 * 导出函数 getChapters：获取/查询数据
 */
export const getChapters = (courseId) => request.get(`/chapters/course/${courseId}`)
/**
 * 导出函数 getChapterById：获取/查询数据
 */
export const getChapterById = (id) => request.get(`/chapters/${id}`)
/**
 * 导出函数 createChapter：创建/新增数据
 */
export const createChapter = (data) => request.post('/chapters', data)
/**
 * 导出函数 updateChapter：更新数据
 */
export const updateChapter = (id, data) => request.put(`/chapters/${id}`, data)
/**
 * 导出函数 deleteChapter：删除数据
 */
export const deleteChapter = (id) => request.delete(`/chapters/${id}`)
/**
 * 导出函数 batchReorder：业务逻辑处理
 */
export const batchReorder = (data) => request.put('/batch-reorder', data)
/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getChapters,
  getChapterById,
  createChapter,
  updateChapter,
  deleteChapter,
  batchReorder,
}
