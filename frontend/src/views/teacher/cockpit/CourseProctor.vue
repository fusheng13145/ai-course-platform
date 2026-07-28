<template>
  <div class="course-proctor fade-in">
    <div v-loading="loading">
      <!-- 指标卡 -->
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-icon" style="background: var(--ac-primary-bg); color: var(--ac-primary)">
            <el-icon :size="22"><VideoCamera /></el-icon>
          </div>
          <div class="stat-info">
            <h3>{{ sessions.length || 0 }}</h3>
            <p>进行中考试</p>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="background: var(--ac-warning-bg); color: var(--ac-warning)">
            <el-icon :size="22"><Warning /></el-icon>
          </div>
          <div class="stat-info">
            <h3>{{ riskTotal || 0 }}</h3>
            <p>风险事件</p>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="background: var(--ac-success-bg); color: var(--ac-success)">
            <el-icon :size="22"><Select /></el-icon>
          </div>
          <div class="stat-info">
            <h3>{{ eventsTotal || 0 }}</h3>
            <p>监考事件</p>
          </div>
        </div>
      </div>

      <!-- 进行中考试 -->
      <div class="chart-card">
        <div class="chart-title">
          进行中考试会话
          <el-tag size="small" type="info" effect="light" round>{{
            filteredSessions.length
          }}</el-tag>
          <span class="spacer" />
          <el-switch v-model="onlyThisCourse" active-text="仅本课程" :disabled="!courseId" />
        </div>

        <el-alert
          v-if="!courseId"
          type="info"
          :closable="false"
          show-icon
          title="未关联到具体课程，已列出全部进行中的考试会话"
          style="margin-bottom: var(--ac-spacing-md)"
        />

        <el-table
          :data="filteredSessions"
          size="small"
          empty-text="暂无进行中的考试"
          highlight-current-row
          :row-class-name="(row) => (row.row.id === selected?.id ? 'row-active' : '')"
          @row-click="onSelectSession"
        >
          <el-table-column label="试卷" min-width="180">
            <template #default="{ row }">{{ paperTitle(row) }}</template>
          </el-table-column>
          <el-table-column label="学生" min-width="120">
            <template #default="{ row }">{{
              row.studentName || row.student || '（未指定）'
            }}</template>
          </el-table-column>
          <el-table-column label="开始时间" min-width="170">
            <template #default="{ row }">{{ fmt(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="结束时间" min-width="170">
            <template #default="{ row }">{{ fmt(row.endTime) || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="sessionTag(row.status)" effect="light">{{
                sessionText(row.status)
              }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 风险汇总 -->
      <div v-loading="risk.loading" class="chart-card">
        <div class="chart-title">
          风险汇总
          <el-tag size="small" type="warning" effect="light" round>{{ risk.list.length }}</el-tag>
          <span v-if="selected" class="hint"> · 当前会话：{{ paperTitle(selected) }}</span>
          <span v-else class="hint"> · 请选择上方一个考试会话</span>
        </div>

        <el-table
          v-if="selected"
          :data="risk.list"
          size="small"
          empty-text="该会话暂无风险记录"
          highlight-current-row
          :row-class-name="(row) => (row.row === selectedRisk ? 'row-active' : '')"
          @row-click="onSelectRisk"
        >
          <el-table-column label="学生" min-width="120">
            <template #default="{ row }">{{
              pick(row, ['studentName', 'student', 'userName']) || '—'
            }}</template>
          </el-table-column>
          <el-table-column label="风险类型" min-width="140">
            <template #default="{ row }">
              <el-tag size="small" type="danger" effect="light">{{
                pick(row, ['riskType', 'type', 'riskTypeName']) || '—'
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="240">
            <template #default="{ row }">{{
              pick(row, ['description', 'detail', 'reason', 'message']) || '—'
            }}</template>
          </el-table-column>
          <el-table-column label="时间" min-width="170">
            <template #default="{ row }">{{
              fmt(pick(row, ['time', 'createTime', 'happenTime']))
            }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="请选择考试会话以查看风险" :image-size="64" />
      </div>

      <!-- 监考事件 -->
      <div v-loading="events.loading" class="chart-card">
        <div class="chart-title">
          监考事件
          <el-tag size="small" type="info" effect="light" round>{{ events.list.length }}</el-tag>
          <span v-if="selectedRisk" class="hint">
            · 风险：{{ pick(selectedRisk, ['riskType', 'type']) || '详情' }}</span
          >
          <span v-else class="hint"> · 请在上方风险列表中选择一条</span>
        </div>

        <el-table
          v-if="selectedRisk"
          :data="events.list"
          size="small"
          empty-text="该风险暂无监考事件"
          max-height="360"
        >
          <el-table-column label="事件类型" min-width="150">
            <template #default="{ row }">
              <el-tag size="small" :type="eventTag(row.eventType || row.type)" effect="light">{{
                row.eventType || row.type || '—'
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="260">
            <template #default="{ row }">{{
              pick(row, ['description', 'detail', 'message', 'content']) || '—'
            }}</template>
          </el-table-column>
          <el-table-column label="时间" min-width="170">
            <template #default="{ row }">{{
              fmt(pick(row, ['time', 'createTime', 'happenTime', 'eventTime']))
            }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="请选择风险记录以查看监考事件" :image-size="64" />
      </div>
    </div>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import examApi from '@/api/exam'
import proctorApi from '@/api/proctor'
import { VideoCamera, Warning, Select } from '@element-plus/icons-vue'

const route = useRoute()
const courseId = ref(route.params.id)

