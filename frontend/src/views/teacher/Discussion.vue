<template>
  <div class="discussion-page fade-in">
    <!-- 顶部课程选择器 -->
    <div class="page-head">
      <div class="page-title">
        <el-icon :size="20" class="ttl-icon"><ChatDotRound /></el-icon>
        <div>
          <h3>课程讨论</h3>
          <p>发布话题、引导学生讨论并参与互动</p>
        </div>
      </div>
      <el-select
        v-model="courseId"
        placeholder="请选择课程"
        filterable
        clearable
        style="width: 240px"
        :loading="courseLoading"
        @change="onCourseChange"
      >
        <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
    </div>

    <div v-loading="loading">
      <el-alert v-if="!courseId" title="请选择课程" type="info" :closable="false" show-icon>
        <template #default>选择一门课程后，即可查看并管理课程讨论话题。</template>
      </el-alert>

      <template v-else>
        <!-- 工具栏 -->
        <div class="toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索话题"
            clearable
            style="width: 240px"
            :prefix-icon="Search"
            @keyup.enter="loadTopics"
            @clear="loadTopics"
          />
          <el-button :disabled="topicLoading" @click="loadTopics">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>发布话题
          </el-button>
        </div>

        <!-- 话题列表 -->
        <div v-loading="topicLoading" class="chart-card">
          <div class="chart-title">
            <el-icon><Comment /></el-icon>话题列表
            <el-tag size="small" type="info" effect="light" round>{{ topics.length }}</el-tag>
          </div>

          <div v-if="topics.length" class="topic-list">
            <div v-for="t in topics" :key="t.id" class="topic-item" @click="openDetail(t)">
              <div class="ti-main">
                <div class="ti-top">
                  <el-tag v-if="t.pinned" size="small" type="danger" effect="light">置顶</el-tag>
                  <span class="ti-title">{{ t.title }}</span>
                  <el-tag
                    size="small"
                    :type="t.status === 'CLOSED' ? 'info' : 'success'"
                    effect="light"
                  >
                    {{ t.status === 'CLOSED' ? '已关闭' : '进行中' }}
                  </el-tag>
                </div>
                <p class="ti-content">{{ t.content }}</p>
                <div class="ti-meta">
                  <span
                    ><el-icon><User /></el-icon>{{ t.authorName || '教师' }}</span
                  >
                  <span
                    ><el-icon><ChatLineRound /></el-icon>{{ t.postCount || 0 }} 条回复</span
                  >
                  <span v-if="t.chapterName"
                    ><el-icon><Collection /></el-icon>{{ t.chapterName }}</span
                  >
                  <span class="ti-time">{{ t.createdAt }}</span>
                </div>
              </div>
              <el-icon class="ti-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
          <el-empty v-else description="暂无话题，点击「发布话题」" :image-size="70" />
        </div>
      </template>
    </div>

    <!-- 发布话题 -->
    <el-dialog v-model="createDialog" title="发布话题" width="560px" append-to-body>
      <el-form :model="topicForm" label-width="80px">
        <el-form-item label="章节" prop="chapterId">
          <el-input
            v-model="topicForm.chapterId"
            placeholder="可选，填写章节标识"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input
            v-model="topicForm.title"
            placeholder="话题标题"
            maxlength="80"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="topicForm.content"
            type="textarea"
            :rows="4"
            placeholder="话题内容"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitTopic">发布</el-button>
      </template>
    </el-dialog>

    <!-- 话题详情 -->
    <el-dialog
      v-model="detailDialog"
      :title="currentTopic?.title || '话题详情'"
      width="680px"
      append-to-body
    >
      <div v-if="currentTopic" class="detail-head">
        <div class="dh-content">{{ currentTopic.content }}</div>
        <div class="dh-actions">
          <el-button
            text
            size="small"
            :type="currentTopic.pinned ? 'danger' : 'primary'"
            @click="togglePin"
          >
            <el-icon><Top /></el-icon>{{ currentTopic.pinned ? '取消置顶' : '置顶' }}
          </el-button>
          <el-button
            text
            size="small"
            type="warning"
            :disabled="currentTopic.status === 'CLOSED'"
            @click="closeTopic"
          >
            <el-icon><SwitchButton /></el-icon>关闭话题
          </el-button>
        </div>
      </div>

      <el-divider>回复（{{ posts.length }}）</el-divider>

      <div v-loading="postLoading" class="post-list">
        <div v-for="p in posts" :key="p.id" class="post-item">
          <div class="pi-head">
            <span class="pi-author">{{ p.authorName || '匿名' }}</span>
            <span class="pi-time">{{ p.createdAt }}</span>
          </div>
          <p class="pi-content">{{ p.content }}</p>
          <div class="pi-foot">
            <el-button text size="small" @click="likePost(p)">
              <el-icon><Pointer /></el-icon>赞 {{ p.likeCount || 0 }}
            </el-button>
            <el-button text size="small" type="danger" @click="removePost(p)">删除</el-button>
          </div>
        </div>
        <el-empty
          v-if="!postLoading && posts.length === 0"
          description="暂无回复"
          :image-size="56"
        />
      </div>

      <div class="reply-box">
        <el-input
          v-model="replyText"
          type="textarea"
          :rows="2"
          placeholder="输入回复内容…"
          maxlength="1000"
        />
        <el-button type="primary" :loading="replySubmitting" @click="submitReply">回复</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import courseApi from '@/api/course'
