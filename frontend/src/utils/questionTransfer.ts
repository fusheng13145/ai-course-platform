import { ref } from 'vue'
import logger from '@/utils/logger'
import questionApi from '@/api/question'

/**
 * 题库批量导入导出域（xlsx 文件流），与提示宿主解耦。
 *
 * <p>导出走后端 `/teacher/questions/export` 生成文件流下载（按当前课程筛选）；
 * 导入选 `.xlsx` 文件后交后端 `/teacher/questions/import-excel` 解析入库，
 * 表头约定（8 列）：题型 / 题干 / 选项(JSON) / 正确答案 / 难度 / 分值 / 知识点 / 解析。</p>
 *
 * <p>旧端由 `composables/useQuestionTransfer.ts` 注入 Element Plus 宿主，
 * v2 页面注入自有 toast；提示文案与文件契约只在本模块维护一份。</p>
 */
export interface QuestionTransferNotify {
  /** 常驻提示（导出/导入进行中） */
  loading: (message: string) => void
  /** 关闭常驻提示（成功与失败路径都必须调用） */
  close: () => void
  success: (message: string) => void
  error: (message: string) => void
}

export interface QuestionTransferDeps {
  /** 当前课程筛选；空值表示不限定课程（导出全部、导入不指定归属） */
  filters: { courseId?: string | number | null }
  /** 导入成功后的列表刷新入口 */
  fetchQuestions: () => unknown
  notify: QuestionTransferNotify
}

/** 批量导入导出动作与进行中状态；busy 用于页面禁用按钮并阻止重复触发 */
export function createQuestionTransfer(deps: QuestionTransferDeps) {
  const { filters, fetchQuestions, notify } = deps
  const busy = ref(false)

  /** 批量导出：调用后端 /teacher/questions/export 生成 xlsx 文件流下载（按当前课程筛选） */
  async function handleExport() {
    if (busy.value) return
    busy.value = true
    try {
      notify.loading('正在导出题库...')
      const res = await questionApi.exportQuestionsExcel({
        courseId: filters.courseId || undefined,
      })
      notify.close()
      const blob = new Blob([res.data || res], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `题库导出_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      notify.success('导出成功')
    } catch (e) {
      notify.close()
      logger.error('导出题库失败:', e)
      notify.error('导出失败，请稍后重试')
    } finally {
      busy.value = false
    }
  }

  /** 批量导入：选择 .xlsx 文件后调用后端 /teacher/questions/import-excel 解析入库 */
  function handleImport() {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = '.xlsx'
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0]
      if (!file) return
      busy.value = true
      try {
        notify.loading('正在导入题目...')
        const formData = new FormData()
        formData.append('file', file)
        if (filters.courseId) formData.append('courseId', String(filters.courseId))
        const res = await questionApi.importQuestionsExcel(formData)
        notify.close()
        const result = res.data || {}
        notify.success(`导入完成：成功 ${result.importedCount ?? 0} / 共 ${result.total ?? 0} 题`)
        fetchQuestions()
      } catch (err) {
        notify.close()
        logger.error('导入题目失败:', err)
        notify.error('导入失败，请检查文件格式（.xlsx，8 列表头）')
      } finally {
        busy.value = false
      }
    }
    input.click()
  }

  return {
    handleExport,
    handleImport,
    busy,
  }
}
