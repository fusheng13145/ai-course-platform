import request from './request'

/**
 * 登录日志 API
 * 后端: LoginLogController @RequestMapping("/login-logs")
 */
export const loginLogApi = {
  /** 获取登录日志列表（支持分页） */
  getList(params) {
    return request.get('/login-logs', { params })
  },
  /** 获取登录统计数据 */
  getStats() {
    return request.get('/login-logs/stats')
  },
  /** 获取登录趋势（按日统计） */
  getTrends(params) {
    return request.get('/login-logs/trends', { params })
  },
  /** 获取最近登录记录 */
  getRecent(limit) {
    return request.get('/login-logs/recent', { params: { limit } })
  },
  /** 获取失败登录记录 */
  getFailures(params) {
    return request.get('/login-logs/failures', { params })
  },
  /** 获取某用户的登录历史 */
  getUserHistory(userId, params) {
    return request.get(`/login-logs/user/${userId}`, { params })
  },
  /** 异常登录检测 */
  getAbnormal(userId) {
    return request.get(`/login-logs/abnormal/${userId}`)
  },
}

export default loginLogApi
