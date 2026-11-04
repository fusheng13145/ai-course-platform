import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'
/**
 * 导出函数 getResourceList：获取/查询数据
 */
export const getResourceList = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/resources/course/${courseId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getResourceById：获取/查询数据
 */
export const getResourceById = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/resources/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 归一化资源上传入参为 multipart/form-data 的正确形态。
 *
 * 缺陷修复（P0-5，2026-10-04）：`/resources/upload` 声明 `Content-Type: multipart/form-data`，
 * 但仓库里存在**两套调用**：
 * - `useResourceManagement.ts` 传 `FormData`（正确）；
 * - `CourseResourcePanel.vue` 直接传普通对象 `{ file, courseId, description }`
 *   —— axios 在 multipart 头下序列化普通对象，后端 `MultipartFile` / `Long courseId`
 *   取不到任何部件，**必然 400**。
 * 同时 `fd.append('courseId', null)` 会把 null 变成字符串 `"null"` 上报，
 * 后端 `Long` 解析失败同样是 400，且报错信息对用户完全不可读。
 *
 * 现在统一在 API 层收敛：无论调用方传 FormData 还是普通对象，都在此校验并转成
 * 合法 FormData；courseId 缺失/为空/"null" 时**拒发并抛出可读错误**，交由调用方提示。
 *
 * @param {{file, courseId, description?, visibleToAll?}|FormData} data 上传入参
 * @returns {FormData} 合法且字段完整的 FormData
 * @throws {Error} 未选文件或 courseId 非法时抛出（消息可直接展示）
 */
// data 为多态入参（FormData / 普通对象 / 非法值），函数内做运行时判别并防御式处理，
// 故此处保留 any（调用方传 null、非对象等非法输入也应走到「未选文件」分支）。
export function buildUploadForm(data: ApiData): FormData {
  let file = null
  let courseId = null
  let description: FormDataEntryValue | null = ''
  let visibleToAll = null

  if (data instanceof FormData) {
    file = data.get('file')
    courseId = data.get('courseId')
    description = data.get('description') ?? ''
    visibleToAll = data.get('visibleToAll')
  } else if (data && typeof data === 'object') {
    file = data.file ?? null
    courseId = data.courseId ?? null
    description = data.description ?? ''
    visibleToAll = data.visibleToAll ?? null
  }

  if (!file) throw new Error('请选择要上传的文件')

  // courseId 为空、null、undefined 或被 FormData 序列化成字符串 "null"/"undefined"/"" 时，
  // 一律视为非法 —— 这类值上报后后端必然 400。
  const cid = String(courseId ?? '').trim()
  if (!cid || cid === 'null' || cid === 'undefined') {
    throw new Error('请先选择所属课程后再上传')
  }

  const fd = new FormData()
  fd.append('file', file)
  fd.append('courseId', cid)
  fd.append('description', description == null ? '' : String(description))
  if (visibleToAll !== null && visibleToAll !== undefined && visibleToAll !== '') {
    fd.append('visibleToAll', String(visibleToAll))
  }
  return fd
}

/**
 * 导出函数 uploadResource：创建/新增数据
 * 入参可为 FormData 或普通对象，统一由 buildUploadForm 归一化（见 P0-5）。
 */
export const uploadResource = async (
  data: FormData | Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/resources/upload', buildUploadForm(data), {
    headers: { 'Content-Type': 'multipart/form-data' },
  }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 downloadResource：业务逻辑处理
 */
export const downloadResource = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/resources/download/${id}`, { responseType: 'blob' }) as Promise<
    ApiEnvelope<ApiData>
  >
/**
 * 导出函数 deleteResource：删除数据
 */
export const deleteResource = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/resources/${id}`) as Promise<ApiEnvelope<ApiData>>

// 资源管理增强 API
export const getResourceTree = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/resources/tree/${courseId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 searchResources：获取/查询数据
 */
export const searchResources = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/resources/search', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getResourceStats：获取/查询数据
 */
export const getResourceStats = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/resources/stats/course/${courseId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 toggleVisibility：业务逻辑处理
 */
export const toggleVisibility = (
  id: number | string,
  visible: unknown,
): Promise<ApiEnvelope<ApiData>> =>
  request.patch(`/resources/${id}/visibility`, { visibleToAll: !!visible }) as Promise<
    ApiEnvelope<ApiData>
  >
/**
 * 导出函数 getChapterResources：获取/查询数据
 */
export const getChapterResources = (chapterId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/resources/chapter/${chapterId}`) as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getResourceList,
  getResourceById,
  buildUploadForm,
  uploadResource,
  downloadResource,
  deleteResource,
  getResourceTree,
  searchResources,
  getResourceStats,
  toggleVisibility,
  getChapterResources,
}
