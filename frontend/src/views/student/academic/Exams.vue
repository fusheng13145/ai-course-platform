﻿<template>
  <div class="fade-in">
    <!-- ===== 考试列表视图 ===== -->
    <div v-if="viewMode === 'list'">
      <!-- 搜索工具栏 -->
      <div class="search-toolbar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索考试名称..."
          clearable
          :prefix-icon="Search"
          style="width: 260px"
        />
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 140px">
          <el-option label="可参加" value="available" />
          <el-option label="已完成" value="completed" />
          <el-option label="已过期" value="expired" />
        </el-select>
      </div>

      <!-- 考试卡片网格 -->
      <div class="exam-grid">
        <div
          v-for="exam in filteredExams"
          :key="exam.id"
          class="exam-card"
        >
          <div class="exam-card-header">
            <span class="exam-type-tag">{{ exam.type || '综合测试' }}</span>
            <el-tag
              :type="getExamStatusType(exam)"
              size="small"
              effect="light"
            >
              {{ getExamStatusLabel(exam) }}
            </el-tag>
          </div>

          <h3 class="exam-card-title">{{ exam.title }}</h3>

          <div class="exam-meta-grid">
            <div class="meta-item">
              <el-icon><Collection /></el-icon>
              <span>{{ exam.courseName || '未知课程' }}</span>
            </div>
            <div class="meta-item">
              <el-icon><Document /></el-icon>
              <span>{{ exam.totalScore || 100 }} 分</span>
            </div>
            <div class="meta-item">
              <el-icon><Timer /></el-icon>
              <span>{{ exam.duration || exam.timeLimit || 60 }} 分钟</span>
            </div>
            <div class="meta-item" v-if="exam.questionCount">
              <el-icon><List /></el-icon>
              <span>{{ exam.questionCount }} 题</span>
            </div>
          </div>

          <p class="exam-desc">{{ exam.description || '暂无描述' }}</p>

          <div class="exam-card-footer">
            <span class="exam-time" v-if="exam.startTime">
              {{ formatTime(exam.startTime) }} ~ {{ formatTime(exam.endTime) }}
            </span>
            <el-button
              type="primary"
              @click="enterExam(exam)"
              :disabled="exam.completed || exam.status === 'EXPIRED'"
            >
              {{ exam.completed ? '已完成' : (exam.status === 'EXPIRED' ? '已过期' : '开始考试') }}
            </el-button>
          </div>
        </div>
      </div>

      <el-empty v-if="!listLoading && filteredExams.length === 0" description="暂无考试" :image-size="120" />
    </div>

    <!-- ===== 答题视图（全屏模式）===== -->
    <div v-else-if="viewMode === 'exam'" class="exam-container">
      <!-- 顶部栏：倒计时 + 导航 -->
      <div class="exam-topbar">
        <div class="topbar-left">
          <el-button text @click="confirmExitExam">
            <el-icon><ArrowLeft /></el-icon> 退出考试
          </el-button>
          <h2 class="exam-paper-title">{{ currentExamPaper?.title || '试卷' }}</h2>
        </div>
        <div class="topbar-center">
          <!-- 题目导航 -->
          <div class="question-nav">
            <button
              v-for="(q, idx) in questions"
              :key="q.id"
              class="nav-dot"
              :class="{
                active: currentIndex === idx,
                answered: isQuestionAnswered(idx),
                current: currentIndex === idx
              }"
              @click="goToQuestion(idx)"
            >
              {{ idx + 1 }}
            </button>
          </div>
        </div>
        <div class="topbar-right">
          <div class="countdown-box" :class="{ warning: remainingMinutes <= 5 }">
            <el-icon><AlarmClock /></el-icon>
            <span class="countdown-text">{{ countdownDisplay }}</span>
          </div>
          <el-button type="success" @click="confirmSubmit">提交试卷</el-button>
        </div>
      </div>

      <!-- 题目区域 -->
      <div class="exam-body" v-loading="questionsLoading">
        <div v-if="currentQuestion" class="question-area">
          <!-- 题号和类型 -->
          <div class="question-label-row">
            <el-tag :type="getQuestionTypeTag(currentQuestion.type)" size="small">
              第 {{ currentIndex + 1 }} 题 · {{ getQuestionTypeName(currentQuestion.type) }}
            </el-tag>
            <span class="question-score">（{{ currentQuestion.score || defaultScore }} 分）</span>
          </div>

          <!-- 题干内容 -->
          <div class="question-content">
            {{ currentQuestion.content || currentQuestion.questionContent }}
          </div>

          <!-- 单选题 -->
          <div v-if="currentQuestion.type === 'SINGLE_CHOICE'" class="answer-area">
            <el-radio-group v-model="answers[currentIndex]" class="radio-group-vertical">
              <el-radio
                v-for="(opt, optIdx) in parseOptions(currentQuestion.options)"
                :key="optIdx"
                :value="optIdx"
                class="option-radio"
              >
                <span class="option-label">{{ ['A', 'B', 'C', 'D', 'E', 'F'][optIdx] }}.</span>
                {{ opt }}
              </el-radio>
            </el-radio-group>
          </div>

          <!-- 多选题 -->
          <div v-else-if="currentQuestion.type === 'MULTIPLE_CHOICE'" class="answer-area">
            <el-checkbox-group v-model="multiAnswers[currentIndex]" class="checkbox-group-vertical">
              <el-checkbox
                v-for="(opt, optIdx) in parseOptions(currentQuestion.options)"
                :key="optIdx"
                :label="optIdx"
                class="option-checkbox"
              >
                <span class="option-label">{{ ['A', 'B', 'C', 'D', 'E', 'F'][optIdx] }}.</span>
                {{ opt }}
              </el-checkbox>
            </el-checkbox-group>
          </div>

          <!-- 判断题 -->
          <div v-else-if="currentQuestion.type === 'TRUE_FALSE'" class="answer-area">
            <div class="judge-buttons">
              <el-button
                :type="answers[currentIndex] === true ? 'primary' : ''"
                @click="answers[currentIndex] = true"
                size="large"
                class="judge-btn"
              >
                <el-icon><CircleCheck /></el-icon> 正确
              </el-button>
              <el-button
                :type="answers[currentIndex] === false ? 'danger' : ''"
                @click="answers[currentIndex] = false"
                size="large"
                class="judge-btn"
              >
                <el-icon><CircleClose /></el-icon> 错误
              </el-button>
            </div>
          </div>

          <!-- 简答题 -->
          <div v-else-if="currentQuestion.type === 'SHORT_ANSWER'" class="answer-area">
            <el-input
              v-model="textAnswers[currentIndex]"
              type="textarea"
              :rows="6"
              placeholder="请输入您的答案..."
              resize="vertical"
            />
          </div>

          <!-- 底部导航按钮 -->
          <div class="question-nav-buttons">
            <el-button @click="prevQuestion" :disabled="currentIndex === 0">
              <el-icon><ArrowLeft /></el-icon> 上一题
            </el-button>
            <span class="question-counter">{{ currentIndex + 1 }} / {{ questions.length }}</span>
            <el-button
              v-if="currentIndex < questions.length - 1"
              type="primary"
              @click="nextQuestion"
            >
              下一题<el-icon><ArrowRight /></el-icon>
            </el-button>
            <el-button
              v-else
              type="success"
              @click="confirmSubmit"
            >
              提交试卷
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 成绩结果视图 ===== -->
    <div v-else-if="viewMode === 'result'" class="result-container">
      <div class="result-card">
        <div class="result-icon-wrapper" :class="getResultClass()">
          <el-icon :size="64"><TrophyBase /></el-icon>
        </div>
        <h2 class="result-title">考试完成</h2>
        <div class="score-display">
          <span class="score-number">{{ examResult.score ?? '--' }}</span>
          <span class="score-total">/ {{ examResult.totalScore || currentExamPaper?.totalScore || 100 }}</span>
        </div>
        <p class="result-message">{{ resultMessage }}</p>
        <div class="result-actions">
          <el-button type="primary" @click="backToList">返回考试列表</el-button>
          <el-button @click="viewDetailResult" v-if="examResult.id">查看详情</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Collection, Document, Timer, List,
  ArrowLeft, ArrowRight, AlarmClock,
  CircleCheck, CircleClose, TrophyBase
} from '@element-plus/icons-vue'
import examApi from '@/api/exam'

