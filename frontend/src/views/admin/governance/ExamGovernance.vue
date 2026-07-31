<template>
  <div class="fade-in exam-governance">
    <PageHeader title="考试防作弊治理" subtitle="监考会话风险看板、违规事件核查与复核工作流" icon="shield" icon-bg="accent">
      <el-button :loading="loading" @click="loadSessions">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard label="进行中监考会话" :value="sessions.length" icon="video" tone="var(--accent-soft)" tone-color="var(--accent)" />
      <StatCard label="高风险会话" :value="highRiskCount" icon="warn" tone="var(--warn-soft)" tone-color="var(--warn)" />
      <StatCard label="已触发标记(FLAGGED)" :value="flaggedCount" icon="bell" tone="var(--info-soft)" tone-color="var(--info)" />
      <StatCard label="累计违规事件" :value="totalEvents" icon="megaphone" tone="var(--warn-soft)" tone-color="var(--warn)" />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background:var(--accent-soft);color:var(--accent)">
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/></svg>
        </span>
        <div><div class="l1-title">监考治理看板</div><div class="l1-sub">风险会话、违规事件与阈值规则</div></div>
      </div>
      <div class="l1-body">
    <div class="grid-main">
      <!-- 监考会话列表 -->
      <div class="content-card session-list-card">
        <div class="card-title">
          <el-icon><VideoCamera /></el-icon>监考会话
        </div>
        <div class="session-list">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{ active: selectedId === s.id }"
            @click="selectSession(s)"
          >
            <div class="session-main">
              <span class="session-id">试卷 #{{ s.examPaperId }}</span>
              <span class="session-stu">学生 #{{ s.studentId }}</span>
            </div>
            <div class="session-sub">
              <span class="session-time">{{ formatTime(s.startTime, '—') }}</span>
              <el-tag v-if="s.riskLevel" size="small" :type="riskTag(s.riskLevel)" effect="light">
                {{ levelText(s.riskLevel) }} · {{ s.riskScore }}
              </el-tag>
            </div>
          </div>
          <el-empty
            v-if="!sessions.length"
            :description="loading ? '加载中…' : '当前无进行中的监考会话'"
            :image-size="70"
          />
        </div>
      </div>

      <!-- 风险详情 + 事件 -->
      <div class="content-card">
        <div class="card-title">
          <el-icon><DataAnalysis /></el-icon>会话风险详情
        </div>
        <div v-if="current" class="detail">
          <div class="detail-row">
            <span class="d-label">监考会话</span
            ><span class="d-val"
              >试卷 #{{ current.examPaperId }} / 学生 #{{ current.studentId }}</span
            >
          </div>
          <div class="detail-row">
            <span class="d-label">风险评分</span>
            <span class="d-val"
              ><b :class="'score-' + (current.riskLevel || 'LOW')">{{
                current.riskScore ?? 0
              }}</b></span
            >
          </div>
          <div class="detail-row">
            <span class="d-label">风险等级</span>
            <span class="d-val">
              <el-tag size="small" :type="riskTag(current.riskLevel)" effect="dark">{{
                levelText(current.riskLevel)
              }}</el-tag>
              <el-tag
                v-if="current.status === 'FLAGGED'"
                size="small"
                type="danger"
                effect="plain"
                style="margin-left: 6px"
                >已标记</el-tag
              >
            </span>
          </div>
          <div class="detail-row">
            <span class="d-label">事件分布</span>
            <span class="d-val type-chips">
              <span v-for="(c, t) in current.typeCounts" :key="t" class="type-chip"
                >{{ typeText(t) }} ×{{ c }}</span
              >
              <span
                v-if="!current.typeCounts || !Object.keys(current.typeCounts).length"
                class="muted"
                >无</span
              >
            </span>
          </div>
          <el-button class="view-events" type="primary" plain @click="openEvents(current)">
            <el-icon><Document /></el-icon>查看违规事件（{{ current.eventCount ?? 0 }}）
          </el-button>
        </div>
        <el-empty v-else description="请选择左侧监考会话查看风险详情" :image-size="70" />
      </div>
    </div>

    <!-- 阈值规则（管理员可调控） -->
    <div class="content-card" style="margin-top: var(--ac-spacing-lg)">
      <div class="card-title">
        <el-icon><Operation /></el-icon>风险判定规则（管理员可调控）
        <el-button
          v-if="isAdmin"
          size="small"
          type="primary"
          plain
          class="save-thr"
          :loading="thrSaving"
          @click="saveThresholds"
        >
          <el-icon><Check /></el-icon>保存阈值
        </el-button>
      </div>
      <div class="rules">
        <div class="rule">
          <b>评分</b> = Σ 事件严重度（LOW ×{{ thresholds.weightLow }} / MEDIUM ×{{
            thresholds.weightMedium
          }}
          / HIGH ×{{ thresholds.weightHigh }}）
        </div>
        <div class="rule">
          <b>等级</b>：评分 ≥ {{ thresholds.high }} → HIGH；≥ {{ thresholds.medium }} → MEDIUM；其余
          LOW
        </div>
        <div class="rule"><b>自动处置</b>：达到 HIGH 时监考会话状态自动置为 FLAGGED</div>
      </div>
      <div v-if="isAdmin" class="thr-form">
        <div class="thr-item">
          <label>高风险阈值 (HIGH)</label>
          <el-input-number v-model="thresholds.high" :min="1" :max="50" controls-position="right" />
        </div>
        <div class="thr-item">
          <label>中风险阈值 (MEDIUM)</label>
          <el-input-number
            v-model="thresholds.medium"
            :min="1"
            :max="50"
            controls-position="right"
          />
        </div>
        <div class="thr-item">
          <label>LOW 事件权重</label>
          <el-input-number
            v-model="thresholds.weightLow"
            :min="0"
            :max="10"
            controls-position="right"
          />
        </div>
        <div class="thr-item">
          <label>MEDIUM 事件权重</label>
          <el-input-number
            v-model="thresholds.weightMedium"
            :min="0"
            :max="10"
            controls-position="right"
          />
        </div>
        <div class="thr-item">
          <label>HIGH 事件权重</label>
          <el-input-number
            v-model="thresholds.weightHigh"
            :min="0"
            :max="10"
            controls-position="right"
          />
        </div>
      </div>
      <el-alert
        v-else
        type="info"
        :closable="false"
        title="仅管理员可修改阈值，当前为只读视图"
        class="thr-readonly"
      />
    </div>
      </div>
    </section>

    <!-- 事件核查抽屉 -->
    <el-drawer v-model="drawer" title="违规事件核查" size="460px" append-to-body>
      <div v-if="eventsLoading" v-loading="true" style="height: 200px"></div>
      <template v-else>
        <div class="event-table">
          <div v-for="(e, i) in events" :key="i" class="event-row">
            <div class="event-head">
              <el-tag size="small" :type="riskTag(e.severity)" effect="light">{{
                typeText(e.type)
              }}</el-tag>
              <el-tag size="small" :type="riskTag(e.severity)" effect="plain">{{
                e.severity
              }}</el-tag>
              <el-tag v-if="e.status" size="small" :type="statusTag(e.status)" effect="dark">{{
                statusText(e.status)
              }}</el-tag>
              <span class="event-time">{{ formatTime(e.capturedAt, '—') }}</span>
            </div>
            <div v-if="e.detail" class="event-detail">{{ e.detail }}</div>
            <div v-if="e.reviewNote" class="event-review-note">
              <b>复核结论：</b>{{ e.reviewNote }}
            </div>
            <div v-if="canReview(e)" class="event-actions">
              <el-button
                size="small"
                type="success"
                plain
                :loading="reviewingId === e.id && reviewAction === 'REVIEWED'"
                @click="submitReview(e, 'REVIEWED')"
                >确认违规</el-button
              >
              <el-button
                size="small"
                type="info"
                plain
                :loading="reviewingId === e.id && reviewAction === 'EXEMPTED'"
                @click="submitReview(e, 'EXEMPTED')"
                >标记豁免</el-button
              >
              <el-link
                v-if="e.evidenceUrl"
                type="primary"
                :href="e.evidenceUrl"
                target="_blank"
                :underline="false"
                >查看证据</el-link
              >
            </div>
          </div>
          <el-empty v-if="!events.length" description="该会话暂无违规事件" :image-size="60" />
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { formatTime } from '@/utils/datetime'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Refresh,
  VideoCamera,
  DataAnalysis,
  Document,
  Operation,
  Check,
} from '@element-plus/icons-vue'
import examApi from '@/api/exam'
import proctorApi from '@/api/proctor'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