import discussionApi from '@/api/discussion'
import {
  ChatDotRound,
  Plus,
  Refresh,
  Search,
  Comment,
  User,
  ChatLineRound,
  Collection,
  ArrowRight,
  Top,
  SwitchButton,
  Pointer,
} from '@element-plus/icons-vue'

const courseLoading = ref(false)
const courseOptions = ref([])
const courseId = ref('')
const loading = ref(false)

/* ---------- 课程选择器 ---------- */
async function loadCourses() {
  courseLoading.value = true
  try {
    const r = await courseApi.getMyCourses()
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

/* ---------- 话题列表 ---------- */
const topicLoading = ref(false)
const topics = ref([])
const keyword = ref('')

async function loadTopics() {
  if (!courseId.value) return
  topicLoading.value = true
  try {
    const r = await discussionApi.getTopics(courseId.value, { keyword: keyword.value || undefined })
    const body = unwrap(r)
    const list = body?.records || body?.list || body || []
    topics.value = Array.isArray(list) ? list : []
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
  if (!courseId.value) return ElMessage.warning('请先选择课程')
  createSubmitting.value = true
  try {
    await discussionApi.createTopic({
      courseId: courseId.value,
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
const currentTopic = ref(null)
const postLoading = ref(false)
const posts = ref([])
const replyText = ref('')
const replySubmitting = ref(false)

async function openDetail(t) {
  currentTopic.value = t
  detailDialog.value = true
  await loadPosts(t.id)
}

async function loadPosts(id) {
  postLoading.value = true
  try {
    const r = await discussionApi.getTopicDetail(id)
    const body = unwrap(r)
    // 兼容 Map 结构：topic 字段 + posts 列表
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
    await discussionApi.replyTopic(currentTopic.value.id, { content: replyText.value.trim() })
    ElMessage.success('回复成功')
    replyText.value = ''
    await loadPosts(currentTopic.value.id)
    loadTopics()
  } catch (e) {
    logger.error('回复失败', e)
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

onMounted(() => {
  loadCourses()
})
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: var(--ac-spacing-lg);
  flex-wrap: wrap;
}
.page-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ttl-icon {
  color: var(--ac-primary);
}
.page-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 640;
  color: var(--ac-text-primary);
}
.page-title p {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--ac-text-muted);
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: var(--ac-spacing-md);
  flex-wrap: wrap;
}

.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
}
.chart-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}

.topic-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.topic-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    transform 0.2s ease;
}
.topic-item:hover {
  border-color: var(--ac-primary-light);
  transform: translateY(-2px);
}
.ti-main {
  flex: 1;
  min-width: 0;
}
.ti-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ti-title {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 14.5px;
}
.ti-content {
  margin: 6px 0 8px;
  font-size: 13px;
  color: var(--ac-text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.ti-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: var(--ac-text-muted);
  flex-wrap: wrap;
}
.ti-meta .el-icon {
  margin-right: 3px;
}
.ti-time {
  margin-left: auto;
}
.ti-arrow {
  color: var(--ac-text-muted);
}

.detail-head {
  margin-bottom: 6px;
}
.dh-content {
  font-size: 13.5px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  white-space: pre-wrap;
}
.dh-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 320px;
  overflow-y: auto;
}
.post-item {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
}
.pi-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pi-author {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 13.5px;
}
.pi-time {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-left: auto;
}
.pi-content {
  margin: 8px 0 10px;
  font-size: 13.5px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  white-space: pre-wrap;
}
.pi-foot {
  display: flex;
  gap: 12px;
}

.reply-box {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  margin-top: 14px;
}
.reply-box .el-input {
  flex: 1;
}

@media (max-width: 1100px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