const loading = ref(false)
const sessions = ref([])
const paperMap = ref({}) // examPaperId -> title
const onlyThisCourse = ref(false)

const selected = ref(null)
const selectedRisk = ref(null)

const risk = reactive({ loading: false, list: [] })
const events = reactive({ loading: false, list: [] })

const riskTotal = ref(0)
const eventsTotal = ref(0)

const filteredSessions = computed(() => {
  if (!onlyThisCourse.value || !courseId.value) return sessions.value
  return sessions.value.filter((s) => String(s.courseId) === String(courseId.value))
})

function pick(obj, keys) {
  if (!obj) return ''
  for (const k of keys) if (obj[k] !== undefined && obj[k] !== null && obj[k] !== '') return obj[k]
  return ''
}
function fmt(v) {
  if (!v) return ''
  const d = new Date(v)
  if (isNaN(d.getTime())) return v
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
function sessionText(s) {
  const m = {
    IN_PROGRESS: '进行中',
    ONGOING: '进行中',
    ENDED: '已结束',
    FINISHED: '已结束',
    PENDING: '待开始',
  }
  return m[s] || s || '未知'
}
function sessionTag(s) {
  return s === 'IN_PROGRESS' || s === 'ONGOING'
    ? 'warning'
    : s === 'ENDED' || s === 'FINISHED'
      ? 'info'
      : 'primary'
}
function eventTag(t) {
  return ['CHEAT', 'COPY', 'LEAVE', 'TAB_SWITCH', 'FACE_MISS'].includes(t)
    ? 'danger'
    : ['WARN', 'WARNING', 'SUSPICIOUS'].includes(t)
      ? 'warning'
      : 'info'
}
function paperTitle(row) {
  return paperMap.value[row.examPaperId] || `试卷#${row.examPaperId}`
}

async function load() {
  loading.value = true
  try {
    // 进行中考试会话
    const r = await examApi.getActiveExams()
    const body = unwrap(r)
    const arr = Array.isArray(body) ? body : body?.records || body?.list || []
    sessions.value = arr

    // 关联试卷标题（getActiveExams 仅含 examPaperId）
    const ids = [...new Set(arr.map((s) => s.examPaperId).filter(Boolean))]
    await Promise.all(
      ids.map(async (id) => {
        try {
          const pr = await examApi.getExamPaperById(id)
          const p = pr?.data ?? pr ?? {}
          paperMap.value[id] = p.title || `试卷#${id}`
        } catch (e) {
          logger.error('加载试卷信息失败', id, e)
        }
      }),
    )

    // 若当前已选中会话被刷新替换，保持选中
    if (selected.value) {
      const still = arr.find((s) => s.id === selected.value.id)
      if (still) selected.value = still
    }
  } catch (e) {
    logger.error('加载进行中考试失败', e)
  } finally {
    loading.value = false
  }
}

async function onSelectSession(row) {
  selected.value = row
  selectedRisk.value = null
  events.list = []
  eventsTotal.value = 0
  await loadRisk(row)
}

async function loadRisk(session) {
  risk.loading = true
  risk.list = []
  riskTotal.value = 0
  try {
    const r = await proctorApi.getExamRisk(session.id)
    const body = unwrap(r)
    const arr = Array.isArray(body) ? body : body?.records || body?.list || []
    risk.list = arr
    riskTotal.value = arr.length
  } catch (e) {
    logger.error('加载风险汇总失败', e)
  } finally {
    risk.loading = false
  }
}

async function onSelectRisk(row) {
  selectedRisk.value = row
  await loadEvents(row)
}

// 从风险项解析监考会话 id：优先取风险项字段，否则通过 getStudentSession 获取
async function resolveProctorSessionId(risk) {
  const direct = pick(risk, ['proctorSessionId', 'sessionId', 'proctorId', 'proctorSession'])
  if (direct) return direct
  const studentId = pick(risk, ['studentId', 'studentUserId', 'userId'])
  if (studentId && selected.value) {
    try {
      const r = await proctorApi.getStudentSession(selected.value.id, studentId)
      const body = r?.data ?? r ?? {}
      return body.id || body.proctorSessionId || body.sessionId || null
    } catch (e) {
      logger.error('获取学生监考会话失败', e)
    }
  }
  return null
}

async function loadEvents(risk) {
  events.loading = true
  events.list = []
  eventsTotal.value = 0
  try {
    const pid = await resolveProctorSessionId(risk)
    if (!pid) {
      logger.warn('无法解析监考会话 id，尝试使用当前考试会话 id')
    }
    const targetId = pid || selected.value?.id
    if (!targetId) return
    const r = await proctorApi.getSessionEvents(targetId)
    const body = unwrap(r)
    const arr = Array.isArray(body) ? body : body?.records || body?.list || []
    events.list = arr
    eventsTotal.value = arr.length
  } catch (e) {
    logger.error('加载监考事件失败', e)
  } finally {
    events.loading = false
  }
}

onMounted(load)
watch(
  () => route.params.id,
  (id) => {
    courseId.value = id
  },
)
</script>

<style scoped>
.fade-in {
  animation: acFadeIn 0.3s ease;
}
@keyframes acFadeIn {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
}
.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-info h3 {
  margin: 0;
  font-size: 24px;
  font-weight: 680;
  color: var(--ac-text-primary);
}
.stat-info p {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}

.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
  margin-bottom: var(--ac-spacing-md);
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
.chart-title .spacer {
  flex: 1;
}
.chart-title .hint {
  font-size: 12.5px;
  font-weight: 400;
  color: var(--ac-text-muted);
}

:deep(.row-active) {
  background: var(--ac-primary-bg) !important;
  cursor: pointer;
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