const loading = ref(false)
const sessions = ref([])
const selectedId = ref(null)
const drawer = ref(false)
const events = ref([])
const eventsLoading = ref(false)

// 角色与阈值
const authStore = useAuthStore()
const isAdmin = computed(() => authStore.userRole === 'ADMIN')
const thresholds = reactive({ high: 6, medium: 3, weightLow: 1, weightMedium: 2, weightHigh: 3 })
const thrSaving = ref(false)

const current = computed(() => sessions.value.find((s) => s.id === selectedId.value) || null)

// 复核状态展示
const STATUS_TEXT = { PENDING: '待复核', REVIEWED: '已确认违规', EXEMPTED: '已豁免' }
const statusText = (s) => STATUS_TEXT[s] || s || '—'
const statusTag = (s) => (s === 'REVIEWED' ? 'danger' : s === 'EXEMPTED' ? 'info' : 'warning')
const canReview = (e) => !e.status || e.status === 'PENDING'
const highRiskCount = computed(() => sessions.value.filter((s) => s.riskLevel === 'HIGH').length)
const flaggedCount = computed(() => sessions.value.filter((s) => s.status === 'FLAGGED').length)
const totalEvents = computed(() => sessions.value.reduce((sum, s) => sum + (s.eventCount || 0), 0))