/* ---- 视图模式：list / exam / result ---- */
const viewMode = ref('list')

/* ---- 列表页状态 ---- */
const searchKeyword = ref('')
const statusFilter = ref('')
const listLoading = ref(false)
const examList = ref([])

/* ---- 答题状态 ---- */
const currentExamPaper = ref(null)
const sessionId = ref(null)
const questions = ref([])
const questionsLoading = ref(false)
const currentIndex = ref(0)
const answers = ref({})           // 单选/判断答案
const multiAnswers = ref({})     // 多选答案
const textAnswers = ref({})      // 简答答案
const defaultScore = 10

/* ---- 倒计时 ---- */
let countdownTimer = null
const totalSeconds = ref(0)
const remainingSeconds = ref(0)

/* ---- 成绩结果 ---- */
const examResult = ref({})
const autoSaveTimer = ref(null)

/* ---- 计算属性 ---- */

/** 过滤后的考试列表 */
const filteredExams = computed(() => {
  let list = examList.value
  if (searchKeyword.value) {
    list = list.filter(e => e.title?.includes(searchKeyword.value))
  }
  if (statusFilter.value) {
    list = list.filter(e => {
      if (statusFilter.value === 'completed') return e.completed
      if (statusFilter.value === 'available') return !e.completed && e.status !== 'EXPIRED'
      if (statusFilter.value === 'expired') return e.status === 'EXPIRED'
      return true
    })
  }
  return list
})

