import logger from '@/utils/logger'
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import loginLogApi from '@/api/loginLog'

/**
 * 登录日志面板数据逻辑（拆分自 OperationLogs.vue，2026-08-29）
 */
export function useLoginLogs() {
  const loginLoading = ref(false)
  const loginList = ref([])
  const loginTotal = ref(0)
  const loginPage = ref(1)
  const loginPageSize = ref(15)
  const loginStats = ref({ totalCount: 0, successCount: 0, failedCount: 0, todayCount: 0 })
  const loginFilters = reactive({ username: '', loginStatus: '' })

  function isLoginSuccess(row) {
    const s = row.loginStatus
    return s === 'SUCCESS' || s === 1 || s === '1' || s === true || s === 'success'
  }

  async function loadLoginLogs() {
    loginLoading.value = true
    try {
      const params: Record<string, any> = { page: loginPage.value, size: loginPageSize.value }
      if (loginFilters.username) params.username = loginFilters.username
      if (loginFilters.loginStatus) params.loginStatus = loginFilters.loginStatus
      const res = await loginLogApi.getList(params)
      const data = res.data || res
      loginList.value = data.records || data.list || data.content || (Array.isArray(data) ? data : [])
      loginTotal.value = data.total ?? data.totalElements ?? loginList.value.length
    } catch (e) {
      logger.error('加载登录日志失败:', e)
      loginList.value = []
      loginTotal.value = 0
      ElMessage.error('加载登录日志失败')
    } finally {
      loginLoading.value = false
    }
  }

  async function loadLoginStats() {
    try {
      const res = await loginLogApi.getStats()
      const data = res.data || res
      loginStats.value = {
        totalCount: data.totalCount ?? data.total ?? 0,
        successCount: data.successCount ?? data.success ?? 0,
        failedCount: data.failedCount ?? data.failed ?? 0,
        todayCount: data.todayCount ?? data.today ?? 0,
      }
    } catch (e) {
      logger.error('加载登录统计失败:', e)
    }
  }

  function applyLoginFilter() {
    loginPage.value = 1
    loadLoginLogs()
  }
  function resetLoginFilter() {
    loginFilters.username = ''
    loginFilters.loginStatus = ''
    loginPage.value = 1
    loadLoginLogs()
  }
  function onLoginPageChange(p) {
    loginPage.value = p
    loadLoginLogs()
  }
  function onLoginSizeChange(s) {
    loginPageSize.value = s
    loginPage.value = 1
    loadLoginLogs()
  }

  return {
    loginLoading,
    loginList,
    loginTotal,
    loginPage,
    loginPageSize,
    loginStats,
    loginFilters,
    isLoginSuccess,
    loadLoginLogs,
    loadLoginStats,
    applyLoginFilter,
    resetLoginFilter,
    onLoginPageChange,
    onLoginSizeChange,
  }
}
