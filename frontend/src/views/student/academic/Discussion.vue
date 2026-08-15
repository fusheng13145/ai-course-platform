<template>
  <div class="discussion-page fade-in">
    <!-- 顶部：标题 + 课程选择 + 发起 -->
    <PageHeader
      title="课程讨论"
      subtitle="参与话题讨论、向老师和同学提问，精华内容由老师标记"
      icon="message"
      icon-bg="accent"
    >
      <el-select
        v-model="courseId"
        placeholder="选择课程"
        filterable
        clearable
        style="width: 240px"
        :loading="courseLoading"
        @change="onCourseChange"
      >
        <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>发起讨论
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="话题总数"
        :value="topics.length"
        icon="message"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="精华话题"
        :value="essenceCount"
        icon="star"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="总回复数"
        :value="totalReplies"
        icon="comment"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background: var(--accent-soft); color: var(--accent)">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
        </span>
        <div>
          <div class="l1-title">话题列表</div>
          <div class="l1-sub">按精华筛选或搜索，点击进入话题详情参与讨论</div>
        </div>
      </div>
      <div class="l1-body">
        <div v-loading="loading">
          <el-alert v-if="!courseId" title="请选择课程" type="info" :closable="false" show-icon>
            <template #default>选择一门课程后，即可查看该课程的讨论话题。</template>
          </el-alert>

          <template v-else>
            <!-- 工具栏：精华筛选 + 搜索 + 发起 -->
            <div class="toolbar">
              <el-radio-group v-model="essenceFilter" @change="loadTopics">
                <el-radio-button :value="''">全部</el-radio-button>
                <el-radio-button :value="true">精华</el-radio-button>
              </el-radio-group>
              <el-input
                v-model="keyword"
                placeholder="搜索话题"
                clearable
                style="width: 220px"
                :prefix-icon="Search"
                @keyup.enter="loadTopics"
                @clear="loadTopics"
              />
              <el-button :disabled="topicLoading" @click="loadTopics">
                <el-icon><Refresh /></el-icon>刷新
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
                      <el-tag v-if="t.isEssence" size="small" class="ti-essence" effect="light">
                        <el-icon><Star /></el-icon>精华
                      </el-tag>
                      <el-tag v-if="t.pinned" size="small" type="danger" effect="light"
                        >置顶</el-tag
                      >
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
                        ><el-icon><User /></el-icon>{{ t.authorName || '同学' }}</span
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
              <el-empty v-else description="暂无话题，点击「发起讨论」开始吧" :image-size="70">
                <template #image><div class="empty-deco">💬</div></template>
              </el-empty>
            </div>
          </template>
        </div>
      </div>
    </section>

    <!-- 发起话题 -->
    <el-dialog v-model="createDialog" title="发起讨论" width="560px" append-to-body>
      <el-form :model="topicForm" label-width="80px">
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
            placeholder="想讨论什么？"
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
    <el-drawer
      v-model="detailVisible"
      :title="currentTopic?.title || '话题详情'"
      size="560px"
      append-to-body
    >
      <div v-if="currentTopic" class="detail-head">
        <div class="dh-tags">
          <el-tag v-if="currentTopic.isEssence" size="small" class="ti-essence" effect="light">
            <el-icon><Star /></el-icon>精华
          </el-tag>
          <el-tag v-if="currentTopic.pinned" size="small" type="danger" effect="light">置顶</el-tag>
          <el-tag
            size="small"
            :type="currentTopic.status === 'CLOSED' ? 'info' : 'success'"
            effect="light"
          >
            {{ currentTopic.status === 'CLOSED' ? '已关闭' : '进行中' }}
          </el-tag>
        </div>
        <div class="dh-content">{{ currentTopic.content }}</div>
        <div class="dh-meta">
          <span
            ><el-icon><User /></el-icon>{{ currentTopic.authorName || '同学' }}</span
          >
          <span class="ti-time">{{ currentTopic.createdAt }}</span>
        </div>
      </div>

      <el-divider>回复（{{ posts.length }}）</el-divider>

      <div v-loading="postLoading" class="post-list">
        <div v-for="p in posts" :key="p.id" class="post-item">
          <div class="pi-head">
            <span v-if="p.floor" class="pi-floor">#{{ p.floor }}</span>
            <span class="pi-author">{{ p.authorName || '匿名' }}</span>
            <span class="pi-time">{{ p.createdAt }}</span>
            <el-tag v-if="p.mentions" size="small" type="warning" effect="light" class="pi-mention">
              <el-icon><Bell /></el-icon>已@通知
            </el-tag>
          </div>
          <p class="pi-content">{{ p.content }}</p>
          <div class="pi-foot">
            <el-button text size="small" @click="likePost(p)">
              <el-icon><Pointer /></el-icon>赞 {{ p.likeCount || 0 }}
            </el-button>
            <el-button v-if="canDelete(p)" text size="small" type="danger" @click="removePost(p)"
              >删除</el-button
            >
          </div>
        </div>
        <el-empty
          v-if="!postLoading && posts.length === 0"
          description="暂无回复，来抢沙发"
          :image-size="56"
        >
          <template #image><div class="empty-deco">💬</div></template>
        </el-empty>
      </div>

      <!-- 回复框（话题关闭后禁用） -->
      <div v-if="currentTopic && currentTopic.status !== 'CLOSED'" class="reply-box">
        <div v-if="participantOptions.length" class="reply-mention">
          <span class="rm-label">@提及：</span>
          <el-select
            v-model="mentionIds"
            multiple
            collapse-tags
            placeholder="选择要通知的同学/老师"
            style="flex: 1"
            size="small"
          >
            <el-option
              v-for="u in participantOptions"
              :key="u.id"
              :label="u.name"
              :value="u.id"
              :disabled="u.id === myUserId"
            />
          </el-select>
        </div>
        <div class="reply-input">
          <el-input
            v-model="replyText"
            type="textarea"
            :rows="2"
            placeholder="输入回复内容…"
            maxlength="1000"
          />
          <el-button type="primary" :loading="replySubmitting" @click="submitReply">回复</el-button>
        </div>
      </div>
      <el-alert
        v-else-if="currentTopic"
        title="该话题已关闭，无法继续回复"
        type="info"
        :closable="false"
        show-icon
      />
    </el-drawer>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { unwrap } from '@/utils/response'
