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
 * Excel 批量导入章节（后端 TeacherController POST /teacher/chapters/import-excel）
 *
 * <p>父章节以标题引用（支持二级章节）；同课程同标题视为重复并跳过。</p>
 *
 * @param courseId 归属课程
 * @param file     .xlsx 文件对象
 */
export const importChaptersExcel = (courseId, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/teacher/chapters/import-excel?courseId=${courseId}`, formData)
}
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
  importChaptersExcel,
}
