<template>
  <div class="fade-in teaching-supervision">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">教学运行监管</h2>
        <p class="page-desc">开课运行、学习行为与异常监控的整体视图</p>
      </div>
      <el-button :loading="loading" @click="loadAll">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
    </div>

    <!-- 核心指标 -->
    <div class="stats-row">
      <div class="stat-mini-card">
        <span class="label">课程总数</span>
        <span class="value">{{ overview.totalCourses ?? '—' }}</span>
      </div>
      <div class="stat-mini-card teacher">
        <span class="label">活跃课程</span>
        <span class="value">{{ overview.activeCourses ?? '—' }}</span>
      </div>
      <div class="stat-mini-card student">
        <span class="label">在校学生</span>
        <span class="value">{{ overview.roleDistribution?.student ?? '—' }}</span>
      </div>
      <div class="stat-mini-card admin">
        <span class="label">授课教师</span>
        <span class="value">{{ overview.roleDistribution?.teacher ?? '—' }}</span>
      </div>
      <div class="stat-mini-card">
        <span class="label">选课总人次</span>
        <span class="value">{{ overview.totalEnrollments ?? '—' }}</span>
      </div>
    </div>

    <div class="grid-2">
      <!-- 开课统计：分类分布 + 热门课程 -->
      <div class="content-card">
        <div class="card-title">
          <el-icon><DataLine /></el-icon>开课统计
        </div>
        <div class="sub-label">课程分类分布</div>
        <div class="cat-list">
          <div v-for="(cnt, name) in categoryDistribution" :key="name" class="cat-row">
            <span class="cat-name">{{ name || '未分类' }}</span>
            <div class="cat-bar">
              <div class="cat-bar-fill" :style="{ width: barWidth(cnt) }"></div>
            </div>
            <span class="cat-cnt">{{ cnt }}</span>
          </div>
          <el-empty v-if="!categoryKeys.length" description="暂无课程分类数据" :image-size="60" />
        </div>
        <div class="sub-label" style="margin-top: 18px">热门课程 Top5</div>
        <div class="top-list">
          <div v-for="(c, i) in topCourses" :key="i" class="top-row">
            <span class="rank">{{ i + 1 }}</span>
            <span class="top-name">{{
              c.courseName || c.name || '课程 #' + (c.courseId || c.id)
            }}</span>
            <span class="top-cnt">{{ c.enrollmentCount ?? c.count ?? '—' }} 人选课</span>
          </div>
          <el-empty v-if="!topCourses.length" description="暂无热门课程" :image-size="60" />
        </div>
      </div>

      <!-- 学习行为热力图 -->
      <div class="content-card">
        <div class="card-title">
          <el-icon><Sunny /></el-icon>学习行为热力图（近 30 天）
        </div>
        <p class="sub-label">按星期 × 时段统计平台学习活跃度（记录数）</p>
        <div class="heatmap">
          <div class="heat-col-head"></div>
          <div v-for="slot in timeSlots" :key="slot" class="heat-col-head">{{ slot }}</div>
          <template v-for="d in dayOrder" :key="d">
            <div class="heat-row-head">{{ dayLabel(d) }}</div>
            <div
              v-for="slot in [0, 1, 2, 3]"
              :key="slot"
              class="heat-cell"
              :style="cellStyle(d, slot)"
              :title="`${dayLabel(d)} ${timeSlots[slot]}：${heatValue(d, slot)} 条`"
            ></div>
          </template>
        </div>
        <div class="heat-legend">
          <span>低</span>
          <span v-for="n in 5" :key="n" class="legend-cell" :style="legendStyle(n - 1)"></span>
          <span>高</span>
        </div>
      </div>
    </div>

    <!-- AI 异常监控 -->
    <div class="content-card" style="margin-top: var(--ac-spacing-lg)">
      <div class="card-title">
        <el-icon><WarnTriangleFilled /></el-icon>AI 异常监控
        <span class="card-meta"
          >近 50 条登录 / 操作日志 · 登录 {{ aiMonitor.loginCount ?? 0 }} · 操作
          {{ aiMonitor.operationCount ?? 0 }}</span
        >
      </div>
      <div v-if="aiAlerts.length" class="alert-list">
        <div v-for="(a, i) in aiAlerts" :key="i" class="alert-row" :class="'lv-' + a.level">
          <el-tag size="small" :type="alertType(a.level)" effect="dark">{{ a.level }}</el-tag>
          <div class="alert-body">
            <div class="alert-title">{{ a.title }}</div>
            <div v-if="a.detail" class="alert-detail">{{ a.detail }}</div>
          </div>
        </div>
      </div>
      <el-empty
        v-else
        :description="aiMonitor.error ? 'AI 分析暂不可用' : '未检测到异常'"
        :image-size="70"
      />
    </div>

    <!-- 学习预警（全校聚合 + 维度下钻） -->
    <div class="content-card" style="margin-top: var(--ac-spacing-lg)">
      <div class="card-title">
        <el-icon><Bell /></el-icon>学习预警（全校掉队聚合）
        <el-select v-model="riskDim" size="small" class="dim-sel" @change="loadRisk">
          <el-option label="全校" value="school" />
          <el-option label="按课程" value="course" />
          <el-option label="按院系" value="department" />
          <el-option label="按专业" value="major" />
          <el-option label="按班级" value="class" />
        </el-select>
      </div>
      <div class="warn-rules">
        <div class="rule-item">
          <el-icon><Warning /></el-icon>课程进度 &lt; 40% 标记为低进度预警
        </div>
        <div class="rule-item">
          <el-icon><Warning /></el-icon>课程成绩 &lt; 60 分标记为成绩预警
        </div>
      </div>

      <div v-if="riskTotal > 0" class="risk-summary">
        <div class="rs-item">
          <span class="rs-val">{{ riskTotal }}</span
          ><span class="rs-lbl">学生选课人次</span>
        </div>
        <div class="rs-item danger">
          <span class="rs-val">{{ riskCount }}</span
          ><span class="rs-lbl">掉队预警人次</span>
        </div>
        <div class="rs-item">
          <span class="rs-val">{{ riskRate }}%</span><span class="rs-lbl">预警占比</span>
        </div>
      </div>

      <div v-if="riskGroups.length" class="risk-groups">
        <div v-for="g in riskGroups" :key="g.key" class="rg-row">
          <span class="rg-name">{{ g.label }}</span>
          <div class="rg-bar">
            <div class="rg-fill" :style="{ width: Math.min(100, g.atRiskRate) + '%' }"></div>
          </div>
          <span class="rg-cnt">{{ g.atRisk }} / {{ g.total }}（{{ g.atRiskRate }}%）</span>
        </div>
      </div>

      <div v-loading="riskLoading" class="risk-table">
        <div class="rt-head">
          <span class="c-name">学生</span>
          <span class="c-course">课程</span>
          <span class="c-prog">进度</span>
          <span class="c-score">成绩</span>
          <span class="c-level">等级</span>
        </div>
        <div v-for="(d, i) in riskDetails" :key="i" class="rt-row">
          <span class="c-name">{{ d.studentName }}</span>
          <span class="c-course">{{ d.courseName || '—' }}</span>
          <span class="c-prog">{{ d.progress }}%</span>
          <span class="c-score">{{ d.finalScore ?? '—' }}</span>
          <span class="c-level">
            <el-tag size="small" :type="d.level === 'HIGH' ? 'danger' : 'warning'" effect="dark">{{
              d.level === 'HIGH' ? '高危' : '中危'
            }}</el-tag>
          </span>
        </div>
        <el-empty
          v-if="!riskDetails.length"
          description="当前维度下无掉队预警学生"
          :image-size="60"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Refresh,
  DataLine,
  Sunny,
  WarnTriangleFilled,
  Bell,
  Warning,
} from '@element-plus/icons-vue'
import adminApi from '@/api/admin'
import analyticsApi from '@/api/analytics'

