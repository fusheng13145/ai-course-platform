import logger from '@/utils/logger'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { unwrap } from '@/utils/response'
import { useAuthStore } from '@/stores/auth'
import courseApi from '@/api/course'
import discussionApi from '@/api/discussion'

/**
 * 课程讨论领域逻辑
 * <p>课程选择、话题列表（精华筛选/搜索）、发起话题、话题详情（回复/点赞/删除/@提及）。</p>
 */
export function useDiscussion() {
  const auth = useAuthStore()
  const myUserId = computed(() => auth.userId)

  /* ---------- 课程选择器 ---------- */
  const courseLoading = ref(false)
  const courseOptions = ref([])
  const courseId = ref('')
  const loading = ref(false)

  async function loadCourses() {
    courseLoading.value = true
    try {
      const r = await courseApi.getCourseList()
      const body = unwrap(r)
      const list = body?.records || body?.list || body || []
      courseOptions.value = Array.isArray(list) ? list : []
      if (courseOptions.value.length && !courseId.value) courseId.value = courseOptions.value[0].id
    } catch (e) {
      logger.error('加载课程列表失败', e)
    } finally {
      courseLoading.value = false
    }
  }

  function onCourseChange() {
    topics.value = []
    if (courseId.value) loadTopics()
  }

  /* ---------- 话题列表（含精华筛选） ---------- */
  const topicLoading = ref(false)
  const topics = ref([])
  const keyword = ref('')
  const essenceFilter = ref('') // '' = 全部, true = 精华

  /** 精华话题数 */
  const essenceCount = computed(() => topics.value.filter((t) => t.isEssence).length)
  /** 总回复数 */
  const totalReplies = computed(() => topics.value.reduce((s, t) => s + (t.postCount || 0), 0))

  async function loadTopics() {
    if (!courseId.value) return
    topicLoading.value = true
    try {
      const params = { keyword: keyword.value || undefined }
      if (essenceFilter.value !== '') params.essence = essenceFilter.value
      const r = await discussionApi.getTopics(courseId.value, params)
      const body = unwrap(r)
      const list = body?.records || body?.list || body || []
      topics.value = Array.isArray(list) ? list : []
    } catch (e) {
      logger.error('加载话题列表失败', e)
    } finally {
      topicLoading.value = false
    }
  }

  /* ---------- 发起话题 ---------- */
  const createDialog = ref(false)
  const createSubmitting = ref(false)
  const topicForm = ref({ title: '', content: '' })

  function openCreateDialog() {
    if (!courseId.value) return ElMessage.warning('请先选择课程')
    topicForm.value = { title: '', content: '' }
    createDialog.value = true
  }

  async function submitTopic() {
    if (!topicForm.value.title.trim()) return ElMessage.warning('请输入标题')
    if (!topicForm.value.content.trim()) return ElMessage.warning('请输入内容')
    createSubmitting.value = true
    try {
      await discussionApi.createTopic({
        courseId: courseId.value,
        title: topicForm.value.title.trim(),
        content: topicForm.value.content.trim(),
      })
      ElMessage.success('话题已发布')
      createDialog.value = false
      loadTopics()
    } catch (e) {
      logger.error('发布话题失败', e)
      ElMessage.error('发布失败，请稍后重试')
    } finally {
      createSubmitting.value = false
    }
  }

  /* ---------- 话题详情 + 回复 ---------- */
  const detailVisible = ref(false)
  const currentTopic = ref(null)
  const postLoading = ref(false)
  const posts = ref([])
  const replyText = ref('')
  const replySubmitting = ref(false)
  const mentionIds = ref([])

  const participantOptions = computed(() => {
    const map = new Map()
    if (currentTopic.value?.authorId) {
      map.set(currentTopic.value.authorId, {
        id: currentTopic.value.authorId,
        name: currentTopic.value.authorName || '楼主',
      })
    }
    posts.value.forEach((p) => {
      if (p.authorId) map.set(p.authorId, { id: p.authorId, name: p.authorName || '同学' })
    })
    return Array.from(map.values())
  })

  function canDelete(p) {
    return p.authorId && Number(p.authorId) === Number(myUserId.value)
  }

  async function openDetail(t) {
    currentTopic.value = t
    detailVisible.value = true
    mentionIds.value = []
    replyText.value = ''
    await loadPosts(t.id)
  }

  async function loadPosts(id) {
    postLoading.value = true
    try {
      const r = await discussionApi.getTopicDetail(id)
      const body = unwrap(r)
      const map = unwrap(body) || {}
      if (!currentTopic.value?.content && map.topic)
        currentTopic.value = { ...currentTopic.value, ...map.topic }
      const list = map.posts || map?.records || map?.list || []
      posts.value = Array.isArray(list) ? list : []
    } catch (e) {
      logger.error('加载话题详情失败', e)
    } finally {
      postLoading.value = false
    }
  }

  async function submitReply() {
    if (!replyText.value.trim()) return ElMessage.warning('请输入回复内容')
    if (!currentTopic.value) return
    replySubmitting.value = true
    try {
      await discussionApi.replyTopic(currentTopic.value.id, {
        content: replyText.value.trim(),
        mentionUserIds: mentionIds.value.length ? mentionIds.value : undefined,
      })
      ElMessage.success(mentionIds.value.length ? '回复成功，已通知被@的同学' : '回复成功')
      replyText.value = ''
      mentionIds.value = []
      await loadPosts(currentTopic.value.id)
      loadTopics()
    } catch (e) {
      logger.error('回复失败', e)
      ElMessage.error('回复失败，请稍后重试')
    } finally {
      replySubmitting.value = false
    }
  }

  async function likePost(p) {
    try {
      await discussionApi.likePost(p.id)
      ElMessage.success('已点赞')
      p.likeCount = (p.likeCount || 0) + 1
    } catch (e) {
      logger.error('点赞失败', e)
    }
  }

  async function removePost(p) {
    try {
      await ElMessageBox.confirm('确认删除该回复？', '提示', { type: 'warning' })
      await discussionApi.deletePost(p.id)
      ElMessage.success('已删除')
      await loadPosts(currentTopic.value.id)
      loadTopics()
    } catch (e) {
      if (e !== 'cancel' && e?.type !== 'cancel') logger.error('删除回复失败', e)
    }
  }

  onMounted(() => {
    loadCourses()
  })

  return {
    myUserId,
    courseLoading,
    courseOptions,
    courseId,
    loading,
    topicLoading,
    topics,
    keyword,
    essenceFilter,
    essenceCount,
    totalReplies,
    createDialog,
    createSubmitting,
    topicForm,
    detailVisible,
    currentTopic,
    postLoading,
    posts,
    replyText,
    replySubmitting,
    mentionIds,
    participantOptions,
    onCourseChange,
    loadTopics,
    openCreateDialog,
    submitTopic,
    openDetail,
    canDelete,
    submitReply,
    likePost,
    removePost,
  }
}
