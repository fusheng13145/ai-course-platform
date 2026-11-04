import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

// 后端 KnowledgeController @RequestMapping("/knowledge")，知识点端点合并为 /knowledge/points
export const getKnowledgePoints = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get('/knowledge/points', { params: { courseId } }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 createKnowledgePoint：创建/新增数据
 */
export const createKnowledgePoint = (
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/knowledge/points', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 updateKnowledgePoint：更新数据
 */
export const updateKnowledgePoint = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/knowledge/points/${id}`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 deleteKnowledgePoint：删除数据
 */
export const deleteKnowledgePoint = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/knowledge/points/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getKnowledgeGraph：获取/查询数据
 */
export const getKnowledgeGraph = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get('/knowledge/points/graph', { params: { courseId } }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 addRelation：创建/新增数据
 */
export const addRelation = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/knowledge/relations', data) as Promise<ApiEnvelope<ApiData>>

// 知识点增强 API
export const getTree = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/knowledge/points/tree/${courseId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getChildren：获取/查询数据
 */
export const getChildren = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/knowledge/points/${id}/children`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getPrerequisites：获取/查询数据
 */
export const getPrerequisites = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/knowledge/points/${id}/prerequisites`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getSubsequent：获取/查询数据
 */
export const getSubsequent = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/knowledge/points/${id}/successors`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getMastery：获取/查询数据
 */
export const getMastery = (
  pointId: number | string,
  userId: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/knowledge/points/${pointId}/mastery/${userId}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getRootPoints：获取/查询数据
 */
export const getRootPoints = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/knowledge/points/root/${courseId}`) as Promise<ApiEnvelope<ApiData>>

// 知识图谱增强 API（对应后端 KnowledgeController 端点，重构时曾缺失，2026-08-30 补回）
/**
 * 知识图谱概览统计（GET /knowledge/overview）
 * @param {number} courseId 可选，按课程过滤
 */
export const getKnowledgeOverview = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get('/knowledge/overview', { params: { courseId } }) as Promise<ApiEnvelope<ApiData>>
/**
 * AI 抽取知识点与关系（POST /knowledge/extract，教师/管理员权限）
 * @param {{courseId: number, content: string}} data 课程内容
 */
export const extractKnowledgePoints = (
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/knowledge/extract', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 课程知识图谱可视化图数据（GET /knowledge/relations/graph/{courseId}，nodes + links）
 * @param {number} courseId 课程ID
 */
export const getRelationGraphData = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/knowledge/relations/graph/${courseId}`) as Promise<ApiEnvelope<ApiData>>

/**
 * 学习路径规划（POST /knowledge/path/plan）
 * 知识点最优学习路径（BFS 多跳）。后端在图谱引擎（8001）未启用/不可达时，
 * 由消费侧本地 BFS 兜底（基于 knowledge_point_relations 无向邻接），始终返回真实路径数据。
 * @param {number} sourceId 起点知识点ID
 * @param {number} targetId 目标知识点ID
 * @param {number} [maxHops] 最大搜索跳数（可选，缺省由后端取默认）
 * @returns {Promise} 信封 { code, message, data: KnowledgePathPlanVO }
 */
export const planLearningPath = (
  sourceId: number | string,
  targetId: number | string,
  maxHops?: unknown,
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/knowledge/path/plan', { sourceId, targetId, maxHops }) as Promise<
    ApiEnvelope<ApiData>
  >

/**
 * 知识图谱摄取：把课程知识点/关系同步至图谱引擎（POST /knowledge/ingest，教师/管理员权限）
 * <p>引擎未接入/不可达时后端返回消费侧本地确认（data.mode=LOCAL），端点始终可用，
 * 与 planLearningPath 同款降级策略。</p>
 * @param {number} courseId 归属课程
 * @returns {Promise} 信封 { code, message, data: { mode, note, syncedPoints, ... } }
 */
export const ingestKnowledgeGraph = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post('/knowledge/ingest', null, { params: { courseId } }) as Promise<ApiEnvelope<ApiData>>

/**
 * 获取课程知识子图（GET /knowledge/subgraph/{courseId}，教师/管理员权限）
 * <p>引擎未接入时后端返回消费侧 MySQL 直读结果（data.mode=LOCAL），端点始终可用。</p>
 * @param {number} courseId 归属课程
 * @param {number} [maxHops] 子图最大跳数（可选，缺省由后端取默认）
 * @returns {Promise} 信封 { code, message, data: { mode, note, nodes[], edges[] } }
 */
export const getKnowledgeSubGraph = (
  courseId: number | string,
  maxHops?: unknown,
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/knowledge/subgraph/${courseId}`, { params: { maxHops } }) as Promise<
    ApiEnvelope<ApiData>
  >

/**
 * Excel 批量导入知识点（后端 TeacherController POST /teacher/knowledge-points/import-excel）
 *
 * <p>父知识点以名称引用；同课程同名称视为重复并跳过。</p>
 *
 * @param {number} courseId 归属课程
 * @param {File}   file     .xlsx 文件对象
 */
export const importKnowledgePointsExcel = (
  courseId: number | string,
  file: Blob,
): Promise<ApiEnvelope<ApiData>> => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(
    `/teacher/knowledge-points/import-excel?courseId=${courseId}`,
    formData,
  ) as Promise<ApiEnvelope<ApiData>>
}

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getKnowledgePoints,
  createKnowledgePoint,
  updateKnowledgePoint,
  deleteKnowledgePoint,
  getKnowledgeGraph,
  addRelation,
  getTree,
  getChildren,
  getPrerequisites,
  getSubsequent,
  getMastery,
  getRootPoints,
  getKnowledgeOverview,
  extractKnowledgePoints,
  getRelationGraphData,
  planLearningPath,
  ingestKnowledgeGraph,
  getKnowledgeSubGraph,
  importKnowledgePointsExcel,
}
