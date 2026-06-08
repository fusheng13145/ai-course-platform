import request from './request'

// 系统配置 API
export const systemConfigApi = {
  getList() { return request.get('/system-configs') },
  getByGroup(group) { return request.get(`/system-configs/group/${group}`) },
  getPublic() { return request.get('/system-configs/public') },
  updateByKey(key, value) { return request.put(`/system-configs/key/${key}`, { configValue: value }) },
  batchUpdate(configs) { return request.post('/system-configs/batch-update', configs) },
  search(keyword) { return request.get('/system-configs/search', { params: { keyword } }) },
}
