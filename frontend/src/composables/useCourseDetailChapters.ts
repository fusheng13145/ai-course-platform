import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import chapterApi from '@/api/chapter'
import { confirmDanger } from '@/utils/dangerConfirm'

/**
 * 课程详情（教师）章节域
 * <p>承载章节 CRUD 与拖拽排序（保存/重置/脏标记）；课程 id、章节数据与刷新动作
 * 由编排层注入，排序基线（originalChapters）归本域保管。</p>
 */
export function useCourseDetailChapters(deps) {
  const { course, chapters, loadChapters } = deps

  const chapterDialogVisible = ref(false)
  const editingChapter = ref(null)
  const chapterForm = reactive({ title: '', duration: 0, orderIndex: 0, content: '' })
  const savingChapter = ref(false)
  const dragChanged = ref(false)
  const originalChapters = ref([])
  const savingOrder = ref(false)

  function showAddChapterDialog() {
    editingChapter.value = null
    Object.assign(chapterForm, {
      title: '',
      duration: 0,
      orderIndex: chapters.value.length,
      content: '',
    })
    chapterDialogVisible.value = true
  }
  function editChapter(row) {
    editingChapter.value = row
    Object.assign(chapterForm, {
      title: row.title,
      duration: row.duration || 0,
      orderIndex: row.orderIndex || 0,
      content: row.content || '',
    })
    chapterDialogVisible.value = true
  }
  async function saveChapter() {
    if (!chapterForm.title.trim()) return ElMessage.warning('请输入章节名称')
    savingChapter.value = true
    try {
      const payload = { ...chapterForm, courseId: Number(course.id) }
      if (editingChapter.value) {
        await chapterApi.updateChapter(editingChapter.value.id, payload)
        ElMessage.success('更新成功')
      } else {
        await chapterApi.createChapter(payload)
        ElMessage.success('添加成功')
      }
      chapterDialogVisible.value = false
      await loadChapters()
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '保存失败')
    } finally {
      savingChapter.value = false
    }
  }
  async function deleteChapter(row) {
    try {
      // D2-2：明确连带影响（子章节与任务点一并移除）与不可逆性
      const ok = await confirmDanger({
        verb: '删除',
        target: '章节',
        subject: row.title,
        impact: '将一并移除该章节下的子章节与任务点',
      })
      if (!ok) return
      await chapterApi.deleteChapter(row.id)
      chapters.value = chapters.value.filter((c) => c.id !== row.id)
      ElMessage.success('已删除')
    } catch (e) {
      if (e !== 'cancel') ElMessage.error('删除失败')
    }
  }
  async function saveChapterOrder() {
    savingOrder.value = true
    try {
      await chapterApi.batchReorder({
        orderList: chapters.value.map((c, idx) => ({ id: c.id, orderIndex: idx })),
      })
      dragChanged.value = false
      originalChapters.value = JSON.parse(JSON.stringify(chapters.value))
      ElMessage.success('排序已保存')
    } catch {
      ElMessage.error('保存排序失败')
    } finally {
      savingOrder.value = false
    }
  }
  function resetChapterOrder() {
    chapters.value = JSON.parse(JSON.stringify(originalChapters.value))
    dragChanged.value = false
  }

  /** 加载完成后同步排序基线（编排层的 loadChapters 在 finally 里调用） */
  function syncBaseline(list) {
    originalChapters.value = JSON.parse(JSON.stringify(list))
  }

  return {
    chapterDialogVisible,
    editingChapter,
    chapterForm,
    savingChapter,
    dragChanged,
    savingOrder,
    showAddChapterDialog,
    editChapter,
    saveChapter,
    deleteChapter,
    saveChapterOrder,
    resetChapterOrder,
    syncBaseline,
  }
}
