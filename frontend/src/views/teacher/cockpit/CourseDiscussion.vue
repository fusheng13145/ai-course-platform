<template>
  <div class="course-discussion fade-in">
    <PageHeader compact title="课程讨论" subtitle="话题浏览、发起与回复互动" icon="message">
      <el-button type="primary" :icon="Plus" @click="openTopicDialog">发起话题</el-button>
    </PageHeader>
    <div v-loading="loading">
      <!-- 筛选 -->
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="filter.keyword"
            placeholder="搜索话题"
            clearable
            class="f-keyword"
            :prefix-icon="Search"
            @keyup.enter="loadTopics"
            @clear="loadTopics"
          />
          <el-input
            v-model="filter.chapterId"
            placeholder="章节 ID（可选）"
            clearable
            class="f-chapter"
            @keyup.enter="loadTopics"
            @clear="loadTopics"
          />
          <el-button :icon="Search" @click="loadTopics">筛选</el-button>
        </div>
      </div>

      <!-- 话题列表 -->
      <div class="l1-card">
        <div class="l1-head">
          <span class="l1-ico"><el-icon><ChatLineSquare /></el-icon></span>
          <div>
            <div class="l1-title">课程讨论话题</div>
            <div class="l1-sub">{{ topics.length }} 个</div>
          </div>
        </div>
        <div class="l1-body">
          <div v-if="topics.length" class="topic-list">
          <div
            v-for="t in topics"
            :key="t.id"
            class="topic-row"
            :class="{ 'is-pinned': t.pinned }"
            @click="openDetail(t)"
          >
            <div class="topic-main">
              <div class="topic-title">
                <el-tag v-if="t.pinned" size="small" type="warning" effect="light" round
                  >置顶</el-tag
                >
                <el-tag v-if="t.closed" size="small" type="info" effect="light" round
                  >已关闭</el-tag
                >
                <span>{{ t.title }}</span>
              </div>
              <div class="topic-content">{{ t.content }}</div>
              <div class="topic-meta">
                <span
                  ><el-icon><User /></el-icon>{{ t.authorName || '匿名' }}</span
                >
                <span
                  ><el-icon><ChatDotRound /></el-icon>{{ t.replyCount || 0 }} 回复</span
                >
                <span v-if="t.chapterId"
                  ><el-icon><Files /></el-icon>章节 {{ t.chapterId }}</span
                >
                <span class="time">{{ t.createdAt || '—' }}</span>
              </div>
            </div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
        </div>
        <el-empty v-else description="暂无讨论话题" />
        </div>
      </div>
    </div>

    <!-- 发起话题 -->
    <el-dialog v-model="topicVisible" title="发起话题" width="480px">
      <el-form :model="topicForm" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="topicForm.title" placeholder="话题标题" maxlength="60" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="topicForm.content" type="textarea" :rows="4" placeholder="话题内容" />
        </el-form-item>
        <el-form-item label="章节ID">
          <el-input v-model="topicForm.chapterId" placeholder="可选，关联章节" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="topicVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createTopic">发布</el-button>
      </template>
    </el-dialog>

    <!-- 话题详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="detail.title || '话题详情'"
      size="460px"
      @closed="onDetailClosed"
    >
      <div v-loading="detailLoading">
        <div class="detail-head">
          <div class="detail-content">{{ detail.content }}</div>
          <div class="detail-meta">
            <span
              ><el-icon><User /></el-icon>{{ detail.authorName || '匿名' }}</span
            >
            <span class="time">{{ detail.createdAt || '—' }}</span>
          </div>
          <div class="detail-actions">
            <el-button
              size="small"
              :type="detail.pinned ? 'warning' : 'default'"
              :icon="Top"
              @click="togglePin"
              >{{ detail.pinned ? '取消置顶' : '置顶' }}</el-button
            >
            <el-button
              size="small"
              :type="detail.closed ? 'success' : 'danger'"
              :icon="detail.closed ? Check : CircleClose"
              @click="toggleClose"
              >{{ detail.closed ? '重新打开' : '关闭话题' }}</el-button
            >
          </div>
        </div>

        <el-divider>帖子 ({{ posts.length }})</el-divider>

        <div v-if="posts.length" class="post-list">
          <div v-for="p in posts" :key="p.id" class="post-item">
            <div class="post-head">
              <span class="post-author"
                ><el-icon><User /></el-icon>{{ p.authorName || '匿名' }}</span
              >
              <span class="time">{{ p.createdAt || '—' }}</span>
            </div>
            <div class="post-content">{{ p.content }}</div>
            <div class="post-foot">
              <el-button link type="primary" :icon="Pointer" @click="likePost(p)"
                >赞 {{ p.likes || 0 }}</el-button
              >
              <el-popconfirm title="确定删除该帖子？" @confirm="delPost(p)">
                <template #reference>
                  <el-button link type="danger" :icon="Delete">删除</el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无回复" :image-size="60" />

        <div class="reply-box">
          <el-input v-model="replyText" type="textarea" :rows="3" placeholder="输入回复内容…" />
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="replyLoading"
            class="reply-send"
            @click="sendReply"
            >发送回复</el-button
          >
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import discussionApi from '@/api/discussion'
import PageHeader from '@/components/PageHeader.vue'
import {
  Plus,
  Search,
  User,
  ChatLineSquare,
  ChatDotRound,
  Files,
  ArrowRight,
  Top,
  Pointer,
  Delete,
  Promotion,
  Check,
  CircleClose,
} from '@element-plus/icons-vue'

