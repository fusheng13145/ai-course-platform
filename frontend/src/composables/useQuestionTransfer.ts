import { ElMessage } from 'element-plus'
import { createQuestionTransfer, type QuestionTransferDeps } from '@/utils/questionTransfer'

/**
 * 旧端题库批量导入导出的薄适配器（Element Plus 提示宿主）。
 *
 * <p>业务逻辑与文案在 `@/utils/questionTransfer`（EP-free，v2 复用同一份）；
 * 这里只把宿主接口映射到 ElMessage，对外签名保持
 * `useQuestionTransfer({ filters, fetchQuestions }) -> { handleExport, handleImport }`，
 * 旧调用方与既有测试无需改动。</p>
 */
export function useQuestionTransfer(
  deps: Pick<QuestionTransferDeps, 'filters' | 'fetchQuestions'>,
) {
  const { handleExport, handleImport } = createQuestionTransfer({
    ...deps,
    notify: {
      loading: (message) => ElMessage({ message, duration: 0 }),
      close: () => ElMessage.closeAll(),
      success: (message) => ElMessage.success(message),
      error: (message) => ElMessage.error(message),
    },
  })
  // 对外只暴露原来那两个动作；busy 仅服务于 v2 按钮禁用，旧端不需要
  return { handleExport, handleImport }
}
