import logger from '@/utils/logger'
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import operationLogApi from '@/api/operationLog'

/**
 * 操作日志面板数据逻辑（拆分自 OperationLogs.vue，2026-08-29）
 */
export function useOperationLogs() {
  const loading = ref(false)
  const logList = ref([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(15)
  const stats = ref({ totalCount: 0, successCount: 0, failedCount: 0 })

  const filters = reactive({ username: '', module: '', action: '', status: '' })

  const moduleOptions = [
    { label: '用户管理', value: 'USER' },
    { label: '课程管理', value: 'COURSE' },
    { label: '考试管理', value: 'EXAM' },
    { label: '成绩管理', value: 'GRADE' },
    { label: '题库管理', value: 'QUESTION' },
    { label: '通知管理', value: 'NOTIFICATION' },
    { label: '系统配置', value: 'SYSTEM' },
    { label: '权限管理', value: 'PERMISSION' },
    { label: '认证登录', value: 'AUTH' },
  ]
  const actionOptions = [
    { label: '新增', value: 'CREATE' },
    { label: '修改', value: 'UPDATE' },
    { label: '删除', value: 'DELETE' },
    { label: '查询', value: 'QUERY' },
    { label: '导入', value: 'IMPORT' },
    { label: '导出', value: 'EXPORT' },
    { label: '登录', value: 'LOGIN' },
    { label: '登出', value: 'LOGOUT' },
  ]
  const moduleMap = Object.fromEntries(moduleOptions.map((m) => [m.value, m.label]))
  const actionMap = Object.fromEntries(actionOptions.map((a) => [a.value, a.label]))
  function moduleLabel(v) {
    return moduleMap[v] || v || '—'
  }
  function actionLabel(v) {
    return actionMap[v] || v || '—'
  }

  const successRate = computed(() => {
    const t = stats.value.totalCount || 0
    if (!t) return 0
    return Math.round(((stats.value.successCount || 0) / t) * 100)
  })

  function isSuccess(row) {
    const s = row.status
    if (s == null) return true
    return s === 'SUCCESS' || s === 1 || s === '1' || s === true || s === 'success'
  }

  async function loadLogs() {
    loading.value = true
    try {
      const params = { page: page.value, size: pageSize.value }
      if (filters.username) params.username = filters.username
      if (filters.module) params.module = filters.module
      if (filters.action) params.action = filters.action
      if (filters.status) params.status = filters.status
      const res = await operationLogApi.getOperationLogs(params)
      const data = res.data || res
      logList.value = data.records || data.list || data.content || (Array.isArray(data) ? data : [])
      total.value = data.total ?? data.totalElements ?? logList.value.length
    } catch (e) {
      logger.error('加载操作日志失败:', e)
      logList.value = []
      total.value = 0
      ElMessage.error('加载操作日志失败')
    } finally {
      loading.value = false
    }
  }

  async function loadStats() {
    try {
      const res = await operationLogApi.getOperationLogStatistics()
      const data = res.data || res
      stats.value = {
        totalCount: data.totalCount ?? data.total ?? 0,
        successCount: data.successCount ?? data.success ?? 0,
        failedCount: data.failedCount ?? data.failed ?? 0,
      }
    } catch (e) {
      logger.error('加载日志统计失败:', e)
    }
  }

  function applyFilter() {
    page.value = 1
    loadLogs()
  }
  function resetFilter() {
    filters.username = ''
    filters.module = ''
    filters.action = ''
    filters.status = ''
    page.value = 1
    loadLogs()
  }
  function onPageChange(p) {
    page.value = p
    loadLogs()
  }
  function onSizeChange(s) {
    pageSize.value = s
    page.value = 1
    loadLogs()
  }

  return {
    loading,
    logList,
    total,
    page,
    pageSize,
    stats,
    filters,
    moduleOptions,
    actionOptions,
    moduleLabel,
    actionLabel,
    successRate,
    isSuccess,
    loadLogs,
    loadStats,
    applyFilter,
    resetFilter,
    onPageChange,
    onSizeChange,
  }
}
