import request from './request'

/**
 * 学习路径节点 API
 * 后端: LearningPathNodeController @RequestMapping("/learning-path-nodes")
 */
export const learningPathNodeApi = {
  /** 获取学习路径下的节点列表 */
  getList(pathId) { return request.get('/learning-path-nodes', { params: { pathId } }) },
  /** 创建节点 */
  create(data) { return request.post('/learning-path-nodes', data) },
  /** 更新节点信息 */
  update(nodeId, data) { return request.put(`/learning-path-nodes/${nodeId}`, data) },
  /** 删除节点 */
  delete(nodeId) { return request.delete(`/learning-path-nodes/${nodeId}`) },
  /** 更新节点状态 */
  updateStatus(nodeId, status) { return request.patch(`/learning-path-nodes/${nodeId}/status`, null, { params: { status } }) },
  /** 标记节点完成 */
  markComplete(nodeId) { return request.patch(`/learning-path-nodes/${nodeId}/complete`) },
  /** 记录节点得分 */
  recordScore(nodeId, score) { return request.put(`/learning-path-nodes/${nodeId}/score`, { score }) },
  /** 获取节点详情 */
  getDetail(nodeId) { return request.get(`/learning-path-nodes/${nodeId}/detail`) },
}

export default learningPathNodeApi
