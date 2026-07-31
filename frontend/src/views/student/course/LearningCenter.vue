<template>
  <div v-loading="loading" class="fade-in">
    <!-- 头部：课程信息 + 整体进度 -->
    <div class="lc-header">
      <el-page-header :title="'返回课程'" @back="$router.push(`/student/courses/${courseId}`)">
        <template #content>
          <div class="lc-title">
            <h1>{{ center.courseName || '学习中心' }}</h1>
            <el-tag size="small" type="primary" effect="light">章节任务点学习中心</el-tag>
          </div>
        </template>
      </el-page-header>

      <div class="lc-progress">
        <div class="lc-progress-num">
          <span class="done">{{ center.completedTasks }}</span>
          <span class="sep">/</span>
          <span class="total">{{ center.totalTasks }}</span>
          <span class="unit">个任务点</span>
        </div>
        <el-progress
          :percentage="center.completionPercent || 0"
          :stroke-width="12"
          :color="center.completionPercent >= 100 ? 'var(--ac-success)' : 'var(--ac-primary)'"
          style="flex: 1; max-width: 420px"
        />
        <span class="lc-percent">{{ center.completionPercent || 0 }}%</span>
      </div>
    </div>

    <!-- 章节任务点树 -->
    <div class="lc-tree-card">
      <div class="lc-tree-head">
        <span class="lc-tree-title">课程章节</span>
        <span class="lc-tree-hint">按顺序解锁 · 完成前置任务点方可进入后续内容</span>
      </div>
      <el-empty
        v-if="!loading && center.chapters.length === 0"
        description="本课程暂无章节任务点"
        :image-size="100"
      />
      <chapter-tree v-else :nodes="center.chapters" @open-task="openTask" />
    </div>

    <!-- 任务点抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      :size="drawerSize"
      direction="rtl"
      @closed="onDrawerClosed"
    >
      <div v-loading="taskLoading" class="task-panel">
        <!-- 视频任务点 -->
        <template v-if="activeTask && isVideo">
          <div v-if="videoSrc" class="video-wrap">
            <video
              ref="videoEl"
              :src="videoSrc"
              controls
              class="video-player"
              @loadedmetadata="onVideoMeta"
              @timeupdate="onVideoTime"
              @ended="onVideoEnded"
            />
          </div>
          <el-alert
            v-if="videoState.completed"
            type="success"
            :closable="false"
            show-icon
            title="已完成观看"
            class="task-alert"
          />
          <div v-else-if="!videoSrc" class="no-resource">
            <el-icon :size="40" color="var(--ac-text-muted)"><VideoPlay /></el-icon>
            <p>该视频任务点暂未配置视频资源</p>
            <el-button type="primary" @click="markVideoFallback">标记为已观看</el-button>
          </div>
          <div v-else class="video-tip">
            <el-icon><InfoFilled /></el-icon>
            观看到 90% 将自动标记为完成（已观看 {{ Math.floor(localWatched) }} 秒 / 共
            {{ Math.floor(videoDuration) }} 秒）
          </div>
        </template>

        <!-- 文档 / 阅读任务点 -->
        <template v-else-if="activeTask && isDoc">
          <div class="doc-panel">
            <p class="doc-desc">{{ activeTask.title }}</p>
            <el-alert
              v-if="activeTask.completed"
              type="success"
              :closable="false"
              show-icon
              title="已完成"
              class="task-alert"
            />
            <el-button v-else type="primary" :loading="docLoading" @click="markDocDone">
              <el-icon><Select /></el-icon> 标记为已学完
            </el-button>
          </div>
        </template>

        <!-- 作业任务点 -->
        <template v-else-if="activeTask && isAssignment">
          <div class="assignment-panel">
            <el-icon :size="40" color="var(--ac-danger)"><Files /></el-icon>
            <h3>{{ activeTask.title }}</h3>
            <p v-if="activeTask.dueDate" class="muted">
              截止：{{ formatTime(activeTask.dueDate, '--') }}
            </p>
            <p class="muted">作业请在「待办 / 作业」区域提交，提交后本任务点自动完成。</p>
            <el-button type="primary" @click="goAssignments">前往作业区</el-button>
          </div>
        </template>

        <!-- 测验任务点 -->
        <template v-else-if="activeTask && isQuiz">
          <div v-if="quizQuestions.length" class="quiz-panel">
            <div v-for="(q, i) in quizQuestions" :key="q.id" class="quiz-q">
              <div class="quiz-q-head">
                <span class="quiz-q-no">{{ i + 1 }}.</span>
                <span class="quiz-q-type" :class="qTypeClass(q.type)">{{
                  qTypeLabel(q.type)
                }}</span>
              </div>
              <p class="quiz-q-stem">{{ q.content }}</p>

              <!-- 单选 -->
              <el-radio-group
                v-if="isSingle(q.type)"
                v-model="quizAnswers[q.id]"
                class="opt-vertical"
              >
                <el-radio
                  v-for="(opt, oi) in parseOptions(q.options)"
                  :key="oi"
                  :value="oi"
                  class="opt-item"
                >
                  <span class="opt-label">{{ letter(oi) }}.</span> {{ opt }}
                </el-radio>
              </el-radio-group>

              <!-- 多选 -->
              <el-checkbox-group
                v-else-if="isMultiple(q.type)"
                v-model="quizAnswers[q.id]"
                class="opt-vertical"
              >
                <el-checkbox
                  v-for="(opt, oi) in parseOptions(q.options)"
                  :key="oi"
                  :label="oi"
                  class="opt-item"
                >
                  <span class="opt-label">{{ letter(oi) }}.</span> {{ opt }}
                </el-checkbox>
              </el-checkbox-group>

              <!-- 判断 -->
              <div v-else-if="isJudge(q.type)" class="judge-btns">
                <el-button
                  :type="quizAnswers[q.id] === true ? 'primary' : ''"
                  @click="quizAnswers[q.id] = true"
                  >正确</el-button
                >
                <el-button
                  :type="quizAnswers[q.id] === false ? 'danger' : ''"
                  @click="quizAnswers[q.id] = false"
                  >错误</el-button
                >
              </div>

              <!-- 提交后反馈 -->
              <div
                v-if="quizResult"
                class="quiz-feedback"
                :class="feedbackOf(q.id).correct ? 'ok' : 'no'"
              >
                <el-icon><component :is="feedbackOf(q.id).correct ? Check : Close" /></el-icon>
                <span>{{
                  feedbackOf(q.id).correct
                    ? '回答正确'
                    : '正确答案：' + (feedbackOf(q.id).correctAnswer || '—')
                }}</span>
                <p v-if="feedbackOf(q.id).explanation" class="quiz-exp">
                  解析：{{ feedbackOf(q.id).explanation }}
                </p>
              </div>
            </div>

            <div class="quiz-actions">
              <el-button
                v-if="!quizResult"
                type="primary"
                :loading="quizSubmitting"
                @click="submitQuizNow"
              >
                提交测验
              </el-button>
              <el-result
                v-else
                :icon="quizResult.correct === quizResult.total ? 'success' : 'warning'"
                :title="`得分 ${quizResult.score}`"
                :sub-title="`答对 ${quizResult.correct} / ${quizResult.total} 题`"
              />
            </div>
          </div>
          <el-empty v-else description="该课程暂无可用测验题目" :image-size="80" />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Select, VideoPlay, Files, InfoFilled, Check, Close } from '@element-plus/icons-vue'
