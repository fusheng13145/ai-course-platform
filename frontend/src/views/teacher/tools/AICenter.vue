<template>
  <div class="ai-page fade-in">
    <!-- 顶部课程选择器 -->
    <div class="page-head">
      <div class="page-title">
        <el-icon :size="20" class="ttl-icon"><MagicStick /></el-icon>
        <div>
          <h3>AI 教学中心</h3>
          <p>智能答疑、学情分析、作文批改与学习路径规划</p>
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
        <template #default>选择一门课程后，即可使用各项 AI 教学能力。</template>
      </el-alert>

      <template v-else>
        <el-tabs v-model="activeTab" class="ai-tabs">
          <!-- 智能答疑 -->
          <el-tab-pane label="智能答疑" name="qa">
            <div class="qa-panel">
              <div ref="msgBox" class="qa-messages">
                <div v-if="messages.length === 0" class="qa-empty">
                  <el-icon :size="36"><ChatRound /></el-icon>
                  <p>向 AI 提问，获取基于本课程的解答</p>
                </div>
                <div v-for="(m, i) in messages" :key="i" class="qa-msg" :class="m.role">
                  <div class="qa-bubble">{{ m.content }}</div>
                </div>
              </div>
              <div class="qa-input">
                <el-input
                  v-model="qaInput"
                  type="textarea"
                  :rows="2"
                  resize="none"
                  placeholder="输入问题，回车发送…"
                  @keydown.enter.exact.prevent="sendQa"
                />
                <el-button type="primary" :loading="qaSending" @click="sendQa">
                  <el-icon><Promotion /></el-icon>发送
                </el-button>
              </div>
              <div v-if="sessionId" class="qa-session">
                会话已建立 ·
                <el-link type="danger" :underline="false" @click="resetSession">重置会话</el-link>
              </div>
            </div>
          </el-tab-pane>

          <!-- 薄弱点分析 -->
          <el-tab-pane label="薄弱点分析" name="weak">
            <div v-loading="weakLoading" class="chart-card">
              <div class="chart-title">
                <el-icon><Warning /></el-icon>薄弱知识点分析
              </div>
              <div v-if="weakStats" class="stat-grid stat-grid--sm">
                <div class="mini-stat">
                  <span class="mini-num">{{ weakStats.avgScore ?? '--' }}</span
                  ><span class="mini-label">平均分</span>
                </div>
                <div class="mini-stat">
                  <span class="mini-num">{{ weakStats.weakCount ?? weakPoints.length }}</span
                  ><span class="mini-label">薄弱点</span>
                </div>
                <div class="mini-stat">
                  <span class="mini-num">{{ weakStats.completionRate ?? '--' }}</span
                  ><span class="mini-label">完成率</span>
                </div>
                <div class="mini-stat">
                  <span class="mini-num">{{ weakStats.studentCount ?? '--' }}</span
                  ><span class="mini-label">学生数</span>
                </div>
              </div>
              <div v-if="weakSummary" class="ai-summary">{{ weakSummary }}</div>
              <el-table
                v-if="weakPoints.length"
                :data="weakPoints"
                size="small"
                empty-text="暂无薄弱点"
              >
                <el-table-column prop="pointName" label="知识点" min-width="160" />
                <el-table-column prop="masteryRate" label="掌握度" width="120">
                  <template #default="{ row }">
                    <el-progress :percentage="numPct(row.masteryRate)" :stroke-width="10" />
                  </template>
                </el-table-column>
                <el-table-column
                  prop="weakStudentCount"
                  label="薄弱人数"
                  width="100"
                  align="center"
                />
                <el-table-column prop="level" label="等级" width="100">
                  <template #default="{ row }">
                    <el-tag
                      size="small"
                      :type="row.level === 'HIGH' ? 'danger' : 'warning'"
                      effect="light"
                    >
                      {{ row.level === 'HIGH' ? '高危' : '关注' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂无薄弱点数据" :image-size="64" />
            </div>
          </el-tab-pane>

          <!-- 学习建议 -->
          <el-tab-pane label="学习建议" name="advice">
            <div v-loading="adviceLoading" class="chart-card">
              <div class="chart-title">
                <el-icon><Lightning /></el-icon>个性化学习建议
              </div>
              <p v-if="adviceSummary" class="ai-summary">{{ adviceSummary }}</p>
              <div v-if="adviceItems.length" class="advice-list">
                <div v-for="(it, i) in adviceItems" :key="i" class="advice-item">
                  <div class="ai-head">
                    <el-tag size="small" :type="levelType(it.level)" effect="light">{{
                      levelText(it.level)
                    }}</el-tag>
                    <span class="ai-title">{{ it.title }}</span>
                  </div>
                  <p class="ai-detail">{{ it.detail }}</p>
                </div>
              </div>
              <el-empty v-else description="暂无学习建议" :image-size="64" />
            </div>
          </el-tab-pane>

          <!-- 作文批改 -->
          <el-tab-pane label="作文批改" name="essay">
            <div class="essay-grid">
              <div class="chart-card">
                <div class="chart-title">
                  <el-icon><EditPen /></el-icon>作文内容
                </div>
                <el-input
                  v-model="essayText"
                  type="textarea"
                  :rows="12"
                  placeholder="粘贴学生作文内容…"
                  maxlength="5000"
                  show-word-limit
                />
                <div class="essay-actions">
                  <el-button type="primary" :loading="essayLoading" @click="runCorrect">
                    <el-icon><Check /></el-icon>批改
                  </el-button>
                  <el-button
                    :loading="polishLoading"
                    :disabled="!essayText.trim()"
                    @click="runPolish"
                  >
                    <el-icon><MagicStick /></el-icon>AI 润色
                  </el-button>
                  <el-button text @click="((essayText = ''), (essayResult = ''))">清空</el-button>
                </div>
              </div>
              <div class="chart-card">
                <div class="chart-title">
                  <el-icon><Document /></el-icon>批改结果
                </div>
                <div
                  v-if="essayResult"
                  v-loading="essayLoading"
                  class="ai-summary"
                  style="white-space: pre-wrap"
                >
                  {{ essayResult }}
                </div>
                <el-empty v-else description="点击「批改」查看结果" :image-size="64" />
              </div>
            </div>
          </el-tab-pane>

          <!-- 学习路径 -->
          <el-tab-pane label="学习路径" name="path">
            <div class="chart-card">
              <div class="chart-title">
                <el-icon><Guide /></el-icon>生成学习路径
              </div>
              <div class="path-form">
                <el-input
                  v-model="pathGoal"
                  placeholder="输入学习目标，如：掌握线性代数中的矩阵运算"
                  style="width: 100%"
                />
                <el-button type="primary" :loading="pathLoading" @click="runPath">
                  <el-icon><Promotion /></el-icon>生成路径
                </el-button>
              </div>
              <div v-if="pathResult" v-loading="pathLoading" class="path-result">
                <el-tag
                  size="small"
                  :type="pathResult.status === 'SUCCESS' ? 'success' : 'info'"
                  effect="light"
                >
                  {{ pathResult.status || '完成' }}
                </el-tag>
                <p class="ai-summary">{{ pathResult.message || '已生成学习路径' }}</p>
                <pre v-if="pathRaw" class="path-json">{{ pathRaw }}</pre>
              </div>
              <el-empty v-else description="输入目标后生成学习路径" :image-size="64" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import courseApi from '@/api/course'
import aiApi from '@/api/ai'
import {
  MagicStick,
  ChatRound,
  Promotion,
  Warning,
  Lightning,
  EditPen,
  Check,
  Document,
  Guide,
} from '@element-plus/icons-vue'

const courseLoading = ref(false)
const courseOptions = ref([])
const courseId = ref('')
const loading = ref(false)
const activeTab = ref('qa')

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
  resetSession()
  weakPoints.value = []
  adviceItems.value = []
  pathResult.value = null
  if (courseId.value) {
    loadWeak()
    loadAdvice()
  }
}

function numPct(v) {
  if (v == null) return 0
  if (typeof v === 'number' && v <= 1.0001 && v > 0) return Math.round(v * 100)
  return Number(v) || 0
}
function levelType(l) {
  return l === 'HIGH' || l === '高'
    ? 'danger'
    : l === 'MEDIUM' || l === '中'
      ? 'warning'
      : 'success'
}
function levelText(l) {
  return l === 'HIGH' || l === '高' ? '高' : l === 'MEDIUM' || l === '中' ? '中' : '低'
}

/* ---------- 智能答疑 ---------- */
const messages = ref([])
const qaInput = ref('')
const qaSending = ref(false)
const sessionId = ref('')
const msgBox = ref(null)

async function sendQa() {
  const text = qaInput.value.trim()
  if (!text) return
  if (!courseId.value) return ElMessage.warning('请先选择课程')
  messages.value.push({ role: 'user', content: text })
  qaInput.value = ''
  qaSending.value = true
  await scrollMsg()
  try {
    let res
    if (sessionId.value) {
      res = await aiApi.qaContinue({ sessionId: sessionId.value, message: text })
    } else {
      res = await aiApi.qaAsk({ courseId: courseId.value, question: text })
    }
    const body = unwrap(res)
    const data = unwrap(body) || {}
    if (data.sessionId) sessionId.value = data.sessionId
    messages.value.push({ role: 'ai', content: data.answer || '（暂无回答）' })
  } catch (e) {
    logger.error('智能答疑失败', e)
    messages.value.push({ role: 'ai', content: '抱歉，回答失败，请稍后重试。' })
  } finally {
    qaSending.value = false
    await scrollMsg()
  }
}

function resetSession() {
  sessionId.value = ''
  messages.value = []
}

async function scrollMsg() {
  await nextTick()
  if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
}

/* ---------- 薄弱点分析 ---------- */
const weakLoading = ref(false)
const weakPoints = ref([])
const weakStats = ref(null)
const weakSummary = ref('')

async function loadWeak() {
  if (!courseId.value) return
  weakLoading.value = true
  try {
    const r = await aiApi.weakPointsAnalysis({ courseId: courseId.value })
    const body = unwrap(r)
    const data = unwrap(body) || {}
    weakPoints.value = data.weakPoints || data.list || data.points || []
    weakStats.value = data.userStats || data.stats || null
    weakSummary.value = data.summary || ''
  } catch (e) {
    logger.error('薄弱点分析失败', e)
  } finally {
    weakLoading.value = false
  }
}

/* ---------- 学习建议 ---------- */
const adviceLoading = ref(false)
const adviceItems = ref([])
const adviceSummary = ref('')

async function loadAdvice() {
  if (!courseId.value) return
  adviceLoading.value = true
  try {
    const r = await aiApi.learningAdvice({ courseId: courseId.value })
    const body = unwrap(r)
    const data = unwrap(body) || {}
    adviceSummary.value = data.summary || ''
    adviceItems.value = data.items || data.list || []
  } catch (e) {
    logger.error('学习建议加载失败', e)
  } finally {
    adviceLoading.value = false
  }
}

/* ---------- 作文批改 ---------- */
const essayText = ref('')
const essayResult = ref('')
const essayLoading = ref(false)
const polishLoading = ref(false)

async function runCorrect() {
  if (!essayText.value.trim()) return ElMessage.warning('请输入作文内容')
  essayLoading.value = true
  try {
    const r = await aiApi.essayCorrect({ content: essayText.value.trim() })
    const body = unwrap(r)
    const data = unwrap(body) || {}
    essayResult.value = data.result || data.corrected || data.feedback || '（暂无批改结果）'
    ElMessage.success('批改完成')
  } catch (e) {
    logger.error('作文批改失败', e)
  } finally {
    essayLoading.value = false
  }
}

async function runPolish() {
  if (!essayText.value.trim()) return ElMessage.warning('请输入作文内容')
  polishLoading.value = true
  try {
    const r = await aiApi.essayPolish({ content: essayText.value.trim() })
    const body = unwrap(r)
    const data = unwrap(body) || {}
    const polished = data.result || data.polished || data.content || ''
    if (polished) {
      essayText.value = polished
      ElMessage.success('已润色')
    } else {
      ElMessage.warning('未返回润色结果')
    }
  } catch (e) {
    logger.error('作文润色失败', e)
  } finally {
    polishLoading.value = false
  }
}

/* ---------- 学习路径 ---------- */
const pathGoal = ref('')
const pathLoading = ref(false)
const pathResult = ref(null)
const pathRaw = ref('')

async function runPath() {
  if (!pathGoal.value.trim()) return ElMessage.warning('请输入学习目标')
  if (!courseId.value) return ElMessage.warning('请先选择课程')
  pathLoading.value = true
  try {
    const r = await aiApi.generateLearningPath({
      courseId: courseId.value,
      goal: pathGoal.value.trim(),
    })
    const body = unwrap(r)
    const data = unwrap(body) || {}
    pathResult.value = { status: data.status, message: data.message || data.summary }
    pathRaw.value = JSON.stringify(data, null, 2)
    ElMessage.success('学习路径已生成')
  } catch (e) {
    logger.error('学习路径生成失败', e)
  } finally {
    pathLoading.value = false
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

.ai-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--ac-spacing-md);
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

.stat-grid--sm {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}
.mini-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 14px 8px;
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
}
.mini-num {
  font-size: 18px;
  font-weight: 660;
  color: var(--ac-text-primary);
}
.mini-label {
  font-size: 11.5px;
  color: var(--ac-text-muted);
}

.ai-summary {
  margin: 0 0 10px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  font-size: 13.5px;
}
.advice-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.advice-item {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
}
.ai-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.ai-title {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 14px;
}
.ai-detail {
  margin: 0;
  font-size: 13px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
}

/* 智能答疑 */
.qa-panel {
  display: flex;
  flex-direction: column;
  height: 560px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  background: var(--ac-bg-elevated);
  overflow: hidden;
}
.qa-messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: var(--ac-surface-2);
}
.qa-empty {
  margin: auto;
  text-align: center;
  color: var(--ac-text-muted);
}
.qa-empty p {
  margin-top: 8px;
  font-size: 13px;
}
.qa-msg {
  display: flex;
}
.qa-msg.user {
  justify-content: flex-end;
}
.qa-msg.ai {
  justify-content: flex-start;
}
.qa-bubble {
  max-width: 76%;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 13.5px;
  line-height: 1.7;
  white-space: pre-wrap;
}
.qa-msg.user .qa-bubble {
  background: var(--ac-primary);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.qa-msg.ai .qa-bubble {
  background: var(--ac-bg-elevated);
  color: var(--ac-text-primary);
  border: 1px solid var(--ac-border);
  border-bottom-left-radius: 4px;
}
.qa-input {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 12px;
  border-top: 1px solid var(--ac-border);
  background: var(--ac-bg-elevated);
}
.qa-input .el-input {
  flex: 1;
}
.qa-session {
  padding: 0 12px 10px;
  font-size: 12px;
  color: var(--ac-text-muted);
  background: var(--ac-bg-elevated);
}

/* 作文 */
.essay-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ac-spacing-md);
}
.essay-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
  flex-wrap: wrap;
}

/* 学习路径 */
.path-form {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.path-result {
  padding-top: 8px;
}
.path-json {
  background: var(--ac-surface-2);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 12px;
  font-size: 12px;
  color: var(--ac-text-secondary);
  overflow-x: auto;
  margin-top: 10px;
}

@media (max-width: 1100px) {
  .stat-grid--sm,
  .essay-grid {
    grid-template-columns: 1fr;
  }
  .page-head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