/** 当前题目 */
const currentQuestion = computed(() => questions.value[currentIndex.value] || null)

/** 剩余分钟数 */
const remainingMinutes = computed(() => Math.floor(remainingSeconds.value / 60))

/** 倒计时显示文本 */
const countdownDisplay = computed(() => {
  const m = Math.floor(remainingSeconds.value / 60)
  const s = remainingSeconds.value % 60
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
})

/** 结果提示信息 */
const resultMessage = computed(() => {
  const score = examResult.value.score
  if (score == null) return '成绩正在批阅中，请稍后查看...'
  if (score >= 90) return '表现优秀！继续保持！'
  if (score >= 75) return '良好水平，再接再厉！'
  if (score >= 60) return '及格了，还有提升空间哦~'
  return '未达到及格线，建议复习后再次尝试。'
})

/* ---- 方法：列表页 ---- */

async function loadExamList() {
  listLoading.value = true
  try {
    const res = await examApi.getExamPaperList()
    const rawData = res.data?.records || res.data?.list || res.data || res
    examList.value = Array.isArray(rawData) ? rawData : []

    // 标记已完成状态（检查是否有提交记录）
    try {
      const sessionRes = await examApi.getMyExamSessions()
      const sessions = sessionRes.data || sessionRes || []
      const submittedIds = new Set(
        (Array.isArray(sessions) ? sessions : [])
          .filter(s => s.status === 'SUBMITTED' || s.status === 'GRADED')
          .map(s => s.examPaperId)
      )
      examList.value.forEach(e => {
        if (submittedIds.has(e.id)) e.completed = true
      })
    } catch { /* 忽略 */ }
  } catch (e) {
    console.error('加载考试列表失败:', e)
  } finally {
    listLoading.value = false
  }
}