import { unwrap } from '@/utils/response'
import { formatTime } from '@/utils/datetime'
import studentApi from '@/api/student'
import taskApi from '@/api/task'
import questionApi from '@/api/question'
import ChapterTree from '../components/ChapterTree.vue'

const route = useRoute()
const router = useRouter()
const courseId = route.params.id

/* ---- 页面状态 ---- */
const loading = ref(false)
const center = reactive({
  courseId: null,
  courseName: '',
  chapters: [],
  totalTasks: 0,
  completedTasks: 0,
  completionPercent: 0,
})

/* ---- 抽屉 / 任务点 ---- */
const drawerVisible = ref(false)
const taskLoading = ref(false)
const activeTask = ref(null)
const drawerSize = computed(() => (window.innerWidth < 768 ? '100%' : '620px'))

const isVideo = computed(() => activeTask.value?.taskType === 'VIDEO')
const isDoc = computed(() => ['DOC', 'DOCUMENT', 'READING'].includes(activeTask.value?.taskType))
const isAssignment = computed(() => activeTask.value?.taskType === 'ASSIGNMENT')
const isQuiz = computed(() => activeTask.value?.taskType === 'QUIZ')

const drawerTitle = computed(() => {
  if (!activeTask.value) return '任务点'
  return `${taskTypeLabel(activeTask.value.taskType)} · ${activeTask.value.title}`
})

/* ---- 视频相关 ---- */
const videoEl = ref(null)
const videoSrc = ref('')
const videoDuration = ref(0)
const localWatched = ref(0)
const lastBeat = ref(0)
const videoState = reactive({
  positionSeconds: 0,
  watchedDuration: 0,
  totalDuration: 0,
  completed: false,
})

