<template>
  <div v-loading="pageLoading" class="fade-in">
    <!-- 页面头部：课程信息 -->
    <div class="course-header-card">
      <el-page-header :title="'返回课程列表'" @back="$router.push('/student/courses')">
        <template #content>
          <div class="header-content">
            <h1 class="course-title">{{ course.name }}</h1>
            <div class="course-meta-row">
              <el-tag size="small" :type="enrollment.isActive ? 'success' : 'info'">
                {{ enrollment.isActive ? '学习中' : '未开始' }}
              </el-tag>
              <span class="meta-text">{{ course.category || '未分类' }}</span>
              <span class="meta-text">{{ course.teacherName || '未知教师' }}</span>
              <span class="meta-text">{{ course.credits || 0 }} 学分</span>
            </div>
          </div>
        </template>
      </el-page-header>

      <!-- 进度概览 -->
      <div class="progress-overview">
        <span class="progress-label">学习进度</span>
        <el-progress
          :percentage="enrollment.progress"
          :stroke-width="10"
          :color="getProgressColor(enrollment.progress)"
          style="flex: 1; max-width: 300px"
        />
        <span class="progress-value">{{ enrollment.progress }}%</span>
      </div>
    </div>

    <!-- Tabs 主内容区 -->
    <el-tabs v-model="activeTab" class="main-tabs">
      <!-- 课程简介 Tab -->
      <el-tab-pane label="课程简介" name="info">
        <div class="tab-content-card">
          <section class="info-section">
            <h3 class="section-title">课程描述</h3>
            <p class="description-text">{{ course.description || '暂无课程简介' }}</p>
          </section>

          <section class="info-section">
            <h3 class="section-title">课程详情</h3>
            <el-descriptions :column="2" border class="detail-desc">
              <el-descriptions-item label="课程分类">{{
                course.category || '未分类'
              }}</el-descriptions-item>
              <el-descriptions-item label="授课教师">{{
                course.teacherName || '未知'
              }}</el-descriptions-item>
              <el-descriptions-item label="学分">{{ course.credits || 0 }}</el-descriptions-item>
              <el-descriptions-item label="难度等级">
                <el-tag size="small" :type="getDifficultyType(course.difficulty)">{{
                  course.difficulty || '未设置'
                }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="总章节数">{{ chapters.length }} 章</el-descriptions-item>
              <el-descriptions-item label="预计时长"
                >{{ estimatedDuration }} 分钟</el-descriptions-item
              >
            </el-descriptions>
          </section>

          <div v-if="!enrollment.id" class="action-section">
            <el-button type="primary" size="large" :loading="enrolling" @click="handleEnroll">
              <el-icon><Plus /></el-icon>
              加入课程学习
            </el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- 章节学习 Tab -->
      <el-tab-pane label="章节学习" name="chapters">
        <div class="tab-content-card">
          <!-- 章节列表视图 -->
          <div v-if="!learningMode" class="chapter-list">
            <div
              v-for="(ch, index) in chapters"
              :key="ch.id"
              class="chapter-item"
              @click="enterLearning(ch)"
            >
              <div class="chapter-left">
                <div
                  class="chapter-index"
                  :class="{ completed: ch.completed, current: ch.current }"
                >
                  <el-icon v-if="ch.completed"><Select /></el-icon>
                  <span v-else>{{ index + 1 }}</span>
                </div>
                <div class="chapter-info">
                  <h4 class="chapter-title">{{ ch.title }}</h4>
                  <p class="chapter-desc">{{ ch.description || '暂无描述' }}</p>
                  <div class="chapter-meta">
                    <span v-if="ch.duration" class="meta-tag">
                      <el-icon><Timer /></el-icon> {{ ch.duration }}分钟
                    </span>
                    <el-tag v-if="ch.completed" type="success" size="small">已完成</el-tag>
                    <el-tag v-else-if="ch.current" type="warning" size="small">当前章节</el-tag>
                    <el-tag v-else type="info" size="small">未开始</el-tag>
                  </div>
                </div>
              </div>
              <div class="chapter-right">
                <el-progress
                  v-if="ch.progress !== undefined && ch.progress > 0"
                  :percentage="ch.progress"
                  :stroke-width="6"
                  :show-text="false"
                  style="width: 80px"
                />
                <el-button type="primary" text @click.stop="enterLearning(ch)">
                  {{ ch.completed ? '重新学习' : ch.current ? '继续学习' : '开始学习' }}
                  <el-icon><ArrowRight /></el-icon>
                </el-button>
              </div>
            </div>
            <el-empty v-if="chapters.length === 0" description="暂无章节内容" :image-size="100" />
          </div>

          <!-- 学习模式视图 -->
          <div v-else class="learning-mode">
            <div class="learning-header">
              <el-button text @click="exitLearning">
                <el-icon><ArrowLeft /></el-icon> 返回章节列表
              </el-button>
              <h3 class="learning-chapter-title">{{ currentChapter.title }}</h3>
              <div class="learning-actions">
                <el-button type="success" :loading="completing" @click="markChapterComplete">
                  <el-icon><Select /></el-icon> 标记完成
                </el-button>
              </div>
            </div>

            <div class="learning-body">
              <!-- 视频播放区域：播放当前章节关联的视频资源（fileType 以 video/ 开头） -->
              <div class="video-player-area">
                <video
                  v-if="currentChapterVideo"
                  :key="currentChapterVideo.id"
                  class="chapter-video"
                  :src="videoSrc(currentChapterVideo)"
                  controls
                  preload="metadata"
                ></video>
                <div v-else class="video-empty">
                  <el-icon :size="48" color="var(--ac-text-muted)"><VideoPlay /></el-icon>
                  <p>本章暂无视频资源</p>
                  <p class="video-hint">可阅读下方章节内容学习</p>
                </div>
              </div>

              <!-- 章节内容区域 -->
              <div class="chapter-content-area">
                <h4 class="content-subtitle">章节内容</h4>
                <div class="content-text">
                  {{
                    currentChapter.content ||
                    currentChapter.description ||
                    '本章节暂无详细内容，请观看视频学习。'
                  }}
                </div>
              </div>
            </div>

            <!-- 学习记录自动上报提示 -->
            <div class="learning-footer">
              <el-alert title="学习记录将自动保存" type="info" :closable="false" show-icon>
                <template #default>
                  已学习 <strong>{{ learningTimeDisplay }}</strong> · 学习进度已同步记录
                </template>
              </el-alert>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, ArrowRight, Select, Timer, ArrowLeft, VideoPlay } from '@element-plus/icons-vue'