function getExamStatusType(exam) {
  if (exam.completed) return 'info'
  if (exam.status === 'EXPIRED') return 'info'
  return 'warning'
}

function getExamStatusLabel(exam) {
  if (exam.completed) return '已完成'
  if (exam.status === 'EXPIRED') return '已过期'
  return '进行中'
}

function formatTime(timeStr) {
  if (!timeStr) return ''
  return timeStr.replace('T', ' ').substring(0, 16)
}

/* ---- 方法：进入考试 ---- */

async function enterExam(exam) {
  if (exam.completed) {
    ElMessage.info('该考试已完成')
    return
  }

  try {
    ElMessageBox.confirm(`确定开始「${exam.title}」吗？\n进入后将启动倒计时。`, '确认开始考试', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    }).then(async () => {
      // 调用 startExam API
      const res = await examApi.startExam(exam.id)
      const sid = res.data?.id || res?.id
      sessionId.value = sid

      // 加载题目
      await loadQuestions(exam.id)

      // 设置当前试卷信息
      currentExamPaper.value = exam

      // 初始化倒计时
      const duration = exam.duration || exam.timeLimit || 60
      totalSeconds.value = duration * 60
      remainingSeconds.value = totalSeconds.value

      // 切换到答题视图
      viewMode.value = 'exam'

      // 启动倒计时
      startCountdown()

      // 启动自动保存（每30秒）
      startAutoSave()

      ElMessage.success('考试已开始，祝你好运！')
    }).catch(() => {})
  } catch (e) {
    console.error('开始考试失败:', e)
    ElMessage.error('无法开始考试，请稍后重试')
  }
}

/* ---- 方法：加载题目 ---- */

async function loadQuestions(paperId) {
  questionsLoading.value = true
  try {
    const res = await examApi.getExamQuestions(paperId)
    const data = res.data || res || []
    questions.value = Array.isArray(data) ? data : []
    // 初始化答案对象
    answers.value = {}
    multiAnswers.value = {}
    textAnswers.value = {}
  } catch (e) {
    console.error('加载题目失败:', e)
    ElMessage.error('加载题目失败')
  } finally {
    questionsLoading.value = false
  }
}

/* ---- 方法：答题导航 ---- */

function goToQuestion(index) {
  saveCurrentAnswer()
  currentIndex.value = index
}

function prevQuestion() {
  if (currentIndex.value > 0) {
    goToQuestion(currentIndex.value - 1)
  }
}

function nextQuestion() {
  if (currentIndex.value < questions.value.length - 1) {
    goToQuestion(currentIndex.value + 1)
  }
}

function isQuestionAnswered(index) {
  const q = questions.value[index]
  if (!q) return false
  if (q.type === 'MULTIPLE_CHOICE') {
    return multiAnswers.value[index]?.length > 0
  }
  if (q.type === 'SHORT_ANSWER') {
    return !!textAnswers.value[index]
  }
  return answers.value[index] !== undefined && answers.value[index] !== null
}

/* ---- 方法：题目类型处理 ---- */

function getQuestionTypeName(type) {
  const map = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    SHORT_ANSWER: '简答题'
  }
  return map[type] || '未知题型'
}

function getQuestionTypeTag(type) {
  const map = {
    SINGLE_CHOICE: '',
    MULTIPLE_CHOICE: 'warning',
    TRUE_FALSE: 'info',
    SHORT_ANSWER: 'success'
  }
  return map[type] || ''
}

function parseOptions(options) {
  if (!options) return []
  if (typeof options === 'string') {
    try {
      const parsed = JSON.parse(options)
      return Array.isArray(parsed) ? parsed : [options]
    } catch {
      return options.split('\n').filter(Boolean)
    }
  }
  return Array.isArray(options) ? options : [options]
}

/* ---- 方法：倒计时 ---- */

