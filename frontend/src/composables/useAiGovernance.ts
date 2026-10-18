import { ref, onMounted } from 'vue'
import logger from '@/utils/logger'
import adminApi from '@/api/admin'

/** 系统配置键：教师 AI 出题权限开关（与后端 AiGenerationPolicy 的常量逐字一致）。 */
export const TEACHER_QUESTION_GENERATION_KEY = 'ai.question-generation.teacher-enabled'

/** 系统配置分组（system_configs.config_group）。 */
const CONFIG_GROUP = 'ai'

/** 限制名单行（与后端 AiGenerationRestrictionVO 对齐）。 */
export interface AiRestrictionRow {
  userId?: number
  username?: string | null
  fullName?: string | null
  reason?: string | null
  restrictedBy?: number | null
  createdAt?: string | null
}

/** 教师选项（管理员从用户列表按角色筛选得到）。 */
export interface TeacherOption {
  id?: number
  username?: string
  fullName?: string
}

/** 从 /admin/configs 的响应里取出配置行数组（兼容 data 信封与裸数据两种形态）。 */
function pickConfigList(res: unknown): { key?: string; value?: string }[] {
  const holder = (res ?? {}) as { data?: unknown }
  const inner = holder.data !== undefined && holder.data !== null ? holder.data : res
  return Array.isArray(inner) ? (inner as { key?: string; value?: string }[]) : []
}

/** 从响应里取出数组（兼容 data 信封与裸数组；fetchAllPages 已解包时直接命中裸数组分支）。 */
function unwrapList<T>(res: unknown): T[] {
  const holder = (res ?? {}) as { data?: unknown }
  const inner = holder.data !== undefined && holder.data !== null ? holder.data : res
  return Array.isArray(inner) ? (inner as T[]) : []
}

/** 从异常里取可用于展示的原因（后端 BusinessException.message 在 response.data.message）。 */
function errorText(e: unknown, fallback: string): string {
  const err = e as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || err?.message || fallback
}

/**
 * AI 出题治理（角色矩阵 ④，2026-10-13 修订为**两层**：平台总闸 + 逐人名单）。
 *
 * <p><b>诚实口径</b>：① 平台总闸关闭后，非 ADMIN 的调用方在出题入口一律 403（紧急"一键全禁"）；
 * ② 逐人名单只命中被点名的教师；两者并存，ADMIN 两层都不受限。
 * 总闸**缺省 = 允许**——配置行不存在时必须判为开启，否则会把"没配过"当成"被禁用"。</p>
 *
 * <p>存储：总闸在既有 {@code system_configs}（系统配置热切换）；名单在后端 {@code ai_generation_restrictions}
 * 表（V10），由 {@code /admin/ai-governance/restrictions} 读写。本前端不新增存储面。</p>
 */