import courseApi from '@/api/course'
import chapterApi from '@/api/chapter'
import enrollmentApi from '@/api/enrollment'
import learningRecordApi from '@/api/learningRecord'
import resourceApi from '@/api/resource'

const route = useRoute()

/* ---- 页面状态 ---- */
const pageLoading = ref(false)
const activeTab = ref('info')
const enrolling = ref(false)
const completing = ref(false)

/* ---- 课程数据 ---- */
const course = reactive({
  id: null,
  name: '加载中...',
  description: '',
  category: '',
  teacherName: '',
  credits: 0,
  difficulty: '',
})

/* ---- 选课状态 ---- */
const enrollment = reactive({
  id: null,
  progress: 0,
  isActive: false,
})

/* ---- 章节数据 ---- */
const chapters = ref([])

/* ---- 学习模式状态 ---- */
const learningMode = ref(false)
const currentChapter = reactive({ id: null, title: '', description: '', content: '', duration: 0 })
const currentChapterVideo = ref(null)
const learningStartTime = ref(null)
const learningSeconds = ref(0)
let learningTimer = null

/* ---- 视频资源 ---- */
function videoSrc(res) {
  if (!res?.url) return ''
  // 后端存储的相对路径 /uploads/xx 需经 /api 前缀转发（dev: Vite 代理；prod: Nginx /api location）
  return res.url.startsWith('/uploads') ? `/api${res.url}` : res.url
}

async function loadChapterVideo(chapterId) {
  currentChapterVideo.value = null
  if (!chapterId) return
  try {
    const res = await resourceApi.getChapterResources(chapterId)
    const list = res.data || res || []
    const arr = Array.isArray(list) ? list : []
    currentChapterVideo.value = arr.find((r) => (r.fileType || '').startsWith('video/')) || null
  } catch {
    currentChapterVideo.value = null
  }
}

/* ---- 计算属性 ---- */
const estimatedDuration = computed(() => {
  return chapters.value.reduce((sum, ch) => sum + (Number(ch.duration) || 0), 0)
})

const learningTimeDisplay = computed(() => {
  const mins = Math.floor(learningSeconds.value / 60)
  const secs = learningSeconds.value % 60
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
})

/* ---- 根据进度返回颜色 ---- */
function getProgressColor(progress) {
  if (progress >= 80) return 'var(--ac-success)'
  if (progress >= 50) return 'var(--ac-primary)'
  if (progress >= 30) return 'var(--ac-warning)'
  return 'var(--ac-danger)'
}

/* ---- 难度类型映射 ---- */
function getDifficultyType(difficulty) {
  const map = { 简单: 'success', 中等: 'warning', 困难: 'danger' }
  return map[difficulty] || 'info'
}

