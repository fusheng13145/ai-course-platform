<template>
  <section class="discussion">
    <div class="course-tab-bar">
      <div>
        <p class="card-kicker">COURSE / DISCUSSION</p>
        <h2>
          课程讨论 <span class="chip chip-mute">{{ topics.length }} 个话题</span>
        </h2>
      </div>
      <div class="course-tab-actions">
        <button class="btn btn-ghost" :disabled="loading" @click="loadTopics">刷新</button>
        <button class="btn btn-primary" @click="openCreate">发起话题</button>
      </div>
    </div>

    <div v-if="loadError" class="callout">
      <span>ERROR / 加载失败</span>
      <p>{{ loadError }}</p>
    </div>

    <section class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">TOPICS</p>
          <h2>话题列表</h2>
        </div>
        <div class="filter-row">
          <input
            v-model="filter.keyword"
            class="input filter-search"
            type="search"
            placeholder="搜索标题…"
            @keyup.enter="loadTopics"
          />
          <button class="btn btn-sm" @click="loadTopics">查询</button>
        </div>
      </header>

      <ul v-if="topics.length" class="topic-list">
        <li v-for="(topic, index) in topics" :key="rowKey(topic, index)" class="topic-item">
          <div class="topic-main" @click="openDetail(topic)">
            <div class="topic-title-line">
              <span v-if="topic.pinned" class="chip chip-accent">置顶</span>
              <span v-if="topic.closed" class="chip chip-mute">已关闭</span>
              <b class="topic-title">{{ topic.title || '（无标题）' }}</b>
            </div>
            <p class="topic-excerpt">{{ excerpt(topic.content) }}</p>
            <div class="topic-meta">
              <span>{{ topic.authorName || topic.author || '匿名' }}</span>
              <span>{{ fmt(topic.createdAt) || '—' }}</span>
              <span>回复 {{ topic.replyCount ?? 0 }}</span>
              <span>赞 {{ topic.likeCount ?? 0 }}</span>
            </div>
          </div>
          <button class="text-btn" @click="openDetail(topic)">进入话题</button>
        </li>
      </ul>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载话题…</b></div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><V2Icon name="message" /></span>
        <b>{{ loadError ? '话题列表未能加载' : '暂无讨论话题' }}</b>
        <p>{{ loadError ? '请查看上方提示后重试。' : '点击「发起话题」开始第一个讨论。' }}</p>
      </div>
    </section>

    <!-- 发起话题 -->
    <V2Modal v-model:open="createOpen" title="发起话题" kicker="DISCUSSION / TOPIC" width="600px">
      <div class="form-grid">
        <label class="field span-2">
          <span>标题 *</span>
          <input
            v-model="createForm.title"
            class="input"
            maxlength="100"
            placeholder="一句话说明讨论什么"
          />
        </label>
        <label class="field span-2">
          <span>正文 *</span>
          <textarea
            v-model="createForm.content"
            class="textarea"
            rows="5"
            placeholder="补充背景与你的观点"
          ></textarea>
        </label>
      </div>
      <p v-if="formError" class="form-error">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="createOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="createTopic">
          {{ saving ? '发布中…' : '发布话题' }}
        </button>
      </template>
    </V2Modal>

    <!-- 话题详情 -->
    <V2Modal
      v-model:open="detailOpen"
      :title="detail.title || '话题详情'"
      kicker="DISCUSSION / THREAD"
      width="760px"
    >
      <div v-if="isTeacher" class="thread-actions">
        <span class="chip" :class="detail.pinned ? 'chip-accent' : 'chip-mute'">
          {{ detail.pinned ? '已置顶' : '未置顶' }}
        </span>
        <button class="text-btn" @click="togglePin">
          {{ detail.pinned ? '取消置顶' : '置顶' }}
        </button>
        <button class="text-btn" @click="askToggleClose">
          {{ detail.closed ? '重新打开' : '关闭话题' }}
        </button>
      </div>
      <div v-else-if="detail.closed" class="thread-actions">
        <span class="chip chip-mute">话题已关闭</span>
      </div>
      <p v-if="detail.content" class="topic-body">{{ detail.content }}</p>

      <div v-if="detailLoading" class="empty-state compact"><b>正在加载回复…</b></div>
      <ul v-else-if="posts.length" class="post-list">
        <li v-for="(post, index) in posts" :key="rowKey(post, index)" class="post-item">
          <div class="post-head">
            <b>{{ post.authorName || post.author || '匿名' }}</b>
            <span class="post-time">{{ fmt(post.createdAt) }}</span>
          </div>
          <p class="post-content">{{ post.content }}</p>
          <div class="post-foot">
            <button class="text-btn" @click="likePost(post)">
              {{ post.liked ? '取消点赞' : '点赞' }} {{ post.likeCount ?? 0 }}
            </button>
            <button v-if="isTeacher" class="text-btn danger" @click="askDeletePost(post)">
              删除
            </button>
          </div>
        </li>
      </ul>
      <div v-else class="empty-state compact"><b>还没有回复</b></div>

      <label class="field span-2 reply-field">
        <span>回复</span>
        <textarea
          v-model="replyText"
          class="textarea"
          rows="3"
          :disabled="detail.closed"
          :placeholder="detail.closed ? '话题已关闭，不能继续回复' : '写下你的看法'"
        ></textarea>
      </label>
      <p v-if="detailError" class="form-error">{{ detailError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="detailOpen = false">关闭</button>
        <button
          class="btn btn-primary"
          :disabled="replyLoading || detail.closed"
          @click="sendReply"
        >
          {{ replyLoading ? '发送中…' : '发表回复' }}
        </button>
      </template>
    </V2Modal>

    <!-- 确认（关闭话题 / 删除回复） -->
    <V2Modal
      v-model:open="confirm.open"
      :title="confirm.title"
      kicker="CONFIRM / ACTION"
      width="460px"
    >
      <p class="modal-text">{{ confirm.text }}</p>
      <p v-if="confirm.note" class="modal-hint">{{ confirm.note }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="confirm.open = false">取消</button>
        <button class="btn btn-danger" :disabled="confirm.busy" @click="runConfirm">确定</button>
      </template>
    </V2Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import V2Modal from '../../../components/V2Modal.vue'
import V2Icon from '../../../components/V2Icon.vue'
import { useV2Ui } from '../../../composables/ui'
import type { V2Role } from '../../../nav'
import discussionApi, {
  normalizeDiscussionPost,
  normalizeDiscussionTopic,
  type DiscussionLikeResult,
} from '@/api/discussion'
import { pickList, unwrap } from '@/utils/response'
import logger from '@/utils/logger'

interface Row {
  id?: number | string
  title?: string
  content?: string
  authorName?: string
  author?: string
  createdAt?: string
  replyCount?: number
  likeCount?: number
  pinned?: boolean
  closed?: boolean
  liked?: boolean
  [key: string]: unknown
}

const props = withDefaults(defineProps<{ courseId: number; role?: V2Role }>(), {
  role: 'teacher',
})
const { toast } = useV2Ui()
/** 置顶 / 关闭 / 删除只对教师渲染；学生可读、可回、可点赞、可发新话题 */
const isTeacher = computed(() => props.role === 'teacher')

const topics = ref<Row[]>([])
const loading = ref(false)
const loadError = ref('')
const filter = reactive({ keyword: '' })

const createOpen = ref(false)
const saving = ref(false)
const formError = ref('')
const createForm = reactive({ title: '', content: '' })

const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = reactive<Row>({})
const posts = ref<Row[]>([])
const currentTopicId = ref<number | string | null>(null)
const replyText = ref('')
const replyLoading = ref(false)

const confirm = reactive({
  open: false,
  title: '确认',
  text: '',
  note: '',
  busy: false,
  action: null as null | (() => Promise<void>),
})

function rowKey(row: Row, index: number): string {
  return String(row.id ?? index)
}

function excerpt(value?: string): string {
  const text = String(value || '')
    .replace(/\s+/g, ' ')
    .trim()
  if (!text) return '（无正文）'
  return text.length > 90 ? `${text.slice(0, 90)}…` : text
}

function fmt(value?: string): string {
  if (!value) return ''
  const date = new Date(String(value))
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`
}

async function loadTopics() {
  if (!props.courseId) return
  loading.value = true
  loadError.value = ''
  try {
    const params: Record<string, string> = {}
    if (filter.keyword.trim()) params.keyword = filter.keyword.trim()
    const body = unwrap(await discussionApi.getTopics(props.courseId, params))
    topics.value = pickList<Row>(body).map(
      (row) => normalizeDiscussionTopic(row as DiscussionTopic) as unknown as Row,
    )
  } catch (error) {
    logger.error('加载话题列表失败', error)
    topics.value = []
    loadError.value = '话题列表加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  formError.value = ''
  createForm.title = ''
  createForm.content = ''
  createOpen.value = true
}

async function createTopic() {
  if (!createForm.title.trim() || !createForm.content.trim()) {
    formError.value = '请填写标题与正文'
    return
  }
  saving.value = true
  try {
    await discussionApi.createTopic({
      courseId: props.courseId,
      title: createForm.title.trim(),
      content: createForm.content.trim(),
    })
    toast('话题已发布', 'ok')
    createOpen.value = false
    await loadTopics()
  } catch (error) {
    formError.value = '发布失败，请稍后重试'
    logger.error('发布话题失败', error)
  } finally {
    saving.value = false
  }
}

async function openDetail(topic: Row) {
  currentTopicId.value = topic.id as number | string
  Object.assign(detail, topic)
  detailError.value = ''
  posts.value = []
  replyText.value = ''
  detailOpen.value = true
  detailLoading.value = true
  try {
    const body = unwrap(await discussionApi.getTopicDetail(topic.id as number | string)) as {
      topic?: Row
      posts?: unknown
    }
    if (body?.topic) Object.assign(detail, normalizeDiscussionTopic(body.topic as DiscussionTopic))
    posts.value = pickList<Row>(body?.posts).map(
      (row) => normalizeDiscussionPost(row as DiscussionPost) as unknown as Row,
    )
  } catch (error) {
    logger.error('加载话题详情失败', error)
    detailError.value = '话题详情加载失败，请稍后重试'
  } finally {
    detailLoading.value = false
  }
}

async function sendReply() {
  if (!replyText.value.trim()) {
    detailError.value = '请输入回复内容'
    return
  }
  replyLoading.value = true
  detailError.value = ''
  try {
    await discussionApi.replyTopic(currentTopicId.value as number | string, {
      content: replyText.value.trim(),
    })
    toast('回复成功', 'ok')
    replyText.value = ''
    await openDetail({ id: currentTopicId.value } as Row)
  } catch (error) {
    detailError.value = '回复失败，请稍后重试'
    logger.error('回复话题失败', error)
  } finally {
    replyLoading.value = false
  }
}

async function likePost(post: Row) {
  try {
    const result = unwrap(
      await discussionApi.likePost(post.id as number | string),
    ) as unknown as DiscussionLikeResult
    post.liked = result.liked
    post.likeCount = result.likeCount ?? post.likeCount
    toast(result.liked ? '已点赞' : '已取消点赞', 'ok')
  } catch (error) {
    toast('点赞失败', 'danger')
    logger.error('点赞失败', error)
  }
}

async function togglePin() {
  if (!currentTopicId.value) return
  try {
    const next = !detail.pinned
    await discussionApi.pinTopic(currentTopicId.value, next)
    detail.pinned = next
    toast(next ? '已置顶' : '已取消置顶', 'ok')
    await loadTopics()
  } catch (error) {
    toast('置顶操作失败', 'danger')
    logger.error('置顶操作失败', error)
  }
}

function askToggleClose() {
  confirm.title = detail.closed ? '重新打开话题' : '关闭话题'
  confirm.text = detail.closed
    ? '重新打开后学生可以继续回复。'
    : '关闭后学生将无法继续回复，已有回复仍可查看。'
  confirm.note = '话题状态可随时切换'
  confirm.action = async () => {
    try {
      await discussionApi.closeTopic(currentTopicId.value as number | string)
      detail.closed = !detail.closed
      toast(detail.closed ? '话题已关闭' : '话题已重新打开', 'ok')
      confirm.open = false
      await loadTopics()
    } catch (error) {
      toast('操作失败', 'danger')
      logger.error('关闭话题失败', error)
    }
  }
  confirm.open = true
}

function askDeletePost(post: Row) {
  confirm.title = '删除回复'
  confirm.text = '确定删除这条回复？删除后不可恢复。'
  confirm.note = ''
  confirm.action = async () => {
    try {
      await discussionApi.deletePost(post.id as number | string)
      toast('已删除', 'ok')
      confirm.open = false
      await openDetail({ id: currentTopicId.value } as Row)
    } catch (error) {
      toast('删除失败', 'danger')
      logger.error('删除帖子失败', error)
    }
  }
  confirm.open = true
}

async function runConfirm() {
  if (!confirm.action) return
  confirm.busy = true
  try {
    await confirm.action()
    confirm.action = null
  } finally {
    confirm.busy = false
  }
}

watch(() => props.courseId, loadTopics, { immediate: true })
</script>

<style scoped>
.discussion {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.filter-search {
  height: 34px;
  width: 220px;
}
.topic-list,
.post-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.topic-item {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface-2);
}
.topic-main {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}
.topic-title-line {
  display: flex;
  align-items: center;
  gap: 6px;
}
.topic-title {
  font-size: 13px;
  font-weight: 600;
}
.topic-excerpt {
  margin: 4px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--ink-2);
}
.topic-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 6px;
  font-family: var(--font-mono);
  font-size: 9.5px;
  color: var(--ink-3);
}
.thread-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.topic-body {
  margin: 0 0 14px;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--ink-2);
}
.post-item {
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface-2);
}
.post-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.post-head b {
  font-size: 12px;
}
.post-time {
  font-family: var(--font-mono);
  font-size: 9.5px;
  color: var(--ink-3);
}
.post-content {
  margin: 5px 0 6px;
  font-size: 12px;
  line-height: 1.7;
}
.post-foot {
  display: flex;
  gap: 12px;
}
.reply-field {
  margin-top: 14px;
}
</style>