/* ---- 文档相关 ---- */
const docLoading = ref(false)

/* ---- 测验相关 ---- */
const quizQuestions = ref([])
const quizAnswers = reactive({})
const quizSubmitting = ref(false)
const quizResult = ref(null)

/* ---- 加载学习中心 ---- */
async function load() {
  loading.value = true
  try {
    const res = await studentApi.getLearningCenter(courseId)
    const d = unwrap(res) || {}
    center.courseId = d.courseId
    center.courseName = d.courseName
    center.chapters = d.chapters || []
    center.totalTasks = d.totalTasks || 0
    center.completedTasks = d.completedTasks || 0
    center.completionPercent = d.completionPercent || 0
  } catch (e) {
    logger.error('加载学习中心失败:', e)
    ElMessage.error('加载学习中心失败')
  } finally {
    loading.value = false
  }
}

/* ---- 打开任务点抽屉 ---- */
async function openTask(task) {
  activeTask.value = task
  drawerVisible.value = true
  quizResult.value = null
  taskLoading.value = true
  try {
    if (isVideo.value) await loadVideo(task)
    else if (isQuiz.value) await loadQuiz(task)
  } catch (e) {
    logger.warn('任务点加载失败:', e)
  } finally {
    taskLoading.value = false
  }
}

/* ---- 视频：续播状态 + 资源 ---- */
async function loadVideo(task) {
  const [stateRes, taskRes] = await Promise.all([
    studentApi.getVideoState(courseId, task.id),
    taskApi.getTaskById(task.id),
  ])
  const s = unwrap(stateRes) || {}
  Object.assign(videoState, {
    positionSeconds: s.positionSeconds || 0,
    watchedDuration: s.watchedDuration || 0,
    totalDuration: s.totalDuration || 0,
    completed: !!s.completed,
  })
  localWatched.value = s.watchedDuration || 0
  const t = unwrap(taskRes) || {}
  videoSrc.value = t.attachmentUrl || t.videoUrl || ''
  videoDuration.value = s.totalDuration || 0
}

function onVideoMeta() {
  const v = videoEl.value
  if (!v) return
  videoDuration.value = v.duration || videoState.totalDuration || 0
    if (videoState.positionSeconds > 0 && videoState.positionSeconds < v.duration) {
      try {
        v.currentTime = videoState.positionSeconds
      } catch {
        // 视频定位失败（元数据可能尚未就绪），忽略即可，仅开发环境记录
        if (import.meta.env.DEV) {
          // eslint-disable-next-line no-console
          logger.warn('视频定位失败（元数据可能尚未就绪）')
        }
      }
    }
}

function onVideoTime() {
  const v = videoEl.value
  if (!v || !v.duration) return
  localWatched.value = Math.max(localWatched.value, Math.floor(v.currentTime))
  const now = Date.now()
  if (now - lastBeat.value > 5000) {
    lastBeat.value = now
    sendHeartbeat()
  }
  // 达 90% 阈值自动完成
  if (!videoState.completed && localWatched.value / v.duration >= 0.9) {
    sendHeartbeat(true)
  }
}

function onVideoEnded() {
  if (!videoState.completed) sendHeartbeat(true)
}

async function sendHeartbeat(forceComplete = false) {
  const v = videoEl.value
  const total = v?.duration ? Math.floor(v.duration) : videoState.totalDuration
  const pos = v?.currentTime ? Math.floor(v.currentTime) : videoState.positionSeconds
  try {
    const res = await studentApi.reportVideoHeartbeat(courseId, activeTask.value.id, {
      position: pos,
      watchedDuration: Math.floor(localWatched.value),
      totalDuration: total,
    })
    const s = unwrap(res) || {}
    Object.assign(videoState, {
      positionSeconds: s.positionSeconds || 0,
      watchedDuration: s.watchedDuration || 0,
      totalDuration: s.totalDuration || 0,
      completed: !!s.completed,
    })
    if (forceComplete || videoState.completed) {
      ElMessage.success('视频观看完成')
      await load()
    }
  } catch (e) {
    logger.warn('视频心跳上报失败:', e)
  }
}

