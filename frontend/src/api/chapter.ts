import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'
/**
 * 导出函数 getChapters：获取/查询数据
 */
export const getChapters = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/chapters/course/${courseId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getChapterById：获取/查询数据
 */
export const getChapterById = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/chapters/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 createChapter：创建/新增数据
 */
export const createChapter = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/chapters', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updateChapter：更新数据
 */
export const updateChapter = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/chapters/${id}`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 deleteChapter：删除数据
 */
export const deleteChapter = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/chapters/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 batchReorder：业务逻辑处理
 */
export const batchReorder = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.put('/batch-reorder', data) as Promise<ApiEnvelope<ApiData>>
/**
 * Excel 批量导入章节（后端 TeacherController POST /teacher/chapters/import-excel）
 *
 * <p>父章节以标题引用（支持二级章节）；同课程同标题视为重复并跳过。</p>
 *
 * @param courseId 归属课程
 * @param file     .xlsx 文件对象
 */
export const importChaptersExcel = (
  courseId: number | string,
  file: Blob,
): Promise<ApiEnvelope<ApiData>> => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/teacher/chapters/import-excel?courseId=${courseId}`, formData) as Promise<
    ApiEnvelope<ApiData>
  >
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
