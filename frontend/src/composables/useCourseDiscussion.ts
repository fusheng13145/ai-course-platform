import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import discussionApi from '@/api/discussion'

export function useCourseDiscussion() {
  const route = useRoute()
  const courseId = route.params.id

  const loading = ref(false)
  const saving = ref(false)
  const replyLoading = ref(false)

  const topics = ref([])
  const filter = reactive({ keyword: '', chapterId: '' })

  /* 发起话题 */
  const topicVisible = ref(false)

  /* 详情抽屉 */
  const detailVisible = ref(false)
  const detailLoading = ref(false)
  const detail = ref<Record<string, unknown>>({})
  const posts = ref([])
  const currentTopicId = ref(null)
  const replyText = ref('')

  async function loadTopics() {
    loading.value = true
    try {
      const params: Record<string, unknown> = {}
      if (filter.keyword.trim()) params.keyword = filter.keyword.trim()
      if (filter.chapterId.trim()) params.chapterId = filter.chapterId.trim()
      const r = await discussionApi.getTopics(courseId, params)
      const body = unwrap(r)
      topics.value = body?.records || body?.list || body || []
    } catch (e) {
      logger.error('加载话题列表失败', e)
    } finally {
      loading.value = false
    }
  }

  async function createTopic(payload) {
    saving.value = true
    try {
      const body = { courseId, ...payload }
      await discussionApi.createTopic(body)
      ElMessage.success('话题已发布')
      topicVisible.value = false
      await loadTopics()
    } catch (e) {
      logger.error('发布话题失败', e)
    } finally {
      saving.value = false
    }
  }

  async function openDetail(t) {
    currentTopicId.value = t.id
    detailVisible.value = true
    detailLoading.value = true
    detail.value = {}
    posts.value = []
    try {
      const r = await discussionApi.getTopicDetail(t.id)
      const body = unwrap(r)
      detail.value = body?.topic || t
      posts.value = body?.posts || []
    } catch (e) {
      logger.error('加载话题详情失败', e)
    } finally {
      detailLoading.value = false
    }
  }

  function onDetailClosed() {
    currentTopicId.value = null
    replyText.value = ''
  }

  async function sendReply() {
    if (!currentTopicId.value) return
    if (!replyText.value.trim()) {
      ElMessage.warning('请输入回复内容')
      return
    }
    replyLoading.value = true
    try {
      await discussionApi.replyTopic(currentTopicId.value, { content: replyText.value.trim() })
      ElMessage.success('回复成功')
      replyText.value = ''
      await openDetail({ id: currentTopicId.value })
      await loadTopics()
    } catch (e) {
      logger.error('回复失败', e)
    } finally {
      replyLoading.value = false
    }
  }

  async function likePost(p) {
    try {
      await discussionApi.likePost(p.id)
      ElMessage.success('已点赞')
      p.likes = (p.likes || 0) + 1
    } catch (e) {
      logger.error('点赞失败', e)
    }
  }

  async function delPost(p) {
    try {
      await discussionApi.deletePost(p.id)
      ElMessage.success('帖子已删除')
      posts.value = posts.value.filter((x) => x.id !== p.id)
      await loadTopics()
    } catch (e) {
      logger.error('删除帖子失败', e)
    }
  }

  async function togglePin() {
    if (!currentTopicId.value) return
    try {
      const next = !detail.value.pinned
      await discussionApi.pinTopic(currentTopicId.value, next)
      detail.value.pinned = next
      ElMessage.success(next ? '已置顶' : '已取消置顶')
      await loadTopics()
    } catch (e) {
      logger.error('置顶操作失败', e)
    }
  }

  async function toggleClose() {
    if (!currentTopicId.value) return
    try {
      await ElMessageBox.confirm(
        detail.value.closed ? '确定重新打开该话题？' : '确定关闭该话题？关闭后学生不可再回复。',
        '提示',
        { type: 'warning' },
      )
      await discussionApi.closeTopic(currentTopicId.value)
      detail.value.closed = !detail.value.closed
      ElMessage.success(detail.value.closed ? '话题已关闭' : '话题已重新打开')
      await loadTopics()
    } catch (e) {
      if (e !== 'cancel' && e?.toLowerCase?.() !== 'cancel') logger.error('关闭话题失败', e)
    }
  }

  onMounted(loadTopics)
  watch(
    () => route.params.id,
    () => {
      topics.value = []
      loadTopics()
    },
  )

  return {
    loading,
    saving,
    replyLoading,
    topics,
    filter,
    topicVisible,
    detailVisible,
    detailLoading,
    detail,
    posts,
    replyText,
    loadTopics,
    createTopic,
    openDetail,
    onDetailClosed,
    sendReply,
    likePost,
    delPost,
    togglePin,
    toggleClose,
  }
}
