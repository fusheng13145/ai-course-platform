<template>
  <el-drawer
    v-model="visible"
    :title="drawerTitle"
    :size="drawerSize"
    direction="rtl"
    @closed="onClosed"
  >
    <div v-loading="taskLoading" class="task-panel">
      <!-- 视频任务点 -->
      <template v-if="task && isVideo">
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
      <template v-else-if="task && isDoc">
        <div class="doc-panel">
          <p class="doc-desc">{{ task.title }}</p>
          <el-alert
            v-if="task.completed"
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
      <template v-else-if="task && isAssignment">
        <div class="assignment-panel">
          <el-icon :size="40" color="var(--ac-danger)"><Files /></el-icon>
          <h3>{{ task.title }}</h3>
          <p v-if="task.dueDate" class="muted">截止：{{ formatTime(task.dueDate, '--') }}</p>
          <p class="muted">作业请在「待办 / 作业」区域提交，提交后本任务点自动完成。</p>
          <el-button type="primary" @click="goAssignments">前往作业区</el-button>
        </div>
      </template>

      <!-- 测验任务点 -->
      <template v-else-if="task && isQuiz">
        <div v-if="quizQuestions.length" class="quiz-panel">
          <div v-for="(q, i) in quizQuestions" :key="q.id" class="quiz-q">
            <div class="quiz-q-head">
              <span class="quiz-q-no">{{ i + 1 }}.</span>
              <span class="quiz-q-type" :class="qTypeClass(q.type)">{{ qTypeLabel(q.type) }}</span>
            </div>
            <p class="quiz-q-stem">{{ q.content }}</p>

            <!-- 单选 -->
            <el-radio-group
              v-if="isSingle(q.type)"
              v-model="quizAnswers[q.id]"
              class="opt-vertical"
            >
              <el-radio v-for="(opt, oi) in q._options" :key="oi" :value="oi" class="opt-item">
                <span class="opt-label">{{ letter(oi) }}.</span> {{ opt }}
              </el-radio>
            </el-radio-group>

            <!-- 多选 -->
            <el-checkbox-group
              v-else-if="isMultiple(q.type)"
              v-model="quizAnswers[q.id]"
              class="opt-vertical"
            >
              <el-checkbox v-for="(opt, oi) in q._options" :key="oi" :label="oi" class="opt-item">
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
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Select, VideoPlay, Files, InfoFilled, Check, Close } from '@element-plus/icons-vue'
import { formatTime } from '@/utils/datetime'
import logger from '@/utils/logger'
import studentApi from '@/api/student'
import { useVideoTask } from '@/composables/useVideoTask'
import { useQuizTask } from '@/composables/useQuizTask'

/**
 * 任务点抽屉组件
 * <p>承接学习中心（LearningCenter）中任务点展开与交互的全部职责：
 * 视频（续播/心跳/自动完成）、文档/阅读（标记完成）、作业（跳转作业区）、
 * 测验（抽题/作答/即时判分）。数据流：任务点对象经 {@code task} prop 注入，
 * 完成/关闭/跳转经事件上报父组件。</p>
 */
const props = defineProps({
  /** v-model 抽屉可见性 */
  modelValue: { type: Boolean, default: false },
  /** 当前活动任务点（含 taskType/id/title/completed 等） */
  task: { type: Object, default: null },
  /** 课程 ID（视频续播状态与抽题按课程隔离） */
  courseId: { type: [String, Number], required: true },
})

const emit = defineEmits(['update:modelValue', 'closed', 'task-completed'])

const router = useRouter()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const taskLoading = ref(false)
const drawerSize = computed(() => (window.innerWidth < 768 ? '100%' : '620px'))

const isVideo = computed(() => props.task?.taskType === 'VIDEO')
const isDoc = computed(() => ['DOC', 'DOCUMENT', 'READING'].includes(props.task?.taskType))
const isAssignment = computed(() => props.task?.taskType === 'ASSIGNMENT')
const isQuiz = computed(() => props.task?.taskType === 'QUIZ')

const drawerTitle = computed(() => {
  if (!props.task) return '任务点'
  return `${taskTypeLabel(props.task.taskType)} · ${props.task.title}`
})

/* ---- 任务点类型文案 ---- */
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

/* ---- 视频 / 测验逻辑（组合式函数） ---- */
const {
  videoEl,
  videoSrc,
  videoDuration,
  localWatched,
  videoState,
  loadVideo,
  onVideoMeta,
  onVideoTime,
  onVideoEnded,
  markVideoFallback,
  resetVideo,
} = useVideoTask({
  courseId: props.courseId,
  getTask: () => props.task,
  onTaskCompleted: async () => emit('task-completed'),
})

const {
  quizQuestions,
  quizAnswers,
  quizSubmitting,
  quizResult,
  loadQuiz,
  submitQuizNow,
  feedbackOf,
  resetQuiz,
  letter,
  isSingle,
  isMultiple,
  isJudge,
  qTypeLabel,
  qTypeClass,
} = useQuizTask({
  courseId: props.courseId,
  getTask: () => props.task,
  onTaskCompleted: async () => emit('task-completed'),
})

/* ---- 文档 / 阅读：标记完成 ---- */
const docLoading = ref(false)
async function markDocDone() {
  docLoading.value = true
  try {
    const type = props.task.taskType === 'READING' ? 'READING' : 'DOC'
    await studentApi.markTaskComplete(props.courseId, props.task.id, type)
    ElMessage.success('已完成')
    // task 为父组件传入的共享对象引用（activeTask），嵌套属性同步完成任务状态，非 prop 整体替换
    props.task.completed = true
    emit('task-completed')
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    docLoading.value = false
  }
}

/* ---- 作业：前往作业区 ---- */
function goAssignments() {
  visible.value = false
  router.push('/student/dashboard')
}

/* ---- 任务点数据加载与抽屉关闭清理 ---- */
async function openTask(task) {
  taskLoading.value = true
  try {
    if (isVideo.value) await loadVideo(task)
    else if (isQuiz.value) await loadQuiz()
  } catch (e) {
    logger.warn('任务点加载失败:', e)
  } finally {
    taskLoading.value = false
  }
}

function onClosed() {
  resetVideo()
  resetQuiz()
  emit('closed')
}

// 抽屉打开且任务点变化时加载对应任务点数据
watch(
  () => [visible.value, props.task],
  () => {
    if (visible.value && props.task) openTask(props.task)
  },
)
</script>

<style scoped>
/* 任务点面板 */
.task-panel {
  padding: 4px;
}
.task-alert {
  margin: var(--ac-spacing-md) 0;
}
.video-wrap {
  background: var(--ink);
  border-radius: var(--ac-radius-md);
  overflow: hidden;
}
.video-player {
  width: 100%;
  max-height: 360px;
  display: block;
  background: var(--ink);
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
