import { ref, onMounted } from 'vue'
import logger from '@/utils/logger'
import adminApi from '@/api/admin'

/** 系统配置键：教师 AI 出题权限开关（与后端 AiGenerationPolicy 的常量逐字一致）。 */
export const TEACHER_QUESTION_GENERATION_KEY = 'ai.question-generation.teacher-enabled'

/** 系统配置分组（system_configs.config_group）。 */
const CONFIG_GROUP = 'ai'

/** 从 /admin/configs 的响应里取出配置行数组（兼容 data 信封与裸数据两种形态）。 */
function pickConfigList(res: unknown): { key?: string; value?: string }[] {
  const holder = (res ?? {}) as { data?: unknown }
  const inner = holder.data !== undefined && holder.data !== null ? holder.data : res
  return Array.isArray(inner) ? (inner as { key?: string; value?: string }[]) : []
}

/**
 * AI 出题治理开关（角色矩阵「限制教师出题权限」，平台级）。
 *
 * <p><b>诚实口径</b>：这是**平台级**开关，不是按教师逐个禁用；关闭后非 ADMIN 的调用方在
 * 出题入口一律 403，管理员不受限（治理者保留能力）。<b>缺省 = 允许</b>——配置行不存在时必须
 * 判为开启，否则会把"没配过"当成"被禁用"，让既有教师的出题能力在无人操作时静默失效。</p>
 *
 * <p>存储复用既有 {@code system_configs}（系统配置热切换），不新增存储面。</p>
 */
export function useAiGovernance() {
  const questionGenEnabled = ref(true)
  const loading = ref(false)
  /** 读取失败文案：非空即表示页面显示的开关状态不可信 */
  const loadError = ref('')
  const saving = ref(false)
  /** 保存失败文案：非空即表示本次操作未落库（本地状态已回滚） */
  const saveError = ref('')

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

  onMounted(() => {
    load()
  })

  return {
    questionGenEnabled,
    loading,
    loadError,
    saving,
    saveError,
    load,
    setQuestionGenEnabled,
  }
}

/** 与后端同口径：仅显式 "false"（忽略大小写与空白）判为禁用。 */
function isDisabledValue(value?: string): boolean {
  return typeof value === 'string' && value.trim().toLowerCase() === 'false'
}