/* ---- 加载页面数据 ---- */
onMounted(async () => {
  pageLoading.value = true
  const courseId = route.params.id
  try {
    const [courseRes, chapterRes, enrollRes] = await Promise.all([
      courseApi.getCourseById(courseId),
      chapterApi.getChapters(courseId),
      enrollmentApi.getMyEnrollments(),
    ])

    // 课程信息
    if (courseRes.data) Object.assign(course, courseRes.data)

    // 章节信息
    const chapterList = chapterRes.data || chapterRes || []
    chapters.value = chapterList.map((ch, _idx) => ({
      ...ch,
      completed: ch.status === 'COMPLETED',
      current: ch.status === 'IN_PROGRESS',
      progress: ch.progress ? Math.round(Number(ch.progress)) : 0,
    }))

    // 选课状态
    const enrollments = enrollRes.data?.list || enrollRes.data || enrollRes || []
    const myEnrollment = enrollments.find(
      (e) => Number(e.courseId) === Number(courseId) && e.isActive,
    )
    if (myEnrollment) {
      enrollment.id = myEnrollment.id
      enrollment.progress = Math.round(Number(myEnrollment.progress || 0) * 100)
      enrollment.isActive = myEnrollment.isActive
    }
  } catch (e) {
    console.error('加载课程失败:', e)
    ElMessage.error('加载课程失败')
  } finally {
    pageLoading.value = false
  }
})

/* ---- 加入课程 ---- */
async function handleEnroll() {
  enrolling.value = true
  try {
    await enrollmentApi.enrollCourse({ courseId: Number(route.params.id) })
    ElMessage.success('加入课程成功')
    enrollment.isActive = true
    enrollment.progress = 0
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '加入课程失败')
  } finally {
    enrolling.value = false
  }
}

/* ---- 进入学习模式 ---- */
function enterLearning(chapter) {
  if (!enrollment.isActive && !enrollment.id) {
    ElMessage.warning('请先加入课程')
    activeTab.value = 'info'
    return
  }

  Object.assign(currentChapter, chapter)
  learningMode.value = true
  learningStartTime.value = Date.now()
  learningSeconds.value = 0
  loadChapterVideo(chapter.id)

  // 启动计时器
  if (learningTimer) clearInterval(learningTimer)
  learningTimer = setInterval(() => {
    learningSeconds.value++
    // 每60秒自动上报一次学习记录
    if (learningSeconds.value % 60 === 0) {
      autoSaveRecord()
    }
  }, 1000)

  // 上报开始学习记录
  reportLearningStart()
}

/* ---- 退出学习模式 ---- */
function exitLearning() {
  // 退出前保存学习记录
  autoSaveRecord()
  currentChapterVideo.value = null

  if (learningTimer) {
    clearInterval(learningTimer)
    learningTimer = null
  }
  learningMode.value = false
  learningSeconds.value = 0
}

/* ---- 清理定时器 ---- */
onUnmounted(() => {
  if (learningTimer) clearInterval(learningTimer)
  // 修复 P1-22：离开页面时保存最终学习时长记录（fire-and-forget），
  // 原实现仅清定时器，中途退出导致本次学习数据全部丢失
  if (learningSeconds.value > 0 && currentChapter.id) {
    learningRecordApi
      .createLearningRecord({
        courseId: Number(route.params.id),
        chapterId: currentChapter.id,
        actionType: 'PROGRESS',
        duration: learningSeconds.value,
      })
      .catch(() => {})
  }
})

/* ---- 上报学习开始 ---- */
async function reportLearningStart() {
  try {
    await learningRecordApi.createLearningRecord({
      courseId: Number(route.params.id),
      chapterId: currentChapter.id,
      actionType: 'START',
      duration: 0,
    })
  } catch (e) {
    console.warn('学习记录上报失败:', e)
  }
}

/* ---- 自动保存学习记录 ---- */
async function autoSaveRecord() {
  try {
    await learningRecordApi.createLearningRecord({
      courseId: Number(route.params.id),
      chapterId: currentChapter.id,
      actionType: 'PROGRESS',
      duration: learningSeconds.value,
    })
  } catch (e) {
    console.warn('自动保存学习记录失败:', e)
  }
}

/* ---- 标记章节完成 ---- */
async function markChapterComplete() {
  completing.value = true
  try {
    // 先保存最终学习记录
    await learningRecordApi.createLearningRecord({
      courseId: Number(route.params.id),
      chapterId: currentChapter.id,
      actionType: 'COMPLETE',
      duration: learningSeconds.value,
    })

    // 更新本地状态
    const idx = chapters.value.findIndex((c) => c.id === currentChapter.id)
    if (idx !== -1) {
      chapters.value[idx].completed = true
      chapters.value[idx].current = false
      chapters.value[idx].progress = 100
    }

    ElMessage.success(`「${currentChapter.title}」已完成`)

    // 自动进入下一章或返回列表
    const nextIdx = chapters.value.findIndex((c) => !c.completed)
    if (nextIdx !== -1) {
      enterLearning(chapters.value[nextIdx])
    } else {
      exitLearning()
    }
  } catch (e) {
    ElMessage.error('标记完成失败')
  } finally {
    completing.value = false
  }
}
</script>