async function markVideoFallback() {
  try {
    await studentApi.markTaskComplete(courseId, activeTask.value.id, 'VIDEO')
    ElMessage.success('已标记为完成')
    videoState.completed = true
    await load()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

/* ---- 文档 / 阅读：标记完成 ---- */
async function markDocDone() {
  docLoading.value = true
  try {
    const type = activeTask.value.taskType === 'READING' ? 'READING' : 'DOC'
    await studentApi.markTaskComplete(courseId, activeTask.value.id, type)
    ElMessage.success('已完成')
    activeTask.value.completed = true
    await load()
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    docLoading.value = false
  }
}

/* ---- 作业：前往作业区 ---- */
function goAssignments() {
  drawerVisible.value = false
  router.push('/student/dashboard')
}

/* ---- 测验：抽题 + 即时判分 ---- */
const OBJ_TYPES = ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'SINGLE', 'MULTIPLE', 'JUDGE']
async function loadQuiz() {
  quizQuestions.value = []
  for (const k in quizAnswers) delete quizAnswers[k]
  quizResult.value = null
  try {
    const res = await questionApi.randomSelect({ courseId, count: 8 })
    const list = unwrap(res) || []
    quizQuestions.value = (Array.isArray(list) ? list : [])
      .filter((q) => OBJ_TYPES.includes(q.type))
      .map((q) => ({ ...q, _options: parseOptions(q.options) }))
  } catch (e) {
    logger.warn('加载测验题目失败:', e)
  }
}

function buildAnswers() {
  const ans = {}
  for (const q of quizQuestions.value) {
    const a = quizAnswers[q.id]
    if (q.type === 'MULTIPLE_CHOICE' || q.type === 'MULTIPLE') {
      const arr = Array.isArray(a)
        ? a
            .map(Number)
            .sort((x, y) => x - y)
            .map(String)
        : []
      ans[q.id] = arr.join(',')
    } else if (q.type === 'TRUE_FALSE' || q.type === 'JUDGE') {
      ans[q.id] = a === true ? 'TRUE' : a === false ? 'FALSE' : ''
    } else {
      ans[q.id] = a == null ? '' : String(a)
    }
  }
  return ans
}

async function submitQuizNow() {
  const ans = buildAnswers()
  // 校验是否全部作答
  const unanswered = quizQuestions.value.filter((q) => !String(ans[q.id]).length)
  if (unanswered.length) {
    ElMessage.warning(`还有 ${unanswered.length} 道题未作答`)
    return
  }
  quizSubmitting.value = true
  try {
    const res = await studentApi.submitQuiz(courseId, activeTask.value.id, ans)
    quizResult.value = unwrap(res) || {}
    activeTask.value.completed = true
    await load()
  } catch (e) {
    ElMessage.error('提交失败')
  } finally {
    quizSubmitting.value = false
  }
}

function feedbackOf(qid) {
  const d = (quizResult.value?.details || []).find((x) => String(x.questionId) === String(qid))
  return d || { correct: false, correctAnswer: '', explanation: '' }
}

/* ---- 工具 ---- */
function parseOptions(str) {
  if (!str) return []
  if (Array.isArray(str)) return str
  try {
    const p = JSON.parse(str)
    if (Array.isArray(p)) return p
    if (p && typeof p === 'object') return Object.values(p)
  } catch {
    // JSON 解析失败，回退为文本分割（下方 return），仅开发环境记录
    if (import.meta.env.DEV) {
      // eslint-disable-next-line no-console
      logger.warn('选项解析失败，已回退为文本分割')
    }
  }
  return String(str)
    .split(/[\n;；]+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

function letter(i) {
  return ['A', 'B', 'C', 'D', 'E', 'F'][i] || String.fromCharCode(65 + i)
}
function isSingle(t) {
  return t === 'SINGLE_CHOICE' || t === 'SINGLE'
}
function isMultiple(t) {
  return t === 'MULTIPLE_CHOICE' || t === 'MULTIPLE'
}
function isJudge(t) {
  return t === 'TRUE_FALSE' || t === 'JUDGE'
}
function qTypeLabel(t) {
  if (isSingle(t)) return '单选'
  if (isMultiple(t)) return '多选'
  if (isJudge(t)) return '判断'
  return t || '题'
}
function qTypeClass(t) {
  if (isSingle(t)) return 't-single'
  if (isMultiple(t)) return 't-multiple'
  if (isJudge(t)) return 't-judge'
  return ''
}
function taskTypeLabel(t) {
  return (
    {
      VIDEO: '视频',
      DOC: '文档',
      DOCUMENT: '文档',
      READING: '阅读',
      QUIZ: '测验',
      ASSIGNMENT: '作业',
    }[t] || '任务'
  )
}

function onDrawerClosed() {
  videoSrc.value = ''
  localWatched.value = 0
  lastBeat.value = 0
  quizQuestions.value = []
  for (const k in quizAnswers) delete quizAnswers[k]
  quizResult.value = null
}

onMounted(load)
onBeforeUnmount(() => {
  videoSrc.value = ''
})
</script>

<style scoped>
.lc-header {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  margin-bottom: var(--ac-spacing-md);
  box-shadow: var(--ac-shadow-sm);
}
.lc-title {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-left: var(--ac-spacing-md);
}
.lc-title h1 {
  font-size: 20px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0;
}
.lc-progress {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-md);
  margin-top: var(--ac-spacing-md);
  padding-top: var(--ac-spacing-md);
  border-top: 1px solid var(--ac-border);
}
.lc-progress-num {
  display: flex;
  align-items: baseline;
  gap: 4px;
  white-space: nowrap;
}
.lc-progress-num .done {
  font-size: 24px;
  font-weight: 700;
  color: var(--ac-primary);
}
.lc-progress-num .sep {
  color: var(--ac-text-muted);
}
.lc-progress-num .total {
  font-size: 18px;
  font-weight: 600;
  color: var(--ac-text-secondary);
}
.lc-progress-num .unit {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-left: 2px;
}
.lc-percent {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-primary);
  min-width: 44px;
  text-align: right;
}

.lc-tree-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  box-shadow: var(--ac-shadow-sm);
}
.lc-tree-head {
  display: flex;
  align-items: baseline;
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}
.lc-tree-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
  padding-left: 12px;
  border-left: 3px solid var(--ac-primary);
}
.lc-tree-hint {
  font-size: 12px;
  color: var(--ac-text-muted);
}