import { useAuthStore } from '@/stores/auth'
import courseApi from '@/api/course'
import discussionApi from '@/api/discussion'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import {
  Plus,
  Refresh,
  Search,
  Comment,
  User,
  ChatLineRound,
  Collection,
  ArrowRight,
  Pointer,
  Star,
  Bell,
} from '@element-plus/icons-vue'

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
  align-items: center;
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
  background: var(--ac-bg);
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
.ti-essence {
  color: var(--ac-warning);
  background: var(--ac-warning-bg);
  border-color: transparent;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

/* 详情 */
.detail-head {
  margin-bottom: 6px;
}
.dh-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}
.dh-content {
  font-size: 13.5px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  white-space: pre-wrap;
}
.dh-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 8px;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 360px;
  overflow-y: auto;
}
.post-item {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-bg);
}
.pi-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pi-floor {
  font-size: 12px;
  font-weight: 700;
  color: var(--ac-primary);
  background: var(--ac-primary-bg);
  padding: 1px 8px;
  border-radius: 999px;
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
.pi-mention {
  display: inline-flex;
  align-items: center;
  gap: 2px;
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
  margin-top: 14px;
  border-top: 1px solid var(--ac-border);
  padding-top: 14px;
}
.reply-mention {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.rm-label {
  font-size: 12px;
  color: var(--ac-text-muted);
  white-space: nowrap;
}
.reply-input {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.reply-input .el-input {
  flex: 1;
}

.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

@media (max-width: 1100px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
