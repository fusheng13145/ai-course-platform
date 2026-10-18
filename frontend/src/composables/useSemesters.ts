import { ref, computed, onMounted } from 'vue'
import logger from '@/utils/logger'
import adminApi from '@/api/admin'

/** 学期行（与后端 Semester 实体字段对齐）。 */
export interface SemesterRow {
  id?: number
  name?: string
  startDate?: string
  endDate?: string
  /** ACTIVE-在册；ARCHIVED-已归档 */
  status?: string
  archivedAt?: string | null
}

/** 创建学期入参（ISO 日期）。 */
export interface SemesterCreateInput {
  name: string
  startDate: string
  endDate: string
}

/** 从响应里取出学期数组（兼容 data 信封与裸数组两种形态）。 */
function unwrapList(res: unknown): SemesterRow[] {
  const holder = (res ?? {}) as { data?: unknown }
  const inner = holder.data !== undefined && holder.data !== null ? holder.data : res
  return Array.isArray(inner) ? (inner as SemesterRow[]) : []
}

/** 从异常里取可用于展示的原因（后端 BusinessException 的 message 在 response.data.message）。 */
function errorText(e: unknown, fallback: string): string {
  const err = e as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || err?.message || fallback
}

/**
 * 学期管理（角色矩阵 ① 创建学期 / ② 归档过期学期）。
 *
 * <p><b>诚实口径</b>：学期表与课程归属**尚未绑定**——`courses.semester` 仍是自由文本列，
 * 因此这里的"归档"只改变学期自身的治理状态与列表分组，<b>不会冻结或改动任何课程</b>。
 * 页面必须把这一点显式告知使用者，避免被误读为"归档后课程就锁了"。</p>
 */
export function useSemesters() {
  const semesters = ref<SemesterRow[]>([])
  const loading = ref(false)
  /** 列表加载失败文案：非空即表示列表不可信（不得只显示空态） */
  const loadError = ref('')
  /** 写操作（创建/归档）失败文案 */
  const actionError = ref('')
  const submitting = ref(false)

  const activeCount = computed(() => semesters.value.filter((s) => s.status === 'ACTIVE').length)
  const archivedCount = computed(() => semesters.value.filter((s) => s.status === 'ARCHIVED').length)

  async function list() {
    loading.value = true
    loadError.value = ''
    try {
      const res: unknown = await adminApi.getSemesters()
      semesters.value = unwrapList(res)
    } catch (e) {
      logger.error('加载学期列表失败', e)
      semesters.value = []
      loadError.value = '学期列表加载失败，请检查网络后重试'
    } finally {
      loading.value = false
    }
  }

  /** 创建学期；成功返回 true 并刷新列表，失败返回 false 并给出原因。 */
  async function create(input: SemesterCreateInput): Promise<boolean> {
    submitting.value = true
    actionError.value = ''
    try {
      await adminApi.createSemester(input)
      await list()
      return true
    } catch (e) {
      logger.error('创建学期失败', e)
      actionError.value = errorText(e, '创建学期失败，请检查输入后重试')
      return false
    } finally {
      submitting.value = false
    }
  }

  /** 归档单个学期（幂等）；成功返回 true。 */
  async function archive(id: number): Promise<boolean> {
    submitting.value = true
    actionError.value = ''
    try {
      await adminApi.archiveSemester(id)
      await list()
      return true
    } catch (e) {
      logger.error('归档学期失败', e)
      actionError.value = errorText(e, '归档失败，请稍后重试')
      return false
    } finally {
      submitting.value = false
    }
  }

  /** 批量归档过期学期；成功返回本次归档条数，失败返回 -1。 */
  async function archiveExpired(): Promise<number> {
    submitting.value = true
    actionError.value = ''
    try {
      const res = (await adminApi.archiveExpiredSemesters()) as { data?: { archivedCount?: number } }
      const count = res?.data?.archivedCount ?? 0
      await list()
      return count
    } catch (e) {
      logger.error('批量归档过期学期失败', e)
      actionError.value = errorText(e, '批量归档失败，请稍后重试')
      return -1
    } finally {
      submitting.value = false
    }
  }

  onMounted(() => {
    list()
  })

  return {
    semesters,
    loading,
    loadError,
    actionError,
    submitting,
    activeCount,
    archivedCount,
    list,
    create,
    archive,
    archiveExpired,
  }
}
