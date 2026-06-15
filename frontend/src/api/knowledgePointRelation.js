import request from './request'

/**
 * 知识点关联关系 API
 * 后端: KnowledgePointRelationController @RequestMapping("/knowledge-relations")
 */
export const knowledgeRelationApi = {
  /** 获取关联列表 */
  getList(params) { return request.get('/knowledge-relations', { params }) },
  /** 创建关联 */
  create(data) { return request.post('/knowledge-relations', data) },
  /** 删除关联 */
  delete(id) { return request.delete(`/knowledge-relations/${id}`) },
  /** 获取课程知识图谱图数据 */
  getGraphData(courseId) { return request.get(`/knowledge-relations/graph/${courseId}`) },
  /** 获取某知识点的出边（作为源节点的关联） */
  getSourceRelations(sourceId) { return request.get(`/knowledge-relations/source/${sourceId}`) },
  /** 获取某知识点的入边（作为目标节点的关联） */
  getTargetRelations(targetId) { return request.get(`/knowledge-relations/target/${targetId}`) },
}

export default knowledgeRelationApi
