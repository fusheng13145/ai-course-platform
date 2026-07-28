import request from './request'

// AI 供应商配置
// 复用系统通用配置 KV 存储（system_configs 表，config_group = 'AI'）。
// 后端 SystemServiceImpl.saveConfig 为 upsert：键不存在时自动插入，存在时更新；
// 因此无需新增后端接口即可实现服务端持久化。
export const aiSupplierApi = {
  // 读取 AI 分组下的全部配置
  getConfigs() {
    return request.get('/system-configs/group/AI')
  },
  // 保存单项配置（upsert 到 system_configs，group = 'AI'）
  // 对应后端 POST /admin/system/configs  { key, value, group }
  saveConfig(key, value) {
    return request.post('/admin/system/configs', { key, value, group: 'AI' })
  },
  // 查询当前运行时供应商与配置状态（无需重启）
  getCurrent() {
    return request.get('/admin/ai/current')
  },
  // 运行时热切换底层大模型（即时生效，无需重启）
  // 对应后端 POST /admin/ai/provider  { provider }
  switchProvider(provider) {
    return request.post('/admin/ai/provider', { provider })
  },
  // 连通性测试：用当前供应商做一次最小补全
  testConnection() {
    return request.get('/admin/ai/test')
  },
}

export default aiSupplierApi
