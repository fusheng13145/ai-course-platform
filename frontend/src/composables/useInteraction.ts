import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import interactionApi from '@/api/interaction'

/**
 * 课堂互动页数据逻辑（拆分自 teacher/tools/Interaction.vue，2026-08-29）
 * @param {import('vue').Ref} courseIdRef 课程 ID
 */
export function useInteraction(courseIdRef) {
  const loading = ref(false)
  const activeTab = ref('sign')

  /* ---------- 签到 ---------- */
  const signLoading = ref(false)
  const signList = ref([])
  const signDialog = ref(false)
  const signSubmitting = ref(false)
  const signForm = ref({ title: '', duration: 300, type: 'NORMAL' })

  async function loadSignInList() {
    if (!courseIdRef.value) return
    signLoading.value = true
    try {
      const r = await interactionApi.getSignInList(courseIdRef.value)
      const body = unwrap(r)
      const list = body?.records || body?.list || body || []
      signList.value = Array.isArray(list) ? list : []
    } catch (e) {
      logger.error('加载签到列表失败', e)
    } finally {
      signLoading.value = false
    }
  }

  function openSignInDialog() {
    signForm.value = { title: '', duration: 300, type: 'NORMAL' }
    signDialog.value = true
  }

  async function submitSignIn() {
    if (!signForm.value.title.trim()) return ElMessage.warning('请输入签到标题')
    if (!courseIdRef.value) return ElMessage.warning('请先选择课程')
    signSubmitting.value = true
    try {
      const r = await interactionApi.createSignIn({
        courseId: courseIdRef.value,
        title: signForm.value.title.trim(),
        duration: signForm.value.duration,
        type: signForm.value.type,
      })
      const obj = unwrap(r)
      const id = obj?.id
      if (id) await interactionApi.publishSignIn(id)
      ElMessage.success('签到已发起并发布')
      signDialog.value = false
      loadSignInList()
    } catch (e) {
      logger.error('发起签到失败', e)
    } finally {
      signSubmitting.value = false
    }
  }

  async function handlePublish(row) {
    try {
      await interactionApi.publishSignIn(row.id)
      ElMessage.success('已发布')
      loadSignInList()
    } catch (e) {
      logger.error('发布签到失败', e)
    }
  }

  async function handleCloseSignIn(row) {
    try {
      await ElMessageBox.confirm(
        `确认关闭签到「${row.title}」？关闭后学生将无法继续签到。`,
        '提示',
        { type: 'warning' },
      )
      await interactionApi.closeSignIn(row.id)
      ElMessage.success('已关闭')
      loadSignInList()
    } catch (e) {
      if (e !== 'cancel' && e?.type !== 'cancel') logger.error('关闭签到失败', e)
    }
  }

  /* 签到记录 */
  const recordsDialog = ref(false)
  const recordLoading = ref(false)
  const recordList = ref([])
  const currentSign = ref(null)
  const markVisible = ref(false)
  const markSubmitting = ref(false)
  const markForm = ref({ studentId: '', status: 'SIGNED' })

  async function openRecords(row) {
    currentSign.value = row
    markVisible.value = false
    recordsDialog.value = true
    recordLoading.value = true
    try {
      const r = await interactionApi.getSignInRecords(row.id)
      const body = unwrap(r)
      const list = body?.records || body?.list || body || []
      recordList.value = Array.isArray(list) ? list : []
    } catch (e) {
      logger.error('加载签到记录失败', e)
    } finally {
      recordLoading.value = false
    }
  }

  function openMarkForm() {
    markForm.value = { studentId: '', status: 'SIGNED' }
    markVisible.value = true
  }

  async function submitMark() {
    if (!markForm.value.studentId) return ElMessage.warning('请选择学生')
    if (!currentSign.value) return
    markSubmitting.value = true
    try {
      await interactionApi.markSignIn(currentSign.value.id, {
        studentId: markForm.value.studentId,
        status: markForm.value.status,
      })
      ElMessage.success('已标记')
      markVisible.value = false
      openRecords(currentSign.value)
    } catch (e) {
      logger.error('手动标记失败', e)
    } finally {
      markSubmitting.value = false
    }
  }

  /* ---------- 投票 ---------- */
  const voteLoading = ref(false)
  const voteList = ref([])
  const voteDialog = ref(false)
  const voteSubmitting = ref(false)
  const voteForm = ref({ title: '', options: ['', ''], multiple: false })

  async function loadVoteList() {
    if (!courseIdRef.value) return
    voteLoading.value = true
    try {
      const r = await interactionApi.getVoteList(courseIdRef.value)
      const body = unwrap(r)
      const list = body?.records || body?.list || body || []
      voteList.value = Array.isArray(list) ? list : []
    } catch (e) {
      logger.error('加载投票列表失败', e)
    } finally {
      voteLoading.value = false
    }
  }

  function openVoteDialog() {
    voteForm.value = { title: '', options: ['', ''], multiple: false }
    voteDialog.value = true
  }

  async function submitVote() {
    if (!voteForm.value.title.trim()) return ElMessage.warning('请输入投票标题')
    const opts = voteForm.value.options.map((o) => o.trim()).filter(Boolean)
    if (opts.length < 2) return ElMessage.warning('至少填写 2 个选项')
    if (!courseIdRef.value) return ElMessage.warning('请先选择课程')
    voteSubmitting.value = true
    try {
      await interactionApi.createVote({
        courseId: courseIdRef.value,
        title: voteForm.value.title.trim(),
        options: opts,
        multiple: voteForm.value.multiple,
      })
      ElMessage.success('投票已发起')
      voteDialog.value = false
      loadVoteList()
    } catch (e) {
      logger.error('发起投票失败', e)
    } finally {
      voteSubmitting.value = false
    }
  }

  async function handleCloseVote(row) {
    try {
      await ElMessageBox.confirm(
        `确认结束投票「${row.title}」？结束后学生将无法继续投票。`,
        '提示',
        { type: 'warning' },
      )
      await interactionApi.closeVote(row.id)
      ElMessage.success('已结束')
      loadVoteList()
    } catch (e) {
      if (e !== 'cancel' && e?.type !== 'cancel') logger.error('结束投票失败', e)
    }
  }

  /* 投票结果 */
  const resultDialog = ref(false)
  const voteResult = ref(null)
  async function openResult(row) {
    resultDialog.value = true
    voteResult.value = null
    try {
      const r = await interactionApi.getVoteResult(row.id)
      const body = unwrap(r)
      voteResult.value = body?.result || body || null
    } catch (e) {
      logger.error('加载投票结果失败', e)
    }
  }

  watch(
    () => courseIdRef.value,
    () => {
      signList.value = []
      voteList.value = []
      if (courseIdRef.value) {
        loadSignInList()
        loadVoteList()
      }
    },
  )

  return {
    loading,
    activeTab,
    signLoading,
    signList,
    signDialog,
    signSubmitting,
    signForm,
    recordsDialog,
    recordLoading,
    recordList,
    currentSign,
    markVisible,
    markSubmitting,
    markForm,
    voteLoading,
    voteList,
    voteDialog,
    voteSubmitting,
    voteForm,
    resultDialog,
    voteResult,
    loadSignInList,
    openSignInDialog,
    submitSignIn,
    handlePublish,
    handleCloseSignIn,
    openRecords,
    openMarkForm,
    submitMark,
    loadVoteList,
    openVoteDialog,
    submitVote,
    handleCloseVote,
    openResult,
  }
}
