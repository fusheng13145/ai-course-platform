import { ref } from 'vue'
import { ElMessage } from 'element-plus'

/**
 * 列表页失败反馈统一原语（D2-1）
 * =================================
 * 以 `FileUploadManage.vue`（P1 试点页）为样板，把「错误提示 + 无数据/加载失败
 * 双态空文案」模式固化为可复用组合式函数，供三端列表页统一接入，避免逐页各写一套
 * 导致反馈口径漂移。
 *
 * 提供的双态语义：
 * - `loading`：请求中（供表格 `v-loading` / 按钮 loading 使用）；
 * - `listError`：失败时置为非空文案（供 `<el-alert v-if="listError">` 渲染）；
 * - `emptyDescription()`：空态文案随有无错误切换——有错误时提示「无法确认数据…」，
 *   无错误时提示「暂无数据」，从而让用户**可辨识失败与空数据**（D2-1 核心目标）。
 *
 * 用法：
 * ```ts
 * const { loading, listError, run, emptyDescription } = useListFeedback({
 *   failMessage: 'xxx 加载失败，请稍后重试；已保留上次显示的数据。',
 *   toastMessage: 'xxx 加载失败',
 *   emptyText: '暂无 xxx',
 * })
 * async function refresh() {
 *   await run(async () => {
 *     const res = await api.list()
 *     const list = extract(res)          // 可在此校验响应格式
 *     if (!list) throw new Error('响应格式无效')  // 抛出即进入失败态
 *     rows.value = list
 *   })
 * }
 * ```
 */
export interface UseListFeedbackOptions {
  /** 失败时顶部 `<el-alert>` 标题文案（可含恢复指引）；默认「数据加载失败，请稍后重试」 */
  failMessage?: string
  /** 失败时弹出的 toast 文案（更简短）；默认与 `failMessage` 相同 */
  toastMessage?: string
  /** 列表为空（非错误）时的空态文案；默认「暂无数据」 */
  emptyText?: string
  /** 列表加载失败时的空态文案；默认「无法确认数据，请查看上方提示后重试」 */
  errorEmptyText?: string
}

const DEFAULT_FAIL_MESSAGE = '数据加载失败，请稍后重试'
const DEFAULT_EMPTY_TEXT = '暂无数据'
const DEFAULT_ERROR_EMPTY_TEXT = '无法确认数据，请查看上方提示后重试'

export function useListFeedback(options: UseListFeedbackOptions = {}) {
  const loading = ref(false)
  const listError = ref('')

  const failMessage = options.failMessage ?? DEFAULT_FAIL_MESSAGE
  const toastMessage = options.toastMessage ?? failMessage
  const emptyText = options.emptyText ?? DEFAULT_EMPTY_TEXT
  const errorEmptyText = options.errorEmptyText ?? DEFAULT_ERROR_EMPTY_TEXT

  /**
   * 执行一次列表加载：自动管理 loading、失败降级与 toast。
   * - 成功：清除错误态，返回 fetcher 的结果；
   * - 失败（含 fetcher 内部主动 throw 的响应格式校验错误）：置 `listError` 并
   *   弹出 toast，返回 null——**绝不向上抛出**，保证页面不因单次加载失败而白屏。
   */
  async function run<T>(fetcher: () => Promise<T>): Promise<T | null> {
    loading.value = true
    listError.value = ''
    try {
      return await fetcher()
    } catch {
      listError.value = failMessage
      ElMessage.error(toastMessage)
      return null
    } finally {
      loading.value = false
    }
  }

  /** 复位反馈态（如切换筛选条件前调用） */
  function reset() {
    loading.value = false
    listError.value = ''
  }

  /** 双态空文案：有错误 → 提示无法确认数据；无错误 → 提示暂无数据 */
  function emptyDescription(): string {
    return listError.value ? errorEmptyText : emptyText
  }

  return { loading, listError, emptyText, run, reset, emptyDescription }
}
