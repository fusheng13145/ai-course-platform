import request from './request'

// 后端 KnowledgeController @RequestMapping("/knowledge")，知识点端点合并为 /knowledge/points
export const getKnowledgePoints = (courseId) =>
  request.get('/knowledge/points', { params: { courseId } })
/**
 * 导出函数 createKnowledgePoint：创建/新增数据
 */
export const createKnowledgePoint = (data) => request.post('/knowledge/points', data)
/**
 * 导出函数 updateKnowledgePoint：更新数据
 */
export const updateKnowledgePoint = (id, data) => request.put(`/knowledge/points/${id}`, data)
/**
 * 导出函数 deleteKnowledgePoint：删除数据
 */
export const deleteKnowledgePoint = (id) => request.delete(`/knowledge/points/${id}`)
/**
 * 导出函数 getKnowledgeGraph：获取/查询数据
 */
export const getKnowledgeGraph = (courseId) =>
  request.get('/knowledge/points/graph', { params: { courseId } })
/**
 * 导出函数 addRelation：创建/新增数据
 */
export const addRelation = (data) => request.post('/knowledge/relations', data)

// 知识点增强 API
export const getTree = (courseId) => request.get(`/knowledge/points/tree/${courseId}`)
/**
 * 导出函数 getChildren：获取/查询数据
 */
export const getChildren = (id) => request.get(`/knowledge/points/${id}/children`)
/**
 * 导出函数 getPrerequisites：获取/查询数据
 */
export const getPrerequisites = (id) => request.get(`/knowledge/points/${id}/prerequisites`)
/**
 * 导出函数 getSubsequent：获取/查询数据
 */
export const getSubsequent = (id) => request.get(`/knowledge/points/${id}/successors`)
/**
 * 导出函数 getMastery：获取/查询数据
 */
export const getMastery = (pointId, userId) =>
  request.get(`/knowledge/points/${pointId}/mastery/${userId}`)
/**
 * 导出函数 getRootPoints：获取/查询数据
 */
export const getRootPoints = (courseId) => request.get(`/knowledge/points/root/${courseId}`)

// 知识图谱增强 API（对应后端 KnowledgeController 端点，重构时曾缺失，2026-08-30 补回）
/**
 * 知识图谱概览统计（GET /knowledge/overview）
 * @param {number} courseId 可选，按课程过滤
 */
export const getKnowledgeOverview = (courseId) =>
  request.get('/knowledge/overview', { params: { courseId } })
/**
 * AI 抽取知识点与关系（POST /knowledge/extract，教师/管理员权限）
 * @param {{courseId: number, content: string}} data 课程内容
 */
export const extractKnowledgePoints = (data) => request.post('/knowledge/extract', data)
/**
 * 课程知识图谱可视化图数据（GET /knowledge/relations/graph/{courseId}，nodes + links）
 * @param {number} courseId 课程ID
 */
export const getRelationGraphData = (courseId) =>
  request.get(`/knowledge/relations/graph/${courseId}`)

/**
 * 学习路径规划（POST /knowledge/path/plan）
 * 知识点最优学习路径（BFS 多跳）。后端在图谱引擎（8001）未启用/不可达时，
 * 由消费侧本地 BFS 兜底（基于 knowledge_point_relations 无向邻接），始终返回真实路径数据。
 * @param {number} sourceId 起点知识点ID
 * @param {number} targetId 目标知识点ID
 * @param {number} [maxHops] 最大搜索跳数（可选，缺省由后端取默认）
 * @returns {Promise} 信封 { code, message, data: KnowledgePathPlanVO }
 */
export const planLearningPath = (sourceId, targetId, maxHops?) =>
  request.post('/knowledge/path/plan', { sourceId, targetId, maxHops })

/**
 * Excel 批量导入知识点（后端 TeacherController POST /teacher/knowledge-points/import-excel）
 *
 * <p>父知识点以名称引用；同课程同名称视为重复并跳过。</p>
 *
 * @param {number} courseId 归属课程
 * @param {File}   file     .xlsx 文件对象
 */
export const importKnowledgePointsExcel = (courseId, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/teacher/knowledge-points/import-excel?courseId=${courseId}`, formData)
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
  importKnowledgePointsExcel,
}