const TYPE_TEXT = {
  TAB_SWITCH: '切屏',
  FACE_MISSING: '人脸消失',
  MULTIPLE_FACE: '多脸检测',
  MOBILE_DETECTED: '手机检测',
  COPY_PASTE: '复制粘贴',
  FULLSCREEN_EXIT: '退出全屏',
  UNKNOWN: '未知',
}
const typeText = (t) => TYPE_TEXT[t] || t
const levelText = (l) => ({ LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' })[l] || l || '—'
const riskTag = (l) => (l === 'HIGH' ? 'danger' : l === 'MEDIUM' ? 'warning' : 'info')

async function loadSessions() {
  loading.value = true
  try {
    const res = await examApi.getActiveExams()
    const list = Array.isArray(res.data) ? res.data : []
    // 并行拉取每个会话的风险汇总，构建治理看板
    const enriched = await Promise.all(
      list.map(async (s) => {
        try {
          const r = await proctorApi.getExamRisk(s.id)
          const rows = Array.isArray(r?.data) ? r.data : []
          const risk = rows[0] || {}
          return {
            ...s,
            riskScore: risk.riskScore ?? 0,
            riskLevel: risk.riskLevel || 'LOW',
            status: risk.status || s.status,
            eventCount: risk.eventCount ?? 0,
            typeCounts: risk.typeCounts || {},
          }
        } catch {
          return { ...s, riskScore: 0, riskLevel: 'LOW', eventCount: 0, typeCounts: {} }
        }
      }),
    )
    sessions.value = enriched
    if (!selectedId.value && enriched.length) selectedId.value = enriched[0].id
  } catch (e) {
    logger.error('加载监考会话失败:', e)
    ElMessage.error('加载监考会话失败')
    sessions.value = []
  } finally {
    loading.value = false
  }
}

function selectSession(s) {
  selectedId.value = s.id
}

async function openEvents(s) {
  drawer.value = true
  eventsLoading.value = true
  events.value = []
  try {
    // 取该学生对应的 proctor session id，再拉事件
    const stu = await proctorApi.getStudentSession(s.id, s.studentId).catch(() => null)
    const proctorSessionId = stu?.data?.id ?? stu?.id
    if (proctorSessionId) {
      const ev = await proctorApi.getSessionEvents(proctorSessionId).catch(() => ({ data: [] }))
      events.value = Array.isArray(ev?.data) ? ev.data : []
    } else {
      events.value = []
    }
  } catch (e) {
    logger.error(e)
    events.value = []
  } finally {
    eventsLoading.value = false
  }
}

async function loadThresholds() {
  try {
    const res = await proctorApi.getThresholds().catch(() => null)
    const t = res?.data
    if (t && typeof t === 'object') {
      thresholds.high = Number(t.high ?? 6)
      thresholds.medium = Number(t.medium ?? 3)
      thresholds.weightLow = Number(t.weightLow ?? 1)
      thresholds.weightMedium = Number(t.weightMedium ?? 2)
      thresholds.weightHigh = Number(t.weightHigh ?? 3)
    }
  } catch (e) {
    logger.error('加载阈值失败:', e)
  }
}

async function saveThresholds() {
  if (!isAdmin.value) return
  thrSaving.value = true
  try {
    await proctorApi.updateThresholds({
      high: thresholds.high,
      medium: thresholds.medium,
      weightLow: thresholds.weightLow,
      weightMedium: thresholds.weightMedium,
      weightHigh: thresholds.weightHigh,
    })
    ElMessage.success('风险判定阈值已保存')
  } catch (e) {
    logger.error(e)
    ElMessage.error('保存阈值失败：' + (e?.response?.data?.message || '权限不足或服务异常'))
  } finally {
    thrSaving.value = false
  }
}

const reviewingId = ref(null)
const reviewAction = ref('')

async function submitReview(e, action) {
  reviewingId.value = e.id
  reviewAction.value = action
  const note = action === 'REVIEWED' ? '教师确认该违规事件成立' : '教师判定为误报，予以豁免'
  try {
    const res = await proctorApi.reviewEvent(e.id, { status: action, reviewNote: note })
    const updated = res?.data
    if (updated) {
      Object.assign(e, updated)
    }
    ElMessage.success(action === 'REVIEWED' ? '已确认违规' : '已标记豁免')
  } catch (err) {
    logger.error(err)
    ElMessage.error('复核提交失败：' + (err?.response?.data?.message || '服务异常'))
  } finally {
    reviewingId.value = null
    reviewAction.value = ''
  }
}

onMounted(() => {
  loadSessions()
  loadThresholds()
})
</script>

<style scoped>
.exam-governance {
  max-width: 1400px;
  margin: 0 auto;
}

.grid-main {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: var(--ac-spacing-lg);
}
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 18px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 14px;
}
.card-title .el-icon {
  color: var(--ac-primary, #3b82f6);
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 560px;
  overflow-y: auto;
}
.session-item {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--ac-border);
  cursor: pointer;
  transition: all 0.15s ease;
  background: var(--ac-bg-base, #fff);
}
.session-item:hover {
  border-color: var(--ac-primary, #3b82f6);
}
.session-item.active {
  border-color: var(--ac-primary, #3b82f6);
  background: color-mix(in srgb, var(--ac-primary, #3b82f6) 8%, var(--ac-bg-elevated));
}
.session-main {
  display: flex;
  align-items: center;
  gap: 8px;
}
.session-id {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.session-stu {
  font-size: 12px;
  color: var(--ac-text-muted);
}
.session-sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}
.session-time {
  font-size: 12px;
  color: var(--ac-text-muted);
}

.detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}
.d-label {
  width: 84px;
  color: var(--ac-text-muted);
  flex-shrink: 0;
}
.d-val {
  color: var(--ac-text-primary);
}
.score-HIGH {
  color: #b05045;
}
.score-MEDIUM {
  color: #b8841e;
}
.score-LOW {
  color: #166534;
}
.type-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.type-chip {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 12px;
  background: rgba(127, 127, 127, 0.1);
  color: var(--ac-text-secondary, #475569);
}
.muted {
  color: var(--ac-text-muted);
}
.view-events {
  margin-top: 6px;
  align-self: flex-start;
}

.rules {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  color: var(--ac-text-secondary, #475569);
}
.rule b {
  color: var(--ac-text-primary);
}
.card-title .save-thr {
  margin-left: auto;
}
.thr-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px 18px;
  margin-top: 14px;
}
.thr-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.thr-item label {
  font-size: 12px;
  color: var(--ac-text-muted);
}
.thr-readonly {
  margin-top: 14px;
}
.event-review-note {
  font-size: 12px;
  color: var(--ac-text-secondary, #475569);
  margin-top: 8px;
  line-height: 1.5;
}
.event-review-note b {
  color: var(--ac-text-primary);
}

.pending-box {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  background: rgba(127, 127, 127, 0.06);
  border: 1px dashed var(--ac-border);
  border-radius: 8px;
  padding: 14px 16px;
  margin-top: 14px;
}
.pending-icon {
  font-size: 20px;
  color: var(--ac-text-muted);
  margin-top: 2px;
}
.pending-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 4px;
}
.pending-desc {
  font-size: 12px;
  color: var(--ac-text-muted);
  line-height: 1.6;
}
.pending-desc code {
  background: rgba(127, 127, 127, 0.12);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 11px;
}

.event-table {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.event-row {
  border: 1px solid var(--ac-border);
  border-radius: 8px;
  padding: 12px;
}
.event-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.event-time {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-left: auto;
}
.event-detail {
  font-size: 12px;
  color: var(--ac-text-secondary, #475569);
  margin-top: 8px;
  line-height: 1.5;
}
.event-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

@media (max-width: 960px) {
  .grid-main {
    grid-template-columns: 1fr;
  }
}
</style>
