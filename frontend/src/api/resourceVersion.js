import request from './request'

/**
 * 资源版本管理 API
 * 后端: ResourceVersionController @RequestMapping("/resource-versions")
 */
export const resourceVersionApi = {
  /** 获取资源版本列表 */
  getList(resourceId) { return request.get('/resource-versions', { params: { resourceId } }) },
  /** 创建新版本 */
  create(data) { return request.post('/resource-versions', data) },
  /** 获取版本详情 */
  getById(id) { return request.get(`/resource-versions/${id}`) },
  /** 激活指定版本 */
  activate(id) { return request.patch(`/resource-versions/${id}/activate`) },
  /** 删除版本 */
  delete(id) { return request.delete(`/resource-versions/${id}`) },
  /** 获取资源的当前激活版本 */
  getActive(resourceId) { return request.get(`/resource-versions/resource/${resourceId}/active`) },
  /** 获取资源版本历史 */
  getHistory(resourceId) { return request.get(`/resource-versions/history/${resourceId}`) },
}

export default resourceVersionApi
