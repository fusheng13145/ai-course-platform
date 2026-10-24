import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import interactionApi from '@/api/interaction'
import { confirmDanger } from '@/utils/dangerConfirm'

/**
 * 课程互动页数据逻辑（拆分自 teacher/cockpit/CourseInteraction.vue，2026-08-29）
 * @param {import('vue').Ref} courseIdRef 课程 ID
 */
export function useCourseInteraction(courseIdRef) {
  const loading = ref(false)
  const saving = ref(false)
  const activeTab = ref('signin')

  const signIns = ref([])
  const votes = ref([])
  /**
   * 签到 / 投票两路列表**各自独立**记录失败，避免「签到成功、投票失败」时被一方复位而互相串味；
   * 对外以 loadError 聚合暴露，页面据此渲染持久提示（失败可见）。
   */
  const signError = ref('')
  const voteError = ref('')
  const loadError = computed(() => signError.value || voteError.value)

  const activeCount = computed(
    () =>
      signIns.value.filter(
        (r) => r.status === 'PUBLISHED' || r.status === 'OPEN' || r.status === 'ONGOING',
      ).length +
      votes.value.filter(
        (r) => r.status === 'PUBLISHED' || r.status === 'OPEN' || r.status === 'ONGOING',
      ).length,
  )
  const endedCount = computed(
    () =>
      signIns.value.filter((r) => r.status === 'CLOSED').length +
      votes.value.filter((r) => r.status === 'CLOSED').length,
  )

  /* 签到对话框 */
  const signInVisible = ref(false)
  const signInForm = reactive({ title: '', duration: 10, type: 'NORMAL' })

  /* 签到记录 */
  const recordsVisible = ref(false)
  const records = ref([])
  const currentSignInId = ref(null)

  /* 投票对话框 */
  const voteVisible = ref(false)
  const voteForm = reactive({ title: '', options: [''], multiple: false })

  /* 投票结果 */
  const resultVisible = ref(false)
  const voteResult = ref(null)

  function statusType(s) {
    if (s === 'PUBLISHED' || s === 'OPEN' || s === 'ONGOING') return 'success'
    if (s === 'CLOSED') return 'info'
    return 'warning'
  }
  function statusText(s) {
    if (s === 'PUBLISHED' || s === 'OPEN' || s === 'ONGOING') return '进行中'
    if (s === 'CLOSED') return '已结束'
    return '草稿'
  }

  async function loadSignInList() {
    signError.value = ''
    try {
      const r = await interactionApi.getSignInList(courseIdRef.value)
      const body = unwrap(r)
      signIns.value = (body?.records || body?.list || body || []) as typeof signIns.value
    } catch (e) {
      logger.error('加载签到列表失败', e)
      signIns.value = []
      signError.value = '签到列表加载失败，请检查网络后重试'
    }
  }
  async function loadVoteList() {
    voteError.value = ''
    try {
      const r = await interactionApi.getVoteList(courseIdRef.value)
      const body = unwrap(r)
      votes.value = (body?.records || body?.list || body || []) as typeof votes.value
    } catch (e) {
      logger.error('加载投票列表失败', e)
      votes.value = []
      voteError.value = '投票列表加载失败，请检查网络后重试'
    }
  }

  function openSignInDialog() {
    signInForm.title = ''
    signInForm.duration = 10
    signInForm.type = 'NORMAL'
    signInVisible.value = true
  }
  async function createSignIn() {
    if (!signInForm.title.trim()) {
      ElMessage.warning('请填写签到标题')
      return
    }
    saving.value = true
    try {
      const payload: Record<string, unknown> = {
        courseId: courseIdRef.value,
        title: signInForm.title.trim(),
        duration: signInForm.duration,
        type: signInForm.type,
      }
      await interactionApi.createSignIn(payload)
      ElMessage.success('签到已发起')
      signInVisible.value = false
      await loadSignInList()
    } catch (e) {
      logger.error('发起签到失败', e)
    } finally {
      saving.value = false
    }
  }
  async function publish(row) {
    try {
      await interactionApi.publishSignIn(row.id)
      ElMessage.success('已发布签到')
      await loadSignInList()
    } catch (e) {
      logger.error('发布签到失败', e)
    }
  }
  async function closeSignIn(row) {
    try {
      // D2-2：明确后果（学生无法继续签到）与不可重新开启
      const ok = await confirmDanger({
        verb: '结束',
        target: '签到',
        impact: '学生将无法继续签到，已签到的记录不受影响',
        irreversible: false,
        note: '结束后暂不支持重新开启',
        confirmText: '确定结束',
      })
      if (!ok) return
      await interactionApi.closeSignIn(row.id)
      ElMessage.success('签到已结束')
      await loadSignInList()
    } catch (e) {
      if (e !== 'cancel' && e?.toLowerCase?.() !== 'cancel') logger.error('结束签到失败', e)
    }
  }
  async function openRecords(row) {
    currentSignInId.value = row.id
    recordsVisible.value = true
    records.value = []
    try {
      const r = await interactionApi.getSignInRecords(row.id)
      const body = unwrap(r)
      records.value = (body?.records || body?.list || body || []) as typeof records.value
    } catch (e) {
      logger.error('加载签到记录失败', e)
    }
  }
  async function mark(row) {
    if (!currentSignInId.value) return
    try {
      await interactionApi.markSignIn(currentSignInId.value, {
        studentId: row.studentId,
        status: 'SIGNED',
      })
      ElMessage.success('已标记签到')
      await openRecords({ id: currentSignInId.value })
    } catch (e) {
      logger.error('标记签到失败', e)
    }
  }

  function openVoteDialog() {
    voteForm.title = ''
    voteForm.options = ['']
    voteForm.multiple = false
    voteVisible.value = true
  }
  async function createVote() {
    if (!voteForm.title.trim()) {
      ElMessage.warning('请填写投票标题')
      return
    }
    const opts = voteForm.options.map((o) => o.trim()).filter(Boolean)
    if (opts.length < 2) {
      ElMessage.warning('请至少填写两个有效选项')
      return
    }
    saving.value = true
    try {
      const payload: Record<string, unknown> = {
        courseId: courseIdRef.value,
        title: voteForm.title.trim(),
        options: opts,
        multiple: voteForm.multiple,
      }
      await interactionApi.createVote(payload)
      ElMessage.success('投票已发起')
      voteVisible.value = false
      await loadVoteList()
    } catch (e) {
      logger.error('发起投票失败', e)
    } finally {
      saving.value = false
    }
  }
  async function closeVote(row) {
    try {
      // D2-2：明确后果（学生无法继续投票）与不可重新开启
      const ok = await confirmDanger({
        verb: '结束',
        target: '投票',
        impact: '学生将无法继续投票，已投票的记录不受影响',
        irreversible: false,
        note: '结束后暂不支持重新开启',
        confirmText: '确定结束',
      })
      if (!ok) return
      await interactionApi.closeVote(row.id)
      ElMessage.success('投票已结束')
      await loadVoteList()
    } catch (e) {
      if (e !== 'cancel' && e?.toLowerCase?.() !== 'cancel') logger.error('结束投票失败', e)
    }
  }
  async function openVoteResult(row) {
    resultVisible.value = true
    voteResult.value = null
    try {
      const r = await interactionApi.getVoteResult(row.id)
      const body = unwrap(r)
      voteResult.value = body || { options: [], total: 0 }
    } catch (e) {
      logger.error('加载投票结果失败', e)
    }
  }

  async function load() {
    loading.value = true
    try {
      await Promise.all([loadSignInList(), loadVoteList()])
    } finally {
      loading.value = false
    }
  }

  onMounted(load)
  watch(() => courseIdRef.value, load)

  return {
    loading,
    loadError,
    signError,
    voteError,
    saving,
    activeTab,
    signIns,
    votes,
    activeCount,
    endedCount,
    signInVisible,
    signInForm,
    recordsVisible,
    records,
    voteVisible,
    voteForm,
    resultVisible,
    voteResult,
    statusType,
    statusText,
    openSignInDialog,
    createSignIn,
    publish,
    closeSignIn,
    openRecords,
    mark,
    openVoteDialog,
    createVote,
    closeVote,
    openVoteResult,
  }
}
