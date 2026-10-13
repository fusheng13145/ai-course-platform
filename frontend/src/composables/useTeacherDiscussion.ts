import logger from '@/utils/logger'
import { firstNonEmptyArray, pickList, unwrap } from '@/utils/response'
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import discussionApi, {
  normalizeDiscussionPost,
  normalizeDiscussionTopic,
  type DiscussionLikeResult,
} from '@/api/discussion'

/**
 * 教师课程讨论页数据逻辑（拆分自 teacher/tools/Discussion.vue，2026-08-29）
 * @param {import('vue').Ref} courseIdRef 课程 ID
 */
export function useTeacherDiscussion<T extends number | string | null | undefined>(
  courseIdRef: import('vue').Ref<T>,
) {
  const loading = ref(false)

  /* ---------- 话题列表 ---------- */
  const topicLoading = ref(false)
  const topics = ref<DiscussionTopic[]>([])
  const keyword = ref('')

  async function loadTopics() {
    if (!courseIdRef.value) return
    topicLoading.value = true
    try {
      const r = await discussionApi.getTopics(courseIdRef.value, {
        keyword: keyword.value || undefined,
      })
      topics.value = pickList<DiscussionTopic>(unwrap(r)).map(normalizeDiscussionTopic)
    } catch (e) {
      logger.error('加载话题列表失败', e)
    } finally {
      topicLoading.value = false
    }
  }

  /* ---------- 发布话题 ---------- */
  const createDialog = ref(false)
  const createSubmitting = ref(false)
  const topicForm = ref({ chapterId: '', title: '', content: '' })

  function openCreateDialog() {
    topicForm.value = { chapterId: '', title: '', content: '' }
    createDialog.value = true
  }

  async function submitTopic() {
    if (!topicForm.value.title.trim()) return ElMessage.warning('请输入标题')
    if (!topicForm.value.content.trim()) return ElMessage.warning('请输入内容')
    if (!courseIdRef.value) return ElMessage.warning('请先选择课程')
    createSubmitting.value = true
    try {
      await discussionApi.createTopic({
        courseId: courseIdRef.value,
        chapterId: topicForm.value.chapterId.trim() || undefined,
        title: topicForm.value.title.trim(),
        content: topicForm.value.content.trim(),
      })
      ElMessage.success('话题已发布')
      createDialog.value = false
      loadTopics()
    } catch (e) {
      logger.error('发布话题失败', e)
    } finally {
      createSubmitting.value = false
    }
  }

  /* ---------- 话题详情 ---------- */
  const detailDialog = ref(false)
  const currentTopic = ref<DiscussionTopic | null>(null)
  const postLoading = ref(false)
  const posts = ref<DiscussionPost[]>([])
  const replyText = ref('')
  const replySubmitting = ref(false)

  async function openDetail(t: DiscussionTopic) {
    currentTopic.value = t
    detailDialog.value = true
    await loadPosts(t.id)
  }

  async function loadPosts(id: number | string) {
    postLoading.value = true
    try {
      const r = await discussionApi.getTopicDetail(id)
      // 兼容 Map 结构：topic 字段 + posts 列表
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
      await discussionApi.replyTopic(topicId, { content: replyText.value.trim() })
      ElMessage.success('回复成功')
      replyText.value = ''
      await loadPosts(topicId)
      loadTopics()
    } catch (e) {
      logger.error('回复失败', e)
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
      await ElMessageBox.confirm('确认删除该回复？此操作不可恢复。', '提示', { type: 'warning' })
      await discussionApi.deletePost(p.id)
      ElMessage.success('已删除')
      await loadPosts(topicId)
      loadTopics()
    } catch (e) {
      if (e !== 'cancel' && e?.type !== 'cancel') logger.error('删除回复失败', e)
    }
  }

  async function togglePin() {
    if (!currentTopic.value) return
    try {
      await discussionApi.pinTopic(currentTopic.value.id, !currentTopic.value.pinned)
      currentTopic.value.pinned = !currentTopic.value.pinned
      ElMessage.success(currentTopic.value.pinned ? '已置顶' : '已取消置顶')
      loadTopics()
    } catch (e) {
      logger.error('置顶操作失败', e)
    }
  }

  async function closeTopic() {
    if (!currentTopic.value) return
    try {
      await ElMessageBox.confirm('确认关闭该话题？关闭后学生将无法继续回复。', '提示', {
        type: 'warning',
      })
      await discussionApi.closeTopic(currentTopic.value.id)
      ElMessage.success('话题已关闭')
      currentTopic.value.status = 'CLOSED'
      loadTopics()
    } catch (e) {
      if (e !== 'cancel' && e?.type !== 'cancel') logger.error('关闭话题失败', e)
    }
  }

  watch(
    () => courseIdRef.value,
    () => {
      topics.value = []
      if (courseIdRef.value) loadTopics()
    },
  )

  return {
    loading,
    topicLoading,
    topics,
    keyword,
    createDialog,
    createSubmitting,
    topicForm,
    detailDialog,
    currentTopic,
    postLoading,
    posts,
    replyText,
    replySubmitting,
    loadTopics,
    openCreateDialog,
    submitTopic,
    openDetail,
    submitReply,
    likePost,
    removePost,
    togglePin,
    closeTopic,
  }
}