const route = useRoute()
const courseId = route.params.id

const loading = ref(false)
const saving = ref(false)
const replyLoading = ref(false)

const topics = ref([])
const filter = reactive({ keyword: '', chapterId: '' })

/* 发起话题 */
const topicVisible = ref(false)
const topicForm = reactive({ title: '', content: '', chapterId: '' })

/* 详情抽屉 */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref({})
const posts = ref([])
const currentTopicId = ref(null)
const replyText = ref('')

async function loadTopics() {
  loading.value = true
  try {
    const params = {}
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

function openTopicDialog() {
  topicForm.title = ''
  topicForm.content = ''
  topicForm.chapterId = ''
  topicVisible.value = true
}
async function createTopic() {
  if (!topicForm.title.trim() || !topicForm.content.trim()) {
    ElMessage.warning('请填写标题与内容')
    return
  }
  saving.value = true
  try {
    const payload = { courseId, title: topicForm.title.trim(), content: topicForm.content.trim() }
    if (topicForm.chapterId.trim()) payload.chapterId = topicForm.chapterId.trim()
    await discussionApi.createTopic(payload)
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
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: var(--ac-spacing-md);
  flex-wrap: wrap;
}
.filters {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.f-keyword {
  width: 220px;
}
.f-chapter {
  width: 180px;
}

.topic-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.topic-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
  cursor: pointer;
  transition:
    border-color var(--ac-transition-fast),
    transform var(--ac-transition-fast);
}
.topic-row:hover {
  border-color: var(--ac-primary-light);
  transform: translateX(2px);
}
.topic-row.is-pinned {
  border-color: var(--ac-warning-bg);
}
.topic-main {
  flex: 1;
  min-width: 0;
}
.topic-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 14px;
}
.topic-content {
  margin: 6px 0;
  font-size: 13px;
  color: var(--ac-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.topic-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--ac-text-muted);
  flex-wrap: wrap;
}
.topic-meta .el-icon {
  margin-right: 3px;
}
.arrow {
  color: var(--ac-text-muted);
}

.detail-head {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--ac-border);
}
.detail-content {
  color: var(--ac-text-primary);
  font-size: 14px;
  line-height: 1.7;
}
.detail-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 8px;
}
.detail-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.post-item {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
}
.post-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--ac-text-muted);
}
.post-author {
  color: var(--ac-text-secondary);
  font-weight: 600;
}
.post-content {
  margin: 8px 0;
  font-size: 13.5px;
  color: var(--ac-text-primary);
  line-height: 1.6;
}
.post-foot {
  display: flex;
  gap: 14px;
}

.reply-box {
  margin-top: 16px;
}
.reply-send {
  margin-top: 10px;
}
.time {
  color: var(--ac-text-muted);
}
</style>
