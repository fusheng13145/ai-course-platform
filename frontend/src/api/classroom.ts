import request from './request'
import { fetchAllPages, type ApiEnvelope, type ApiData } from '@/utils/response'
/**
 * 导出函数 getClassroomList：获取/查询数据
 */
export const getClassroomList = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/classrooms', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getClassroomById：获取/查询数据
 */
export const getClassroomById = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/classrooms/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 createClassroom：创建/新增数据
 */
export const createClassroom = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/classrooms', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updateClassroom：更新数据
 */
export const updateClassroom = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/classrooms/${id}`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 deleteClassroom：删除数据
 */
export const deleteClassroom = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/classrooms/${id}`) as Promise<ApiEnvelope<ApiData>>

// 班级管理增强 API
export const getDetail = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/classrooms/${id}/detail`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getStudents：获取/查询数据
 */
export const getStudents = (
  id: number | string,
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/classrooms/${id}/students`, { params }) as Promise<ApiEnvelope<ApiData>>
/** 获取班级所有匹配学生，供完整成员管理列表使用。 */
export const getAllStudents = (
  id: number | string,
  filters: Record<string, unknown> = {},
): Promise<ApiData[]> =>
  fetchAllPages((page: number, size: number) =>
    getStudents(id, { ...filters, page, size }),
  ) as Promise<ApiData[]>
/**
 * 导出函数 addStudent：创建/新增数据
 */
export const addStudent = (
  classroomId: number | string,
  studentId: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/classrooms/${classroomId}/students/${studentId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 removeStudent：删除数据
 */
export const removeStudent = (
  classroomId: number | string,
  studentId: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/classrooms/${classroomId}/students/${studentId}`) as Promise<
    ApiEnvelope<ApiData>
  >
/**
 * 导出函数 getStats：获取/查询数据
 */
export const getStats = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/classrooms/${id}/stats`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getMyClassrooms：获取/查询数据
 */
export const getMyClassrooms = (advisorId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get('/classrooms/my', { params: { advisorId } }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 search：获取/查询数据
 */
export const search = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/classrooms/search', { params }) as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getClassroomList,
  getClassroomById,
  createClassroom,
  updateClassroom,
  deleteClassroom,
  getDetail,
  getStudents,
  getAllStudents,
  addStudent,
  removeStudent,
  getStats,
  getMyClassrooms,
  search,
}
