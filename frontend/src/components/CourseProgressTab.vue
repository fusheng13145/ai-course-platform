<template>
  <div class="progress-tab">
    <!-- 总览统计卡 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon stat-icon--blue">
          <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
            <path d="M22 12A10 10 0 1 1 12 2" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <path d="M22 12A10 10 0 1 1 2 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.3"/>
            <path d="M12 6v6l4 2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overallProgress }}%</span>
          <span class="stat-lbl">总体进度</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--green">
          <Collection :size="20" />
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ completedChapters }}/{{ totalChapters }}</span>
          <span class="stat-lbl">已完成章节</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--orange">
          <Document :size="20" />
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ totalTasks }}</span>
          <span class="stat-lbl">任务点总数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--purple">
          <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
            <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            <rect x="8" y="2" width="8" height="4" rx="1" stroke="currentColor" stroke-width="2"/>
            <path d="M9 14l2 2 4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ avgSubmissionRate }}%</span>
          <span class="stat-lbl">平均提交率</span>
        </div>
      </div>
    </div>

    <!-- 总体进度环形图 -->
    <div class="progress-section">
      <div class="section-header">
        <h3 class="section-title">教学进度总览</h3>
      </div>
      <div class="progress-overview">
        <div class="ring-chart-wrapper">
          <div class="ring-chart">
            <svg viewBox="0 0 120 120" width="120" height="120">
              <circle cx="60" cy="60" r="52" fill="none" stroke="var(--ac-border, #e5e7eb)" stroke-width="10" />
              <circle
                cx="60" cy="60" r="52"
                fill="none"
                stroke="var(--ac-primary, #3b82f6)"
                stroke-width="10"
                stroke-linecap="round"
                :stroke-dasharray="circumference"
                :stroke-dashoffset="dashOffset"
                transform="rotate(-90 60 60)"
                style="transition: stroke-dashoffset 0.8s ease"
              />
            </svg>
            <div class="ring-center">
              <span class="ring-percent">{{ overallProgress }}%</span>
              <span class="ring-sublabel">已完成</span>
            </div>
          </div>
          <div class="ring-legend">
            <div class="legend-item">
              <span class="legend-dot legend-dot--done"></span>
              <span>已完成章节</span>
              <strong>{{ completedChapters }}</strong>
            </div>
            <div class="legend-item">
              <span class="legend-dot legend-dot--pending"></span>
              <span>未完成章节</span>
              <strong>{{ totalChapters - completedChapters }}</strong>
            </div>
            <div class="legend-item">
              <span class="legend-dot legend-dot--total"></span>
              <span>计划总章节</span>
              <strong>{{ totalChapters }}</strong>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 计划进度 vs 实际进度 -->
    <div class="compare-section">
      <div class="section-header">
        <h3 class="section-title">计划 vs 实际进度</h3>
      </div>
      <div class="compare-grid">
        <div class="compare-card">
          <div class="compare-card-header">
            <span class="compare-label">计划进度</span>
            <el-tag size="small" type="info" effect="plain">按教学计划</el-tag>
          </div>
          <el-progress
            :percentage="plannedProgress"
            :stroke-width="16"
            :color="'#94a3b8'"
            :show-text="false"
          />
          <div class="compare-detail">
            <span>计划完成 {{ plannedChapters }} / {{ totalChapters }} 章</span>
            <span class="compare-sub">预计还需 {{ plannedRemaining }} 章</span>
          </div>
        </div>
        <div class="compare-card">
          <div class="compare-card-header">
            <span class="compare-label">实际进度</span>
            <el-tag size="small" type="success" effect="plain">实际完成</el-tag>
          </div>
          <el-progress
            :percentage="overallProgress"
            :stroke-width="16"
            :show-text="false"
          />
          <div class="compare-detail">
            <span>实际完成 {{ completedChapters }} / {{ totalChapters }} 章</span>
            <span class="compare-sub" :class="{ 'compare-behind': behindCount > 0 }">
              {{ behindCount > 0 ? `落后计划 ${behindCount} 章` : '进度正常/超前' }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 章节进度明细 -->
    <div class="chapters-section">
      <div class="section-header">
        <h3 class="section-title">章节进度明细</h3>
      </div>
      <div v-loading="loading" class="content-card">
        <el-table :data="chapterList" style="width: 100%" row-key="id" default-expand-all>
          <el-table-column prop="title" label="章节名称" min-width="220">
            <template #default="{ row }">
              <div class="chapter-name-cell">
                <el-icon :size="16" :color="row.isPublished ? 'var(--ac-success)' : 'var(--ac-text-tertiary)'">
                  <component :is="row.isPublished ? 'CircleCheckFilled' : 'CircleClose'" />
                </el-icon>
                <span>{{ row.title }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.isPublished ? 'success' : 'info'" size="small">
                {{ row.isPublished ? '已发布' : '草稿' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="任务点数" width="90" align="center">
            <template #default="{ row }">{{ row.taskCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="提交数" width="90" align="center">
            <template #default="{ row }">{{ row.submittedCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="完成率" width="150">
            <template #default="{ row }">
              <div class="completion-cell">
                <el-progress
                  :percentage="Math.round((row.completionRate ?? 0) * 100)"
                  :stroke-width="6"
                  :show-text="false"
                  style="width: 90px"
                />
                <span class="completion-text">{{ Math.round((row.completionRate ?? 0) * 100) }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="时长(分)" width="80" align="center">
            <template #default="{ row }">{{ row.duration || 0 }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && chapterList.length === 0" description="暂无章节数据" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { Collection, Document } from '@element-plus/icons-vue'
import teacherApi from '@/api/teacher'
import logger from '@/utils/logger'

const props = defineProps({
  courseId: { type: [Number, String], required: true },
})

const loading = ref(false)
const chapterList = ref([])
const plannedChapters = ref(0)

// 环形图常量
const circumference = 2 * Math.PI * 52

const totalChapters = computed(() => chapterList.value.length)
const completedChapters = computed(
  () => chapterList.value.filter((c) => c.isPublished).length,
)
const totalTasks = computed(() =>
  chapterList.value.reduce((sum, c) => sum + (c.taskCount || 0), 0),
)
const overallProgress = computed(() => {
  if (totalChapters.value === 0) return 0
  return Math.round((completedChapters.value / totalChapters.value) * 100)
})
const dashOffset = computed(() => {
  const pct = overallProgress.value / 100
  return circumference * (1 - pct)
})

const avgSubmissionRate = computed(() => {
  const chapters = chapterList.value.filter((c) => c.taskCount > 0)
  if (chapters.length === 0) return 0
  const total = chapters.reduce(
    (s, c) => s + Math.round((c.completionRate ?? 0) * 100),
    0,
  )
  return Math.round(total / chapters.length)
})

// 计划进度（假设每章均匀分布；数据未加载时回退为全部章节）
const effectivePlanned = computed(() =>
  plannedChapters.value > 0 ? plannedChapters.value : totalChapters.value,
)
const plannedProgress = computed(() => {
  if (totalChapters.value === 0) return 0
  return Math.round((effectivePlanned.value / totalChapters.value) * 100)
})
const plannedRemaining = computed(() =>
  Math.max(0, effectivePlanned.value - completedChapters.value),
)
const behindCount = computed(() =>
  Math.max(0, effectivePlanned.value - completedChapters.value),
)

/** 扁平化章节树（模块内顶层函数，供 loadData 调用） */
function flattenTree(nodes, flat, level = 0) {
  nodes.forEach((node) => {
    flat.push({
      ...node,
      _level: level,
      title: level > 0 ? '  '.repeat(level) + '└ ' + (node.title || '') : node.title || '',
    })
    if (node.children && node.children.length > 0) {
      flattenTree(node.children, flat, level + 1)
    }
  })
}

async function loadData() {
  loading.value = true
  try {
    const res = await teacherApi.getChapterTree(props.courseId)
    const raw = res.data || res || []
    const list = Array.isArray(raw) ? raw : []

    // 扁平化章节树用于表格展示
    const flat = []
    flattenTree(list, flat)
    chapterList.value = flat
    plannedChapters.value = list.length
  } catch (e) {
    logger.warn('[CourseProgressTab] loadData 失败:', e.message || e)
    chapterList.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
watch(() => props.courseId, loadData)
</script>

<style scoped>
.progress-tab {
  padding: 4px 0;
}

/* 统计卡 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 22px;
}
.stat-card {
  background: var(--ac-bg);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-sm);
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: box-shadow 0.2s;
}
.stat-card:hover {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}
.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
}
.stat-icon--blue { background: linear-gradient(135deg, #3b82f6, #60a5fa); }
.stat-icon--green { background: linear-gradient(135deg, #10b981, #34d399); }
.stat-icon--orange { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.stat-icon--purple { background: linear-gradient(135deg, #8b5cf6, #a78bfa); }
.stat-info { flex: 1; min-width: 0; }
.stat-val { display: block; font-size: 20px; font-weight: 700; color: var(--ac-text-primary); line-height: 1.2; }
.stat-lbl { display: block; font-size: 12px; color: var(--ac-text-secondary); margin-top: 2px; }

/* 章节 */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}

/* 进度总览 - 环形图 */
.progress-section {
  margin-bottom: 22px;
}
.progress-overview {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 20px 24px;
}
.ring-chart-wrapper {
  display: flex;
  align-items: center;
  gap: 40px;
}
.ring-chart {
  position: relative;
  flex-shrink: 0;
}
.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.ring-percent {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1;
}
.ring-sublabel {
  font-size: 11px;
  color: var(--ac-text-secondary);
  margin-top: 3px;
}
.ring-legend {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ac-text-secondary);
}
.legend-item strong {
  margin-left: auto;
  color: var(--ac-text-primary);
  min-width: 24px;
  text-align: right;
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.legend-dot--done { background: var(--ac-primary, #3b82f6); }
.legend-dot--pending { background: var(--ac-border, #e5e7eb); }
.legend-dot--total { background: var(--ac-text-tertiary, #94a3b8); }

/* 计划 vs 实际 对比 */
.compare-section {
  margin-bottom: 22px;
}
.compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.compare-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 18px 20px;
}
.compare-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.compare-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.compare-detail {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}
.compare-sub {
  font-weight: 500;
}
.compare-behind {
  color: var(--ac-danger, #ef4444);
}

/* 章节进度表格 */
.chapters-section {
  margin-bottom: 10px;
}
.chapter-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.completion-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.completion-text {
  font-size: 12px;
  color: var(--ac-text-secondary);
}

@media (max-width: 768px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .compare-grid { grid-template-columns: 1fr; }
  .ring-chart-wrapper { flex-direction: column; align-items: flex-start; }
}
</style>