export function useAiGovernance() {
  const questionGenEnabled = ref(true)
  const loading = ref(false)
  /** 读取失败文案：非空即表示页面显示的开关状态不可信 */
  const loadError = ref('')
  const saving = ref(false)
  /** 保存失败文案：非空即表示本次操作未落库（本地状态已回滚） */
  const saveError = ref('')

  /* ---- 逐人名单（V10） ---- */
  /** 教师候选（供选择器用；来自 /admin/users 按 role=TEACHER 筛出的全量） */
  const teachers = ref<TeacherOption[]>([])
  /** 当前限制名单 */
  const restrictions = ref<AiRestrictionRow[]>([])
  const restrictionLoading = ref(false)
  /** 名单读取失败文案：非空即表示名单不可信 */
  const restrictionLoadError = ref('')
  /** 名单写操作（加入/解除）失败文案 */
  const restrictionActionError = ref('')
  const restrictionBusy = ref(false)

  async function load() {
    loading.value = true
    loadError.value = ''
    try {
      const res: unknown = await adminApi.getConfigs({ group: CONFIG_GROUP })
      const list = pickConfigList(res)
      // 保存进行中：不要用读取结果覆盖用户刚做出的选择（挂载期读取与用户点击可能并发）
      if (saving.value) return
      const hit = list.find((c) => c.key === TEACHER_QUESTION_GENERATION_KEY)
      // 配置行不存在 → 缺省为允许（与后端判定同一口径）
      questionGenEnabled.value = hit ? !isDisabledValue(hit.value) : true
    } catch (e) {
      logger.error('读取 AI 出题治理开关失败', e)
      loadError.value = 'AI 治理开关读取失败，显示的开关状态不可信，请稍后重试'
    } finally {
      loading.value = false
    }
  }

  /**
   * 保存开关；失败时**回滚本地状态**并把原因显示出来（不能让界面显示"已关闭"而实际未落库）。
   */
  async function setQuestionGenEnabled(next: boolean) {
    const previous = questionGenEnabled.value
    saving.value = true
    saveError.value = ''
    questionGenEnabled.value = next
    try {
      await adminApi.updateConfig(TEACHER_QUESTION_GENERATION_KEY, {
        value: next ? 'true' : 'false',
        group: CONFIG_GROUP,
      })
    } catch (e) {
      logger.error('保存 AI 出题治理开关失败', e)
      questionGenEnabled.value = previous
      saveError.value = '保存失败：开关未生效，已回滚到原状态，请稍后重试'
    } finally {
      saving.value = false
    }
  }

  /* ---- 逐人名单：读 ---- */

  /** 载入限制名单（失败可见：给出文案并保留空列表，不伪造条目）。 */
  async function loadRestrictions() {
    restrictionLoading.value = true
    restrictionLoadError.value = ''
    try {
      const res: unknown = await adminApi.getAiRestrictions()
      restrictions.value = unwrapList<AiRestrictionRow>(res)
    } catch (e) {
      logger.error('加载 AI 出题限制名单失败', e)
      restrictions.value = []
      restrictionLoadError.value = '限制名单加载失败，请检查网络后重试'
    } finally {
      restrictionLoading.value = false
    }
  }

  /** 载入教师候选（供选择器）；失败只记日志并置空，不阻断名单本身的展示。 */
  async function loadTeachers() {
    try {
      const res: unknown = await adminApi.getAllUsers({ role: 'TEACHER' })
      teachers.value = unwrapList<TeacherOption>(res)
    } catch (e) {
      logger.error('加载教师列表失败（选择器将为空）', e)
      teachers.value = []
    }
  }

  /* ---- 逐人名单：写 ---- */

  /**
   * 把教师加入限制名单；成功返回 true 并刷新名单。
   * <p>重复加入是幂等更新（后端保留首次加入时间），故不视为失败。</p>
   */
  async function restrict(userId: number, reason: string): Promise<boolean> {
    restrictionBusy.value = true
    restrictionActionError.value = ''
    try {
      await adminApi.restrictAiGeneration(userId, { reason })
      await loadRestrictions()
      return true
    } catch (e) {
      logger.error('加入 AI 出题限制名单失败', e)
      restrictionActionError.value = errorText(e, '加入限制名单失败，请稍后重试')
      return false
    } finally {
      restrictionBusy.value = false
    }
  }

  /** 解除限制；成功返回 true 并刷新名单。 */
  async function unrestrict(userId: number): Promise<boolean> {
    restrictionBusy.value = true
    restrictionActionError.value = ''
    try {
      await adminApi.unrestrictAiGeneration(userId)
      await loadRestrictions()
      return true
    } catch (e) {
      logger.error('解除 AI 出题限制失败', e)
      restrictionActionError.value = errorText(e, '解除限制失败，请稍后重试')
      return false
    } finally {
      restrictionBusy.value = false
    }
  }

  onMounted(() => {
    load()
    loadRestrictions()
    loadTeachers()
  })

  return {
    questionGenEnabled,
    loading,
    loadError,
    saving,
    saveError,
    load,
    setQuestionGenEnabled,
    teachers,
    restrictions,
    restrictionLoading,
    restrictionLoadError,
    restrictionActionError,
    restrictionBusy,
    loadRestrictions,
    loadTeachers,
    restrict,
    unrestrict,
  }
}

/** 与后端同口径：仅显式 "false"（忽略大小写与空白）判为禁用。 */
function isDisabledValue(value?: string): boolean {
  return typeof value === 'string' && value.trim().toLowerCase() === 'false'
}
