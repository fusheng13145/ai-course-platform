import logger from '@/utils/logger'
import { formatTime } from '@/utils/datetime'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import examApi from '@/api/exam'
import proctorApi from '@/api/proctor'
import { useAuthStore } from '@/stores/auth'

/** 阈值默认值：后端返回缺失/非法时回退到这里，避免 NaN 污染界面 */
const DEFAULT_THRESHOLDS = { high: 6, medium: 3, weightLow: 1, weightMedium: 2, weightHigh: 3 }

/**
 * 阈值合法性校验（纯函数，便于单测直接覆盖）。
 *
 * 缺陷修复（P1-9，2026-10-04）：原实现 `Number(t.high ?? 6)` 不做任何校验——
 * 后端返回 `"abc"` 时得到 NaN 直接进 reactive 状态；返回 `high=2, medium=8`
 * （阈值顺序倒置）时照样 PUT 回后端，导致风险分级完全反向。
 *
 * 规则：
 * - 五个字段都必须为**有限数值**（拒绝 NaN / Infinity / 字符串 / null / undefined）
 * - 三个权重必须为正数
 * - `high` 必须严格大于 `medium`（否则高中风险区间倒置）
 *
 * @param {{high,medium,weightLow,weightMedium,weightHigh}} t 待校验阈值
 * @returns {{valid: boolean, message: string}} 校验结果与首个不满足项的中文原因
 */
export function validateThresholds(t) {
  if (!t || typeof t !== 'object') {
    return { valid: false, message: '阈值数据格式不正确' }
  }
  const numeric = ['high', 'medium', 'weightLow', 'weightMedium', 'weightHigh']
  for (const key of numeric) {
    const v = Number(t[key])
    if (!Number.isFinite(v)) {
      return { valid: false, message: `阈值 ${key} 必须为有效数字` }
    }
  }
  for (const key of ['weightLow', 'weightMedium', 'weightHigh']) {
    if (Number(t[key]) <= 0) {
      return { valid: false, message: `权重 ${key} 必须大于 0` }
    }
  }
  if (Number(t.high) <= Number(t.medium)) {
    return { valid: false, message: '高风险阈值必须严格大于中风险阈值' }
  }
  return { valid: true, message: '' }
}

/**
 * 归一化后端阈值：逐字段校验，非法值回退默认值，绝不产出 NaN。
 */
export function normalizeThresholds(t) {
  const pick = (key) => {
    const raw = t?.[key]
    // 缺陷修复（2026-10-04）：`Number(null)` 与 `Number('')` 都是有限数 0，
    // 旧实现把「后端字段缺失/空串」当成合法阈值 0 —— high=0 会让所有会话被判高风险。
    // null/undefined/空串（含纯空白）一律视为缺失并回退默认值。
    if (raw === null || raw === undefined || String(raw).trim() === '') {
      return DEFAULT_THRESHOLDS[key]
    }
    const v = Number(raw)
    return Number.isFinite(v) ? v : DEFAULT_THRESHOLDS[key]
  }
  return {
    high: pick('high'),
    medium: pick('medium'),
    weightLow: pick('weightLow'),
    weightMedium: pick('weightMedium'),
    weightHigh: pick('weightHigh'),
  }
}

/**
 * 考试防作弊治理领域逻辑
 * <p>监考会话风险看板（并行 enrich 风险汇总）、风险详情、违规事件核查（确认/豁免）、阈值加载与保存。</p>
 */