function startCountdown() {
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    if (remainingSeconds.value > 0) {
      remainingSeconds.value--
      if (remainingSeconds.value <= 300 && remainingSeconds.value % 60 === 0) {
        ElMessage.warning(`剩余时间：${Math.ceil(remainingSeconds.value / 60)} 分钟`)
      }
    } else {
      // 时间到，自动提交
      clearInterval(countdownTimer)
      ElMessage.warning('考试时间已到，将自动提交试卷')
      submitExam()
    }
  }, 1000)
}

/* ---- 方法：自动保存 ---- */

function startAutoSave() {
  if (autoSaveTimer.value) clearInterval(autoSaveTimer.value)
  autoSaveTimer.value = setInterval(() => {
    autoSaveAnswers()
  }, 30000) // 每30秒
}

async function autoSaveAnswers() {
  if (!sessionId.value) return
  try {
    const answerData = collectAnswers()
    // 使用专用的自动保存 API
    await examApi.autoSaveAnswers(sessionId.value, { answers: answerData })
  } catch (e) {
    // autoSave 接口不可用时回退到 submit（兼容模式）
    console.warn('自动保存失败，尝试回退:', e)
    try {
      await examApi.submitExam(sessionId.value, { answers: collectAnswers(), autoSave: true })
    } catch (e2) {
      console.warn('回退保存也失败:', e2)
    }
  }
}

function collectAnswers() {
  const result = []
  questions.value.forEach((q, idx) => {
    let answer
    if (q.type === 'MULTIPLE_CHOICE') {
      answer = multiAnswers.value[idx] || []
    } else if (q.type === 'SHORT_ANSWER') {
      answer = textAnswers.value[idx] || ''
    } else {
      answer = answers.value[idx]
    }
    result.push({
      questionId: q.id,
      questionType: q.type,
      answer: answer
    })
  })
  return result
}

function saveCurrentAnswer() {
  // 当前答案已在响应式数据中，此方法用于扩展逻辑
}

/* ---- 方法：提交试卷 ---- */

async function confirmSubmit() {
  const unansweredCount = questions.value.filter((_, idx) => !isQuestionAnswered(idx)).length

  let msg = '确定要提交试卷吗？'
  if (unansweredCount > 0) {
    msg += `\n您还有 ${unansweredCount} 道题目未作答。`
  }

  try {
    await ElMessageBox.confirm(msg, '确认提交', {
      confirmButtonText: '确认提交',
      cancelButtonText: '继续答题',
      type: 'warning'
    })

    // 清理定时器
    cleanupTimers()
    await submitExam()
  } catch {
    // 用户取消
  }
}

async function submitExam() {
  try {
    const answerData = collectAnswers()
    const res = await examApi.submitExam(sessionId.value, { answers: answerData })
    const result = res.data || res || {}

    // 尝试获取即时成绩
    try {
      const resultRes = await examApi.getExamResult(sessionId.value)
      examResult.value = resultRes.data || resultRes || result
    } catch {
      examResult.value = { score: null, ...result }
    }

    viewMode.value = 'result'
  } catch (e) {
    console.error('提交试卷失败:', e)
    ElMessage.error('提交失败，请重试')
  }
}

/* ---- 方法：退出考试 ---- */

async function confirmExitExam() {
  try {
    await ElMessageBox.confirm(
      '确定要退出考试吗？\n已作答的进度将被保留。',
      '退出确认',
      { confirmButtonText: '确定退出', cancelButtonText: '继续答题', type: 'warning' }
    )

    // 先自动保存一次
    await autoSaveAnswers()
    cleanupTimers()
    viewMode.value = 'list'
  } catch {
    // 取消
  }
}

function backToList() {
  viewMode.value = 'list'
  resetExamState()
}

async function viewDetailResult() {
  if (!sessionId.value) return
  try {
    const res = await examApi.getScoreReport(sessionId.value)
    const report = res.data || res || {}
    ElMessage.info(`详细报告：得分 ${report.score ?? '--'} / ${report.totalScore ?? 100}`)
    // TODO: 可扩展为弹窗展示完整报告
  } catch (e) {
    console.error('获取成绩报告失败:', e)
    ElMessage.info('成绩详情加载失败')
  }
}

