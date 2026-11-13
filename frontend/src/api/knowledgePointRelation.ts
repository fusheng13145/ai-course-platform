import request from './request'
import { addRelation } from './knowledge'

/**
 * 知识点关联关系 API
 * 后端: KnowledgeController @RequestMapping("/knowledge")，关系端点为 /knowledge/relations
 */
export const knowledgeRelationApi = {
  /** 获取关联列表 */
  getList(params: Record<string, unknown>) {
    return request.get('/knowledge/relations', { params })
  },
  /** 创建关联（复用 knowledge.js addRelation，避免重复定义） */
  create: addRelation,
  /** 删除关联（路由参数来源既有 number 也有 string，如 route.params） */
  delete(id: number | string) {
    return request.delete(`/knowledge/relations/${id}`)
  },
  /** 获取课程知识图谱图数据 */
  getGraphData(courseId: number | string) {
    return request.get(`/knowledge/relations/graph/${courseId}`)
  },
  /** 获取某知识点的出边（作为源节点的关联） */
  getSourceRelations(sourceId: number | string) {
    return request.get(`/knowledge/relations/source/${sourceId}`)
  },
  /** 获取某知识点的入边（作为目标节点的关联） */
  getTargetRelations(targetId: number | string) {
    return request.get(`/knowledge/relations/target/${targetId}`)
  },
}

export default knowledgeRelationApi