/* 任务点面板 */
.task-panel {
  padding: 4px;
}
.task-alert {
  margin: var(--ac-spacing-md) 0;
}
.video-wrap {
  background: #000;
  border-radius: var(--ac-radius-md);
  overflow: hidden;
}
.video-player {
  width: 100%;
  max-height: 360px;
  display: block;
  background: #000;
}
.video-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-top: var(--ac-spacing-sm);
}
.no-resource {
  text-align: center;
  padding: var(--ac-spacing-xl) 0;
  color: var(--ac-text-muted);
}
.no-resource p {
  margin: var(--ac-spacing-sm) 0 var(--ac-spacing-md);
}

.doc-panel {
  text-align: center;
  padding: var(--ac-spacing-lg) 0;
}
.doc-desc {
  font-size: 15px;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}

.assignment-panel {
  text-align: center;
  padding: var(--ac-spacing-lg) 0;
}
.assignment-panel h3 {
  margin: var(--ac-spacing-sm) 0;
  color: var(--ac-text-primary);
}
.assignment-panel .muted {
  color: var(--ac-text-muted);
  font-size: 13px;
  margin: 4px 0;
}
.assignment-panel .el-button {
  margin-top: var(--ac-spacing-md);
}

/* 测验 */
.quiz-panel {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-lg);
}
.quiz-q {
  border-bottom: 1px solid var(--ac-border);
  padding-bottom: var(--ac-spacing-md);
}
.quiz-q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.quiz-q-no {
  font-weight: 700;
  color: var(--ac-text-primary);
}
.quiz-q-type {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
  border: 1px solid var(--ac-border);
}
.quiz-q-type.t-single {
  color: var(--ac-primary);
  background: var(--ac-primary-bg);
}
.quiz-q-type.t-multiple {
  color: var(--ac-warning);
  background: var(--ac-warning-bg);
}
.quiz-q-type.t-judge {
  color: var(--ac-success);
  background: var(--ac-success-bg);
}
.quiz-q-stem {
  font-size: 14px;
  color: var(--ac-text-primary);
  line-height: 1.7;
  margin: 0 0 var(--ac-spacing-sm);
}
.opt-vertical {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.opt-item {
  white-space: normal;
  height: auto;
  margin-right: 0;
}
.opt-label {
  font-weight: 600;
  margin-right: 4px;
}
.judge-btns {
  display: flex;
  gap: var(--ac-spacing-md);
}

.quiz-feedback {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 13px;
  margin-top: var(--ac-spacing-sm);
  padding: 8px 10px;
  border-radius: var(--ac-radius-sm);
}
.quiz-feedback.ok {
  color: var(--ac-success);
  background: var(--ac-success-bg);
}
.quiz-feedback.no {
  color: var(--ac-danger);
  background: var(--ac-danger-bg);
}
.quiz-exp {
  margin: 4px 0 0;
  color: var(--ac-text-secondary);
  font-size: 12px;
}

.quiz-actions {
  margin-top: var(--ac-spacing-md);
}
</style>