function resetExamState() {
  sessionId.value = null
  currentExamPaper.value = null
  questions.value = []
  currentIndex.value = 0
  answers.value = {}
  multiAnswers.value = {}
  textAnswers.value = {}
  examResult.value = {}
}

function cleanupTimers() {
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
  if (autoSaveTimer.value) { clearInterval(autoSaveTimer.value); autoSaveTimer.value = null }
}

function getResultClass() {
  const score = examResult.value.score
  if (score >= 90) return 'excellent'
  if (score >= 75) return 'good'
  if (score >= 60) return 'pass'
  return 'fail'
}

/* ---- 生命周期 ---- */

onMounted(() => {
  loadExamList()
})

onUnmounted(() => {
  cleanupTimers()
})
</script>

<style scoped>
/* ---- 搜索工具栏 ---- */
.search-toolbar {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}

/* ---- 考试卡片网格 ---- */
.exam-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: var(--ac-spacing-md);
}

.exam-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  transition: all var(--ac-transition-normal);
  display: flex;
  flex-direction: column;
}

.exam-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--ac-shadow-md);
  border-left: 4px solid var(--ac-primary);
}

.exam-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-sm);
}

.exam-type-tag {
  font-size: 12px;
  color: var(--ac-text-muted);
  background: var(--ac-bg);
  padding: 2px 10px;
  border-radius: 999px;
}

.exam-card-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0 0 var(--ac-spacing-sm);
}

.exam-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
  margin-bottom: var(--ac-spacing-sm);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ac-text-secondary);
}

.meta-item .el-icon {
  font-size: 14px;
  color: var(--ac-text-muted);
}

.exam-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
  line-height: 1.6;
  margin: 0 0 var(--ac-spacing-md);
  flex: 1;
}

.exam-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--ac-spacing-sm);
  border-top: 1px solid var(--ac-border);
}

.exam-time {
  font-size: 12px;
  color: var(--ac-text-muted);
}

/* ====== 答题视图样式 ====== */
.exam-container {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  z-index: 1000;
  background: var(--ac-bg);
  display: flex;
  flex-direction: column;
}

.exam-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: var(--ac-bg-elevated);
  border-bottom: 1px solid var(--ac-border);
  box-shadow: var(--ac-shadow-sm);
  gap: var(--ac-spacing-md);
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-md);
  min-width: 200px;
}

.exam-paper-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}

.topbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.question-nav {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: center;
  max-width: 600px;
}

.nav-dot {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--ac-border);
  background: var(--ac-bg-elevated);
  color: var(--ac-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--ac-transition-fast);
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-dot:hover { border-color: var(--ac-primary); color: var(--ac-primary); }
.nav-dot.active { background: var(--ac-primary); color: #fff; border-color: var(--ac-primary); }
.nav-dot.answered:not(.active) {
  background: rgba(139,168,136,0.15);
  border-color: var(--ac-success);
  color: var(--ac-success);
}
.nav-dot.current { box-shadow: 0 0 0 2px var(--ac-primary-light); }

.topbar-right {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-md);
  min-width: 200px;
  justify-content: flex-end;
}

.countdown-box {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--ac-radius-sm);
  background: var(--ac-bg);
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

.countdown-box.warning {
  background: rgba(196,128,128,0.12);
  color: var(--ac-danger);
  animation: blink 1s infinite;
}

@keyframes blink {
  50% { opacity: 0.7; }
}

.countdown-text { letter-spacing: 1px; }

/* ---- 答题主体 ---- */
.exam-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--ac-spacing-xl);
  display: flex;
  justify-content: center;
}

.question-area {
  max-width: 800px;
  width: 100%;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-xl);
  box-shadow: var(--ac-shadow-sm);
}

.question-label-row {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  margin-bottom: var(--ac-spacing-md);
}

