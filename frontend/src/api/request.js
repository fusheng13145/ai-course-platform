import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const service = axios.create({
  baseURL: '/api',
  timeout: 30000
})

service.interceptors.request.use(config => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}, error => Promise.reject(error))

service.interceptors.response.use(
  response => {
    const data = response.data
    if (data.code !== undefined && data.code !== 200 && data.code !== 0) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  error => {
    const status = error.response?.status
    const msg = error.response?.data?.message || error.message || '请求失败'

    if (status === 401) {
      const authStore = useAuthStore()
      localStorage.removeItem('token'); localStorage.removeItem('user')
      sessionStorage.removeItem('token'); sessionStorage.removeItem('user')
      authStore.token = ''; authStore.user = null
      ElMessage.error('登录已过期，请重新登录')
      router.push('/login')
    } else if (status === 403) {
      ElMessage.error('权限不足')
      router.push('/403')
    } else if (status >= 500) {
      ElMessage.error('服务器内部错误')
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default service