export function useExamGovernance() {
  const loading = ref(false)
  const sessions = ref([])
  const selectedId = ref(null)
  const drawer = ref(false)
  const events = ref([])
  const eventsLoading = ref(false)

  // 角色与阈值
  const authStore = useAuthStore()
  const isAdmin = computed(() => authStore.userRole === 'ADMIN')
  const thresholds = reactive({ ...DEFAULT_THRESHOLDS })
  const thrSaving = ref(false)

  const current = computed(() => sessions.value.find((s) => s.id === selectedId.value) || null)

  // 复核状态展示
  const STATUS_TEXT = { PENDING: '待复核', REVIEWED: '已确认违规', EXEMPTED: '已豁免' }
  const statusText = (s) => STATUS_TEXT[s] || s || '—'
  const statusTag = (s) => (s === 'REVIEWED' ? 'danger' : s === 'EXEMPTED' ? 'info' : 'warning')
  const canReview = (e) => !e.status || e.status === 'PENDING'
  const highRiskCount = computed(() => sessions.value.filter((s) => s.riskLevel === 'HIGH').length)
  const flaggedCount = computed(() => sessions.value.filter((s) => s.status === 'FLAGGED').length)
  const totalEvents = computed(() =>
    sessions.value.reduce((sum, s) => sum + (s.eventCount || 0), 0),
  )

  const TYPE_TEXT = {
    TAB_SWITCH: '切屏',
    FACE_MISSING: '人脸消失',
    MULTIPLE_FACE: '多脸检测',
    MOBILE_DETECTED: '手机检测',
    COPY_PASTE: '复制粘贴',
    FULLSCREEN_EXIT: '退出全屏',
    UNKNOWN: '未知',
  }
  const typeText = (t) => TYPE_TEXT[t] || t
  const levelText = (l) => ({ LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' })[l] || l || '—'
  const riskTag = (l) => (l === 'HIGH' ? 'danger' : l === 'MEDIUM' ? 'warning' : 'info')

  async function loadSessions() {
    loading.value = true
    try {
      const res = await examApi.getActiveExams()
      const list = Array.isArray(res.data) ? res.data : []
      // 并行拉取每个会话的风险汇总，构建治理看板
      const enriched = await Promise.all(
        list.map(async (s) => {
          try {
            const r = await proctorApi.getExamRisk(s.id)
            const rows = Array.isArray(r?.data) ? r.data : []
            const risk = rows[0] || {}
            return {
              ...s,
              riskScore: risk.riskScore ?? 0,
              riskLevel: risk.riskLevel || 'LOW',
              status: risk.status || s.status,
              eventCount: risk.eventCount ?? 0,
              typeCounts: risk.typeCounts || {},
            }
          } catch {
            return { ...s, riskScore: 0, riskLevel: 'LOW', eventCount: 0, typeCounts: {} }
          }
        }),
      )
      sessions.value = enriched
      if (!selectedId.value && enriched.length) selectedId.value = enriched[0].id
    } catch (e) {
      logger.error('加载监考会话失败:', e)
      ElMessage.error('加载监考会话失败')
      sessions.value = []
    } finally {
      loading.value = false
    }
  }

  function selectSession(s) {
    selectedId.value = s.id
  }

  async function openEvents(s) {
    drawer.value = true
    eventsLoading.value = true
    events.value = []
    try {
      // 取该学生对应的 proctor session id，再拉事件
      const stu = await proctorApi.getStudentSession(s.id, s.studentId).catch(() => null)
      const proctorSessionId = stu?.data?.id ?? stu?.id
      if (proctorSessionId) {
        const ev = await proctorApi.getSessionEvents(proctorSessionId).catch(() => ({ data: [] }))
        events.value = Array.isArray(ev?.data) ? ev.data : []
      } else {
        events.value = []
      }
    } catch (e) {
      logger.error(e)
      events.value = []
    } finally {
      eventsLoading.value = false
    }
  }

  async function loadThresholds() {
    try {
      const res = await proctorApi.getThresholds().catch(() => null)
      const t = res?.data
      if (t && typeof t === 'object') {
        // 逐字段校验回退，非法值（NaN/字符串/null）绝不进状态（P1-9）
        Object.assign(thresholds, normalizeThresholds(t))
      }
    } catch (e) {
      logger.error('加载阈值失败:', e)
    }
  }

  async function saveThresholds() {
    // 非管理员：零请求直接返回（P1-9 断言点）
    if (!isAdmin.value) return false
    // 保存前短路校验：NaN / 非数值 / 倒置阈值一律不发 PUT（P1-9）
    const check = validateThresholds(thresholds)
    if (!check.valid) {
      ElMessage.error(check.message)
      return false
    }
    thrSaving.value = true
    try {
      await proctorApi.updateThresholds({
        high: thresholds.high,
        medium: thresholds.medium,
        weightLow: thresholds.weightLow,
        weightMedium: thresholds.weightMedium,
        weightHigh: thresholds.weightHigh,
      })
      ElMessage.success('风险判定阈值已保存')
      return true
    } catch (e) {
      logger.error(e)
      ElMessage.error('保存阈值失败：' + (e?.response?.data?.message || '权限不足或服务异常'))
      return false
    } finally {
      thrSaving.value = false
    }
  }

  const reviewingId = ref(null)
  const reviewAction = ref('')

  async function submitReview(e, action) {
    reviewingId.value = e.id
    reviewAction.value = action
    const note = action === 'REVIEWED' ? '教师确认该违规事件成立' : '教师判定为误报，予以豁免'
    try {
      const res = await proctorApi.reviewEvent(e.id, { status: action, reviewNote: note })
      const updated = res?.data
      if (updated) {
        Object.assign(e, updated)
      }
      ElMessage.success(action === 'REVIEWED' ? '已确认违规' : '已标记豁免')
    } catch (err) {
      logger.error(err)
      ElMessage.error('复核提交失败：' + (err?.response?.data?.message || '服务异常'))
    } finally {
      reviewingId.value = null
      reviewAction.value = ''
    }
  }

  onMounted(() => {
    loadSessions()
    loadThresholds()
  })

  return {
    loading,
    sessions,
    selectedId,
    drawer,
    events,
    eventsLoading,
    isAdmin,
    thresholds,
    thrSaving,
    current,
    highRiskCount,
    flaggedCount,
    totalEvents,
    statusText,
    statusTag,
    canReview,
    typeText,
    levelText,
    riskTag,
    formatTime,
    loadSessions,
    selectSession,
    openEvents,
    loadThresholds,
    saveThresholds,
    submitReview,
    reviewingId,
    reviewAction,
  }
}
