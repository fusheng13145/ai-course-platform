import request from './request'
import { unwrap, type ApiData } from '@/utils/response'

// RAG 知识库接口
// 后端 context-path 已为 /api，RAGController 映射为 /rag，故完整路径为 /api/rag/*
// RAGController 统一返回 ApiResponse 信封，业务数据位于 res.data（已是结构化对象）。

// 智能问答（需登录，任何已登录角色均可调用）
export const ragAsk = (params: Record<string, unknown> = {}): Promise<ApiData> =>
  request.post('/rag/ask', null, { params }).then(unwrap) as Promise<ApiData>

// 语义召回（需登录）
export const ragRetrieve = (params: Record<string, unknown> = {}): Promise<ApiData> =>
  request.post('/rag/retrieve', null, { params }).then(unwrap) as Promise<ApiData>

// 文档列表（需登录）
export const ragListDocuments = (courseId: number | string): Promise<ApiData> =>
  request.get('/rag/documents', { params: { courseId } }).then(unwrap) as Promise<ApiData>

// 新增文档（ADMIN / TEACHER）
export const ragAddDocument = (
  courseId: number | string,
  documentName: unknown,
  content: unknown,
): Promise<ApiData> =>
  request
    .post('/rag/documents', content, { params: { courseId, documentName } })
    .then(unwrap) as Promise<ApiData>

// 更新文档（ADMIN / TEACHER）
export const ragUpdateDocument = (
  documentId: number | string,
  content: unknown,
): Promise<ApiData> =>
  request.put(`/rag/documents/${documentId}`, content).then(unwrap) as Promise<ApiData>

// 删除文档（ADMIN / TEACHER）
export const ragDeleteDocument = (documentId: number | string): Promise<ApiData> =>
  request.delete(`/rag/documents/${documentId}`).then(unwrap) as Promise<ApiData>

// 构建索引（ADMIN / TEACHER）
export const ragBuildIndex = (courseId: number | string): Promise<ApiData> =>
  request.post(`/rag/index/build/${courseId}`).then(unwrap) as Promise<ApiData>

// 清理索引（ADMIN / TEACHER）
export const ragClearIndex = (courseId: number | string): Promise<ApiData> =>
  request.delete(`/rag/index/${courseId}`).then(unwrap) as Promise<ApiData>

/**
 * 从课程结构化资料（课程简介 / 章节 / 资源）一键构建课程知识库并重建索引（ADMIN / TEACHER）。
 * 幂等：知识库中已存在的同名文档会被跳过，重复执行不会产生重复语料。
 * 响应 data 关键字段：mode（ENGINE / LOCAL / UNAVAILABLE）、total、added、skipped、
 * indexedChunks、documents、note；引擎不可用时附 error / code=503，由调用方按「失败可见」展示降级提示。
 */
export const ragBuildCourseKnowledgeBase = (courseId: number | string): Promise<ApiData> =>
  request.post(`/rag/course/${courseId}/build`).then(unwrap) as Promise<ApiData>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  ragAsk,
  ragRetrieve,
  ragListDocuments,
  ragAddDocument,
  ragUpdateDocument,
  ragDeleteDocument,
  ragBuildIndex,
  ragClearIndex,
  ragBuildCourseKnowledgeBase,
}
