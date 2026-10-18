import logger from '@/utils/logger'
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { firstNonEmptyArray, pickList, unwrap } from '@/utils/response'
import { useAuthStore } from '@/stores/auth'
import courseApi from '@/api/course'
import discussionApi, {
  normalizeDiscussionPost,
  normalizeDiscussionTopic,
  type DiscussionLikeResult,
} from '@/api/discussion'
import { confirmDanger } from '@/utils/dangerConfirm'

/**
 * 课程讨论领域逻辑
 * <p>课程选择、话题列表（精华筛选/搜索）、发起话题、话题详情（回复/点赞/删除/@提及）。</p>
 */
export function useDiscussion() {
  const auth = useAuthStore()
  const myUserId = computed(() => auth.userId)

  /* ---------- 课程选择器 ---------- */
  const courseLoading = ref(false)
  const courseOptions = ref<Course[]>([])
  const courseId = ref<number | string>('')
  const loading = ref(false)
  /**
   * 加载失败提示文案：非空即表示话题列表数据不可信。
   * 失败后 topics 被置空 → 页面显示「暂无话题」，学生会误判该课程无讨论（失败可见）。
   */
  const loadError = ref('')

  async function loadCourses() {
    courseLoading.value = true
    try {
      courseOptions.value = (await courseApi.getAllCourses()) as Course[]
      if (courseOptions.value.length && !courseId.value) courseId.value = courseOptions.value[0].id
    } catch (e) {
      logger.error('加载课程列表失败', e)
    } finally {
      courseLoading.value = false
    }
  }

  function onCourseChange() {
    topics.value = []
    loadError.value = ''
    if (courseId.value) loadTopics()
  }

  /* ---------- 话题列表（含精华筛选） ---------- */
  const topicLoading = ref(false)
  const topics = ref<DiscussionTopic[]>([])
  const keyword = ref('')
  const essenceFilter = ref<string | boolean>('') // '' = 全部, true = 精华

  /** 精华话题数 */
  const essenceCount = computed(() => topics.value.filter((t) => t.isEssence).length)
  /** 总回复数 */
  const totalReplies = computed(() => topics.value.reduce((s, t) => s + (t.postCount || 0), 0))

  async function loadTopics() {
    if (!courseId.value) return
    topicLoading.value = true
    loadError.value = ''
    try {
      const params: Record<string, unknown> = { keyword: keyword.value || undefined }
      if (essenceFilter.value !== '') params.essence = essenceFilter.value
      const r = await discussionApi.getTopics(courseId.value, params)
      topics.value = pickList<DiscussionTopic>(unwrap(r)).map(normalizeDiscussionTopic)
    } catch (e) {
      logger.error('加载话题列表失败', e)
      topics.value = []
      loadError.value = '话题列表加载失败，请检查网络后重试'
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
  const currentTopic = ref<DiscussionTopic | null>(null)
  const postLoading = ref(false)
  const posts = ref<DiscussionPost[]>([])
  const replyText = ref('')
  const replySubmitting = ref(false)
  const mentionIds = ref<Array<number | string>>([])

  const participantOptions = computed(() => {
    const map = new Map<number | string, { id: number | string; name: string }>()
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

  function canDelete(p: DiscussionPost): boolean {
    return p.authorId != null && Number(p.authorId) === Number(myUserId.value)
  }

  async function openDetail(t: DiscussionTopic) {
    currentTopic.value = t
    detailVisible.value = true
    mentionIds.value = []
    replyText.value = ''
    await loadPosts(t.id)
  }

  async function loadPosts(id: number | string) {
    postLoading.value = true
    try {
      const r = await discussionApi.getTopicDetail(id)
      const map = unwrap<DiscussionTopicDetail>(r) || {}
      if (!currentTopic.value?.content && map.topic)
        currentTopic.value = normalizeDiscussionTopic({
          ...(currentTopic.value || {}),
          ...map.topic,
        })
      posts.value = firstNonEmptyArray<DiscussionPost>(map.posts, map.records, map.list).map(
        normalizeDiscussionPost,
      )
    } catch (e) {
      logger.error('加载话题详情失败', e)
    } finally {
      postLoading.value = false
    }
  }

  async function submitReply() {
    if (!replyText.value.trim()) return ElMessage.warning('请输入回复内容')
    if (!currentTopic.value) return
    const topicId = currentTopic.value.id
    replySubmitting.value = true
    try {
      await discussionApi.replyTopic(topicId, {
        content: replyText.value.trim(),
        mentionUserIds: mentionIds.value.length ? mentionIds.value : undefined,
      })
      ElMessage.success(mentionIds.value.length ? '回复成功，已通知被@的同学' : '回复成功')
      replyText.value = ''
      mentionIds.value = []
      await loadPosts(topicId)
      loadTopics()
    } catch (e) {
      logger.error('回复失败', e)
      ElMessage.error('回复失败，请稍后重试')
    } finally {
      replySubmitting.value = false
    }
  }

  async function likePost(p: DiscussionPost) {
    try {
      const result = unwrap<DiscussionLikeResult>(await discussionApi.likePost(p.id))
      if (!result) return
      p.likeCount = result.likeCount
      p.likes = result.likeCount
      p.liked = result.liked
      ElMessage.success(result.liked ? '已点赞' : '已取消点赞')
    } catch (e) {
      logger.error('点赞失败', e)
    }
  }

  async function removePost(p: DiscussionPost) {
    if (!currentTopic.value) return
    const topicId = currentTopic.value.id
    try {
      // D2-2：删除回复不可逆，明确不可逆性与恢复路径
      const ok = await confirmDanger({ verb: '删除', target: '回复' })
      if (!ok) return
      await discussionApi.deletePost(p.id)
      ElMessage.success('已删除')
      await loadPosts(topicId)
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
    loadError,
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
