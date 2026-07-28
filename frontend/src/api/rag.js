import request from './request'

// RAG 知识库接口
// 后端 context-path 已为 /api，RAGController 映射为 /rag，故完整路径为 /api/rag/*
// RAGController 统一返回 ApiResponse 信封，业务数据位于 res.data（已是结构化对象）。

const unwrap = (res) => res?.data

// 智能问答（需登录，任何已登录角色均可调用）
export const ragAsk = (params) => request.post('/rag/ask', null, { params }).then(unwrap)

// 语义召回（需登录）
export const ragRetrieve = (params) => request.post('/rag/retrieve', null, { params }).then(unwrap)

// 文档列表（需登录）
export const ragListDocuments = (courseId) =>
  request.get('/rag/documents', { params: { courseId } }).then(unwrap)

// 新增文档（ADMIN / TEACHER）
export const ragAddDocument = (courseId, documentName, content) =>
  request.post('/rag/documents', content, { params: { courseId, documentName } }).then(unwrap)

// 更新文档（ADMIN / TEACHER）
export const ragUpdateDocument = (documentId, content) =>
  request.put(`/rag/documents/${documentId}`, content).then(unwrap)

// 删除文档（ADMIN / TEACHER）
export const ragDeleteDocument = (documentId) =>
  request.delete(`/rag/documents/${documentId}`).then(unwrap)

// 构建索引（ADMIN / TEACHER）
export const ragBuildIndex = (courseId) => request.post(`/rag/index/build/${courseId}`).then(unwrap)

// 清理索引（ADMIN / TEACHER）
export const ragClearIndex = (courseId) => request.delete(`/rag/index/${courseId}`).then(unwrap)

export default {
  ragAsk,
  ragRetrieve,
  ragListDocuments,
  ragAddDocument,
  ragUpdateDocument,
  ragDeleteDocument,
  ragBuildIndex,
  ragClearIndex,
}
