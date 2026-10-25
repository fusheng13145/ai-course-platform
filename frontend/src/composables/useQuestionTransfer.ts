import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import questionApi from '@/api/question'

/**
 * 题库批量导入导出域（xlsx 文件流）
 * <p>导出走后端 /teacher/questions/export 生成文件流下载（按当前课程筛选）；
 * 导入选 .xlsx 文件后交后端 /teacher/questions/import-excel 解析入库，
 * 表头约定：题型/题干/选项(JSON)/正确答案/难度/分值/知识点/解析。</p>
 */
export function useQuestionTransfer(deps) {
  const { filters, fetchQuestions } = deps

  /** 批量导出：调用后端 /teacher/questions/export 生成 xlsx 文件流下载（按当前课程筛选） */
  async function handleExport() {
    try {
      ElMessage({ message: '正在导出题库...', duration: 0 })
      const res = await questionApi.exportQuestionsExcel({
        courseId: filters.courseId || undefined,
      })
      ElMessage.closeAll()
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
      ElMessage.success('导出成功')
    } catch (e) {
      ElMessage.closeAll()
      logger.error('导出题库失败:', e)
      ElMessage.error('导出失败，请稍后重试')
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
      try {
        ElMessage({ message: '正在导入题目...', duration: 0 })
        const formData = new FormData()
        formData.append('file', file)
        if (filters.courseId) formData.append('courseId', filters.courseId)
        const res = await questionApi.importQuestionsExcel(formData)
        ElMessage.closeAll()
        const result = res.data || {}
        ElMessage.success(
          `导入完成：成功 ${result.importedCount ?? 0} / 共 ${result.total ?? 0} 题`,
        )
        fetchQuestions()
      } catch (err) {
        ElMessage.closeAll()
        logger.error('导入题目失败:', err)
        ElMessage.error('导入失败，请检查文件格式（.xlsx，8 列表头）')
      }
    }
    input.click()
  }

  return {
    handleExport,
    handleImport,
  }
}