.question-score {
  font-size: 13px;
  color: var(--ac-text-muted);
}

.question-content {
  font-size: 16px;
  line-height: 1.9;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-lg);
  font-weight: 500;
}

.answer-area {
  margin-bottom: var(--ac-spacing-xl);
}

.radio-group-vertical {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-sm);
}

.option-radio {
  display: flex;
  align-items: flex-start;
  padding: 12px 16px;
  border-radius: var(--ac-radius-sm);
  border: 1px solid var(--ac-border);
  margin-right: 0;
  transition: all var(--ac-transition-fast);
  width: 100%;
  height: auto;
}

.option-radio:hover {
  background: var(--ac-bg-hover);
  border-color: var(--ac-primary-light);
}

.option-radio.is-checked {
  background: var(--ac-primary-bg);
  border-color: var(--ac-primary);
}

.option-label {
  font-weight: 600;
  color: var(--ac-primary);
  margin-right: 8px;
  min-width: 22px;
}

.checkbox-group-vertical {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-sm);
}

.option-checkbox {
  display: flex;
  align-items: flex-start;
  padding: 12px 16px;
  border-radius: var(--ac-radius-sm);
  border: 1px solid var(--ac-border);
  margin-right: 0;
  transition: all var(--ac-transition-fast);
  width: 100%;
  height: auto;
}

.option-checkbox:hover {
  background: var(--ac-bg-hover);
  border-color: var(--ac-secondary-light);
}

.option-checkbox.is-checked {
  background: rgba(184,154,138,0.08);
  border-color: var(--ac-secondary);
}

.judge-buttons {
  display: flex;
  gap: var(--ac-spacing-lg);
  justify-content: center;
  padding: var(--ac-spacing-lg) 0;
}

.judge-btn {
  min-width: 140px;
  height: 52px;
  font-size: 15px;
  border-radius: var(--ac-radius-md);
}

/* ---- 题目导航按钮 ---- */
.question-nav-buttons {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--ac-spacing-lg);
  border-top: 1px solid var(--ac-border);
}

.question-counter {
  font-size: 14px;
  color: var(--ac-text-muted);
  font-weight: 500;
}

/* ====== 成绩结果样式 ====== */
.result-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.result-card {
  text-align: center;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-xl);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-xl) var(--ac-spacing-xxl);
  max-width: 480px;
  box-shadow: var(--ac-shadow-lg);
}

.result-icon-wrapper {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto var(--ac-spacing-lg);
}

.result-icon-wrapper.excellent { background: rgba(139,168,136,0.15); color: var(--ac-success); }
.result-icon-wrapper.good { background: var(--ac-primary-bg); color: var(--ac-primary); }
.result-icon-wrapper.pass { background: rgba(203,181,138,0.18); color: var(--ac-warning); }
.result-icon-wrapper.fail { background: rgba(196,128,128,0.15); color: var(--ac-danger); }

.result-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0 0 var(--ac-spacing-md);
}

.score-display {
  margin-bottom: var(--ac-spacing-md);
}

.score-number {
  font-size: 56px;
  font-weight: 700;
  color: var(--ac-primary);
  line-height: 1;
}

.score-total {
  font-size: 20px;
  color: var(--ac-text-muted);
  font-weight: 500;
}

.result-message {
  font-size: 14px;
  color: var(--ac-text-secondary);
  margin: 0 0 var(--ac-spacing-lg);
  line-height: 1.6;
}

.result-actions {
  display: flex;
  gap: var(--ac-spacing-md);
  justify-content: center;
}

@media (max-width: 900px) {
  .exam-topbar {
    flex-wrap: wrap;
    padding: 10px 16px;
  }
  .topbar-left, .topbar-right { min-width: auto; flex: 1; }
  .exam-paper-title { max-width: 150px; font-size: 14px; }
  .question-body { padding: var(--ac-spacing-md); }
}
</style>
