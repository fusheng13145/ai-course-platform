import { ref, computed, onMounted } from 'vue'
import logger from '@/utils/logger'
import adminApi from '@/api/admin'

/**
 * 按课程聚合的一行 AI 用量（与后端 `AiUsageByCourseVO` 字段一一对应）。
 * <p>`courseId` 为 null 的行是「未绑定课程」的调用（通识问答、作文批改等）——
 * 该行必须与课程行一起展示，丢掉它总量就对不上。</p>
 */
export interface AiUsageCourseRow {
  courseId?: number | null
  courseName?: string | null
  calls?: number
  tokens?: number
  avgDurationMs?: number
  successCalls?: number
  successRate?: number | null
}

/** 后端 `GET /admin/statistics/ai-usage/by-course` 的响应体。 */
export interface AiUsageStats {
  rangeDays?: number
  since?: string
  until?: string
  /** 采集口径声明（后端下发）：只统计自 2026-10-13 埋点上线后的调用 */
  collectionNote?: string
  totalCalls?: number
  totalTokens?: number
  distinctCourses?: number
  unboundCalls?: number
  avgDurationMs?: number
  successRate?: number | null
  courses?: AiUsageCourseRow[]
}

/**
 * 响应体解包：request 拦截器返回的已是 body 本体（{code, message, data}），
 * 业务数据挂在 `data` 上；个别形态直接是裸数据，故取不到 data 时退化用整个响应体。
 */
function unwrapStats(res: unknown): AiUsageStats {
  if (res && typeof res === 'object') {
    const holder = res as { data?: unknown }
    const inner = holder.data
    if (inner && typeof inner === 'object') return inner as AiUsageStats
    return holder as AiUsageStats
  }
  return {}
}

/** 可选的时间窗档位（天）；后端对越界值会收敛到 1–365。 */
export const AI_USAGE_RANGES = [
  { key: '7', label: '近 7 天' },
  { key: '30', label: '近 30 天' },
  { key: '90', label: '近 90 天' },
] as const

/**
 * 跨课程 AI 统计（管理端）。
 *
 * <p><b>诚实边界</b>：数据源 `ai_call_logs` 自 2026-10-13 埋点上线才有数据，
 * 历史调用没有留痕、**不可回填**，故本页不做任何"趋势回推"；`collectionNote` 由后端下发并原样展示。
 * 另：流式（SSE）问答不落库（实际调用发生在订阅线程、线程级归属失效），故调用量不含流式路径。</p>
 */
export function useAIUsageByCourse(options: { days?: number } = {}) {
  const days = ref<number>(options.days ?? 30)
  const loading = ref(false)
  /** 加载失败文案：非空即表示本页数据不可信（页面须显示持久告警，不得只显示空态） */
  const loadError = ref('')
  const stats = ref<AiUsageStats>({})

  /** 明细行（含「未绑定课程」行） */
  const rows = computed<AiUsageCourseRow[]>(() => stats.value.courses ?? [])
  /** 是否有任何调用记录：用于区分"加载失败"与"确实没有数据" */
  const hasData = computed(() => (stats.value.totalCalls ?? 0) > 0)
  /** 采集口径说明（后端未下发时给同一句话兜底，避免页面出现空白说明） */
  const collectionNote = computed(
    () =>
      stats.value.collectionNote ||
      '仅统计自 2026-10-13 埋点上线后的调用；此前的历史调用未留痕，不可回填。',
  )
  /** 条形图数据：按调用次数取前 8 个课程（含未绑定课程行） */
  const topCourses = computed(() =>
    rows.value
      .filter((r) => (r.calls ?? 0) > 0)
      .slice(0, 8)
      .map((r) => ({ label: r.courseName || '未绑定课程', value: r.calls ?? 0 })),
  )

  async function load(nextDays?: number) {
    if (nextDays !== undefined) days.value = nextDays
    loading.value = true
    loadError.value = ''
    try {
      const res: unknown = await adminApi.getAIUsageByCourse({ days: days.value })
      stats.value = unwrapStats(res)
    } catch (e) {
      logger.error('加载跨课程 AI 统计失败', e)
      stats.value = {}
      loadError.value = 'AI 使用统计加载失败，请检查网络后重试'
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    load()
  })

  return {
    days,
    loading,
    loadError,
    stats,
    rows,
    hasData,
    collectionNote,
    topCourses,
    load,
  }
}