const loading = ref(false)
const overview = reactive({})
const courseStats = reactive({})
const heatCells = ref([])
const aiMonitor = reactive({})

const timeSlots = ['0–6时', '6–12时', '12–18时', '18–24时']
const dayOrder = [1, 2, 3, 4, 5, 6, 0] // MON..SAT, SUN
const dayNames = { 0: '周日', 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六' }

const categoryDistribution = computed(() => courseStats.categoryDistribution || {})
const categoryKeys = computed(() => Object.keys(categoryDistribution.value))
const categoryMax = computed(() =>
  Math.max(1, ...categoryKeys.value.map((k) => categoryDistribution.value[k])),
)
const topCourses = computed(() => courseStats.topCourses || [])
const aiAlerts = computed(() => aiMonitor.alerts || [])

// 学习预警（全校掉队聚合）
const riskDim = ref('school')
const riskLoading = ref(false)
const riskTotal = ref(0)
const riskCount = ref(0)
const riskRate = ref(0)
const riskGroups = ref([])
const riskDetails = ref([])

async function loadRisk() {
  riskLoading.value = true
  try {
    const res = await analyticsApi
      .getAtRiskStudents({ groupBy: riskDim.value, size: 200 })
      .catch(() => ({ data: {} }))
    const d = res?.data || {}
    riskTotal.value = d.totalStudents || 0
    riskCount.value = d.atRiskCount || 0
    riskRate.value = d.atRiskRate || 0
    riskGroups.value = Array.isArray(d.groups) ? d.groups : []
    riskDetails.value = Array.isArray(d.details) ? d.details : []
  } catch (e) {
    logger.error('加载掉队预警失败:', e)
    ElMessage.error('加载掉队预警失败')
  } finally {
    riskLoading.value = false
  }
}

function dayLabel(d) {
  return dayNames[d]
}
function barWidth(cnt) {
  return Math.round((cnt / categoryMax.value) * 100) + '%'
}

function heatValue(d, slot) {
  const cell = heatCells.value.find((c) => c.dayOfWeek === d && c.hour === slot)
  return cell ? cell.value || cell.count || 0 : 0
}
function heatMax() {
  return Math.max(1, ...heatCells.value.map((c) => c.value || c.count || 0))
}
function cellStyle(d, slot) {
  const v = heatValue(d, slot)
  const ratio = v / heatMax()
  const lvl = v === 0 ? 0 : Math.min(4, Math.ceil(ratio * 4))
  const palette = [
    'rgba(127,127,127,0.08)',
    'rgba(59,130,246,0.30)',
    'rgba(59,130,246,0.50)',
    'rgba(59,130,246,0.72)',
    'rgba(37,99,235,0.95)',
  ]
  return { background: palette[lvl] }
}
function legendStyle(n) {
  const palette = [
    'rgba(127,127,127,0.08)',
    'rgba(59,130,246,0.30)',
    'rgba(59,130,246,0.50)',
    'rgba(59,130,246,0.72)',
    'rgba(37,99,235,0.95)',
  ]
  return { background: palette[n] }
}
function alertType(level) {
  return level === 'HIGH' ? 'danger' : level === 'MEDIUM' ? 'warning' : 'info'
}

async function loadAll() {
  loading.value = true
  try {
    const [ov, cs, hm, am] = await Promise.all([
      adminApi.getOverview().catch(() => ({ data: {} })),
      adminApi.getCourseStats().catch(() => ({ data: {} })),
      analyticsApi.getPlatformHeatmap({ days: 30 }).catch(() => ({ data: [] })),
      adminApi.getAIMonitor().catch(() => ({ data: { alerts: [] } })),
    ])
    Object.assign(overview, ov.data || {})
    Object.assign(courseStats, cs.data || {})
    heatCells.value = Array.isArray(hm.data) ? hm.data : []
    Object.assign(aiMonitor, am.data || { alerts: [] })
  } catch (e) {
    logger.error('加载监管数据失败:', e)
    ElMessage.error('加载监管数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadAll()
  loadRisk()
})
</script>

<style scoped>
.teaching-supervision {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: var(--ac-spacing-lg);
  gap: 16px;
  flex-wrap: wrap;
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  letter-spacing: -0.3px;
}
.page-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-top: 4px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}
.stat-mini-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: all var(--ac-transition-normal);
}
.stat-mini-card:hover {
  box-shadow: var(--ac-shadow-md);
  transform: translateY(-2px);
}
.stat-mini-card .label {
  font-size: 13px;
  color: var(--ac-text-muted);
  font-weight: 500;
}
.stat-mini-card .value {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
}
.stat-mini-card.admin .value {
  color: #7f1d1d;
}
.stat-mini-card.teacher .value {
  color: #166534;
}
.stat-mini-card.student .value {
  color: #1e40af;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
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
.card-meta {
  font-size: 12px;
  color: var(--ac-text-muted);
  font-weight: 400;
  margin-left: auto;
}
.sub-label {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-bottom: 10px;
}

.cat-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.cat-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.cat-name {
  width: 80px;
  color: var(--ac-text-secondary, #475569);
  flex-shrink: 0;
}
.cat-bar {
  flex: 1;
  height: 8px;
  background: rgba(127, 127, 127, 0.1);
  border-radius: 4px;
  overflow: hidden;
}
.cat-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #60a5fa, #2563eb);
  border-radius: 4px;
  transition: width 0.4s ease;
}
.cat-cnt {
  width: 36px;
  text-align: right;
  color: var(--ac-text-primary);
  font-weight: 600;
}

.top-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.top-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--ac-border);
}
.top-row:last-child {
  border-bottom: none;
}
.rank {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--ac-primary, #3b82f6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
.top-name {
  flex: 1;
  color: var(--ac-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.top-cnt {
  color: var(--ac-text-muted);
  flex-shrink: 0;
}

.heatmap {
  display: grid;
  grid-template-columns: 52px repeat(4, 1fr);
  gap: 6px;
  margin-top: 6px;
}
.heat-col-head {
  font-size: 11px;
  color: var(--ac-text-muted);
  text-align: center;
  padding-bottom: 2px;
}
.heat-row-head {
  font-size: 12px;
  color: var(--ac-text-secondary, #475569);
  display: flex;
  align-items: center;
}
.heat-cell {
  height: 38px;
  border-radius: 6px;
  transition: transform 0.15s ease;
}
.heat-cell:hover {
  transform: scale(1.06);
}
.heat-legend {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 12px;
  font-size: 11px;
  color: var(--ac-text-muted);
}
.legend-cell {
  width: 20px;
  height: 12px;
  border-radius: 3px;
}

.alert-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.alert-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--ac-border);
  background: var(--ac-bg-base, #fff);
}
.alert-row.lv-HIGH {
  border-color: rgba(176, 80, 69, 0.4);
  background: rgba(176, 80, 69, 0.06);
}
.alert-row.lv-MEDIUM {
  border-color: rgba(180, 140, 20, 0.35);
  background: rgba(180, 140, 20, 0.06);
}
.alert-body {
  flex: 1;
}
.alert-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.alert-detail {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 3px;
  line-height: 1.5;
}

.warn-rules {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.rule-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ac-text-secondary, #475569);
}
.rule-item .el-icon {
  color: #b8841e;
}
.dim-sel {
  margin-left: auto;
  width: 130px;
}

.risk-summary {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.rs-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 18px;
  border-radius: 10px;
  background: rgba(127, 127, 127, 0.06);
  border: 1px solid var(--ac-border);
}
.rs-item.danger {
  background: rgba(176, 80, 69, 0.08);
  border-color: rgba(176, 80, 69, 0.3);
}
.rs-val {
  font-size: 24px;
  font-weight: 700;
  color: var(--ac-text-primary);
}
.rs-item.danger .rs-val {
  color: #b05045;
}
.rs-lbl {
  font-size: 12px;
  color: var(--ac-text-muted);
}

.risk-groups {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}
.rg-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.rg-name {
  width: 110px;
  color: var(--ac-text-secondary, #475569);
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rg-bar {
  flex: 1;
  height: 8px;
  background: rgba(127, 127, 127, 0.1);
  border-radius: 4px;
  overflow: hidden;
}
.rg-fill {
  height: 100%;
  background: linear-gradient(90deg, #f59e0b, #b05045);
  border-radius: 4px;
  transition: width 0.4s ease;
}
.rg-cnt {
  width: 150px;
  text-align: right;
  color: var(--ac-text-primary);
  flex-shrink: 0;
}

.risk-table {
  border: 1px solid var(--ac-border);
  border-radius: 8px;
  overflow: hidden;
}
.rt-head,
.rt-row {
  display: grid;
  grid-template-columns: 1.2fr 1.4fr 0.8fr 0.8fr 0.8fr;
  gap: 10px;
  padding: 10px 12px;
  font-size: 13px;
  align-items: center;
}
.rt-head {
  background: var(--ac-bg-base, #fff);
  color: var(--ac-text-muted);
  font-weight: 600;
  border-bottom: 1px solid var(--ac-border);
}
.rt-row {
  border-bottom: 1px solid var(--ac-border);
}
.rt-row:last-child {
  border-bottom: none;
}
.c-name {
  color: var(--ac-text-primary);
  font-weight: 500;
}
.c-course,
.c-prog,
.c-score {
  color: var(--ac-text-secondary, #475569);
}
.pending-box {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  background: rgba(127, 127, 127, 0.06);
  border: 1px dashed var(--ac-border);
  border-radius: 8px;
  padding: 14px 16px;
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

@media (max-width: 960px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