<style scoped>
.course-header-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  margin-bottom: var(--ac-spacing-md);
  box-shadow: var(--ac-shadow-sm);
}

.header-content {
  padding-left: var(--ac-spacing-md);
}

.course-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0 0 var(--ac-spacing-xs);
}

.course-meta-row {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}

.meta-text {
  font-size: 13px;
  color: var(--ac-text-muted);
}

.progress-overview {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-md);
  margin-top: var(--ac-spacing-md);
  padding-top: var(--ac-spacing-md);
  border-top: 1px solid var(--ac-border);
}

.progress-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--ac-text-secondary);
  white-space: nowrap;
}

.progress-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-primary);
  min-width: 48px;
}

.main-tabs {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: 0 var(--ac-spacing-lg);
}

.tab-content-card {
  padding: var(--ac-spacing-md) 0;
}

.info-section {
  margin-bottom: var(--ac-spacing-xl);
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
  padding-left: 12px;
  border-left: 3px solid var(--ac-primary);
}

.description-text {
  line-height: 1.9;
  color: var(--ac-text-secondary);
  font-size: 14px;
}

.detail-desc {
  margin-top: var(--ac-spacing-sm);
}

.action-section {
  margin-top: var(--ac-spacing-xl);
  text-align: center;
}

/* ---- 章节列表样式 ---- */
.chapter-list {
  max-height: 600px;
  overflow-y: auto;
}

.chapter-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--ac-spacing-md);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  margin-bottom: var(--ac-spacing-sm);
  cursor: pointer;
  transition: all var(--ac-transition-normal);
  background: var(--ac-bg-elevated);
}

.chapter-item:hover {
  transform: translateY(-1px);
  box-shadow: var(--ac-shadow-md);
  border-color: var(--ac-primary-light);
}

.chapter-left {
  display: flex;
  align-items: flex-start;
  gap: var(--ac-spacing-md);
  flex: 1;
}

.chapter-index {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
  background: var(--ac-bg);
  color: var(--ac-text-muted);
  border: 2px solid var(--ac-border);
  transition: all var(--ac-transition-normal);
}

.chapter-index.completed {
  background: rgba(139, 168, 136, 0.15);
  color: var(--ac-success);
  border-color: var(--ac-success);
}

.chapter-index.current {
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
  border-color: var(--ac-primary);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(122, 139, 165, 0.3);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(122, 139, 165, 0);
  }
}

.chapter-info {
  flex: 1;
}

.chapter-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0 0 4px;
}

.chapter-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin: 0 0 var(--ac-spacing-xs);
}

.chapter-meta {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  flex-wrap: wrap;
}

.meta-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--ac-text-muted);
}

.chapter-right {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  flex-shrink: 0;
  margin-left: var(--ac-spacing-md);
}

/* ---- 学习模式样式 ---- */
.learning-mode {
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  overflow: hidden;
}

.learning-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--ac-spacing-md) var(--ac-spacing-lg);
  background: var(--ac-gradient-card);
  border-bottom: 1px solid var(--ac-border);
}

.learning-chapter-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}

.learning-body {
  display: grid;
  grid-template-columns: 1fr 380px;
  min-height: 400px;
}

.video-player-area {
  background: #0f1420;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid var(--ac-border);
  min-height: 300px;
}

.chapter-video {
  width: 100%;
  height: 100%;
  max-height: 480px;
  object-fit: contain;
  background: #0f1420;
}

.video-empty {
  text-align: center;
  color: var(--ac-text-muted);
  padding: 40px 20px;
}

.video-empty p {
  margin-top: var(--ac-spacing-sm);
  font-size: 14px;
}

.video-hint {
  font-size: 12px !important;
  color: var(--ac-text-muted) !important;
}

.chapter-content-area {
  padding: var(--ac-spacing-lg);
  overflow-y: auto;
}

.content-subtitle {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-secondary);
  margin: 0 0 var(--ac-spacing-md);
  padding-bottom: var(--ac-spacing-sm);
  border-bottom: 1px solid var(--ac-border);
}

.content-text {
  line-height: 1.9;
  color: var(--ac-text-secondary);
  font-size: 14px;
  white-space: pre-wrap;
}

.learning-footer {
  padding: var(--ac-spacing-md) var(--ac-spacing-lg);
  border-top: 1px solid var(--ac-border);
  background: var(--ac-bg);
}

@media (max-width: 900px) {
  .learning-body {
    grid-template-columns: 1fr;
  }
  .video-player-area {
    min-height: 220px;
    border-right: none;
    border-bottom: 1px solid var(--ac-border);
  }
}
</style>
