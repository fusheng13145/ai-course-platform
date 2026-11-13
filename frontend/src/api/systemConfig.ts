import request from './request'
import { type ApiData } from '@/utils/response'

// 系统配置 API
export const systemConfigApi = {
  getList() {
    return request.get('/system-configs')
  },
  getByGroup(group: string) {
    return request.get(`/system-configs/group/${group}`)
  },
  getPublic() {
    return request.get('/system-configs/public')
  },
  updateByKey(key: string, value: ApiData) {
    return request.put(`/system-configs/key/${key}`, { value: value })
  },
  batchUpdate(configs: ApiData) {
    return request.post('/system-configs/batch-update', configs)
  },
  search(keyword: string) {
    return request.get('/system-configs/search', { params: { keyword } })
  },
  testEmail() {
    return request.post('/system-configs/test-email')
  },
}

export default systemConfigApi
