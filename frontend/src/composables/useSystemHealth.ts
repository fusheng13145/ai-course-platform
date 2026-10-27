import { ref, computed, onMounted } from 'vue'
import logger from '@/utils/logger'
import systemApi from '@/api/system'
import { unwrap } from '@/utils/response'

/**
 * Python 微服务单项健康明细（与后端 `PythonServicesHealthIndicator` 下发形状对齐）。
 * <p>未启用的服务 status 为 `DISABLED`——这是「如实告知开关状态」，**不等于故障**，
 * 展示层须与 DOWN 区分，不能把所有非 UP 一律标红。</p>
 */
export interface PythonServiceDetail {
  enabled?: boolean
  baseUrl?: string
  status?: string
  error?: string
}

/** 系统健康响应体（与后端 `SysMonitorComponent.getHealth` 下发形状对齐）。 */
export interface SystemHealth {
  status?: string
  timestamp?: string
  database?: string
  redis?: string
  /** 逐服务明细；健康指示器自身异常时为 null（缺键），此时各项须显示 "--" 而非当作正常 */
  pythonServices?: Record<string, PythonServiceDetail> | null
  /** 健康指示器自身异常时的失败原因（后端如实下发；正常情况下缺键） */
  pythonServicesError?: string
}

/** 一行引擎状态（供面板渲染）。 */
export interface EngineHealthRow {
  key: string
  label: string
  /** 展示状态文案；数据缺失（请求失败或该项未下发）时为 "--"，不留白也不伪装正常 */
  status: string
  /** 展示基调：ok=正常，warn=异常/未知，info=未启用（非故障） */
  tone: 'ok' | 'warn' | 'info'
  /** 补充说明（baseUrl 或失败原因），无则空串 */
  detail: string
}

/** Python 引擎清单（顺序即面板展示顺序）。 */
const PY_ENGINES: { key: string; label: string }[] = [
  { key: 'knowledge-graph', label: '知识图谱引擎' },
  { key: 'rag', label: 'RAG 检索服务' },
  { key: 'fine-tune', label: 'LoRA 微调服务' },
]

/** 状态 → 基调。未知/缺失一律判为 warn（失败可见：看不见的状态不能算好）。 */
function toneOf(status?: string): EngineHealthRow['tone'] {
  if (status === 'CONNECTED' || status === 'UP') return 'ok'
  if (status === 'DISABLED') return 'info'
  return 'warn'
}

/** 状态文案：缺值以 "--" 呈现，避免空单元格被误读为正常。 */
function textOf(status?: string): string {
  return status && status !== '' ? status : '--'
}

/**
 * 管理端系统引擎健康状态。
 *
 * <p><b>失败可见</b>：GET /admin/system/health 失败时给出持久 loadError 并清空结果，
 * 对应行显示 "--"，绝不显示为「全部正常」；后端未启用某项（DISABLED）作为信息态展示，
 * 不误报为故障；健康指示器自身异常时原样透出后端 `pythonServicesError`。</p>
 */
export function useSystemHealth() {
  const loading = ref(false)
  /** 加载失败文案：非空即表示健康数据不可信（面板须显示持久告警） */
  const loadError = ref('')
  const health = ref<SystemHealth>({})

  /** 后端下发的健康指示器自身异常说明（正常时为空） */
  const pythonNote = computed(() => health.value.pythonServicesError || '')

  /** 逐项引擎状态：数据库/Redis 与三个 Python 微服务。 */
  const rows = computed<EngineHealthRow[]>(() => {
    const h = health.value
    const py = h.pythonServices ?? {}
    const headerRows: EngineHealthRow[] = [
      {
        key: 'database',
        label: '数据库',
        status: textOf(h.database),
        tone: toneOf(h.database),
        detail: '',
      },
      { key: 'redis', label: 'Redis', status: textOf(h.redis), tone: toneOf(h.redis), detail: '' },
    ]
    const engineRows = PY_ENGINES.map<EngineHealthRow>((e) => {
      const d = py[e.key]
      return {
        key: e.key,
        label: e.label,
        status: textOf(d?.status),
        tone: toneOf(d?.status),
        detail: d?.error || d?.baseUrl || '',
      }
    })
    return [...headerRows, ...engineRows]
  })

  /** 是否存在异常项（warn）：用于面板顶部给出汇总提示 */
  const hasIssue = computed(() => rows.value.some((r) => r.tone === 'warn'))

  async function load() {
    loading.value = true
    loadError.value = ''
    try {
      const res: unknown = await systemApi.getSystemHealth()
      health.value = unwrap<SystemHealth>(res) ?? {}
    } catch (e) {
      logger.error('加载系统健康状态失败', e)
      health.value = {}
      loadError.value = '系统健康状态加载失败，请检查网络后重试'
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    load()
  })

  return {
    loading,
    loadError,
    health,
    rows,
    hasIssue,
    pythonNote,
    load,
  }
}
