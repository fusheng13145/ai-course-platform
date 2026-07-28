<template>
  <div class="course-overview">
    <div v-loading="loading">
      <!-- 概览 + 内容 + 学习 指标卡 -->
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-icon" style="background: var(--ac-primary-bg); color: var(--ac-primary)">
            <el-icon :size="22"><User /></el-icon>
          </div>
          <div class="stat-info">
            <h3>{{ ov.totalStudents || 0 }}</h3>
            <p>选课学生</p>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="background: var(--ac-success-bg); color: var(--ac-success)">
            <el-icon :size="22"><Histogram /></el-icon>
          </div>
          <div class="stat-info">
            <h3>{{ ov.activeStudents || 0 }}</h3>
            <p>活跃学生</p>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="background: var(--ac-warning-bg); color: var(--ac-warning)">
            <el-icon :size="22"><Bell /></el-icon>
          </div>
          <div class="stat-info">
            <h3>{{ ov.pendingApproval || 0 }}</h3>
            <p>待审批</p>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="background: var(--ac-danger-bg); color: var(--ac-danger)">
            <el-icon :size="22"><EditPen /></el-icon>
          </div>
          <div class="stat-info">
            <h3>{{ grading.pendingGrading || 0 }}</h3>
            <p>待批改</p>
          </div>
        </div>
      </div>

      <!-- 内容与学习指标 -->
      <div class="stat-grid stat-grid--sm">
        <div class="mini-stat">
          <span class="mini-num">{{ ct.chapterCount || 0 }}</span
          ><span class="mini-label">章节</span>
        </div>
        <div class="mini-stat">
          <span class="mini-num">{{ ct.publishedChapterCount || 0 }}</span
          ><span class="mini-label">已发布章节</span>
        </div>
        <div class="mini-stat">
          <span class="mini-num">{{ ct.taskCount || 0 }}</span
          ><span class="mini-label">任务点</span>
        </div>
        <div class="mini-stat">
          <span class="mini-num"
            >{{ ct.taskPointsBoundToChapters || 0 }}/{{ ct.taskPointsTotal || 0 }}</span
          ><span class="mini-label">已绑章节任务</span>
        </div>
        <div class="mini-stat">
          <span class="mini-num">{{ ct.examCount || 0 }}</span
          ><span class="mini-label">试卷</span>
        </div>
        <div class="mini-stat">
          <span class="mini-num">{{ ct.questionCount || 0 }}</span
          ><span class="mini-label">题库</span>
        </div>
        <div class="mini-stat">
          <span class="mini-num">{{ pct(lr.avgProgress) }}</span
          ><span class="mini-label">平均进度</span>
        </div>
        <div class="mini-stat">
          <span class="mini-num">{{ pct(lr.completionRate) }}</span
          ><span class="mini-label">完成率</span>
        </div>
        <div class="mini-stat">
          <span class="mini-num">{{ lr.avgScore ?? '--' }}</span
          ><span class="mini-label">平均分</span>
        </div>
        <div class="mini-stat">
          <span class="mini-num">{{ pct(lr.passRate) }}</span
          ><span class="mini-label">及格率</span>
        </div>
        <div class="mini-stat">
          <span class="mini-num">{{ pct(lr.excellentRate) }}</span
          ><span class="mini-label">优秀率</span>
        </div>
      </div>

      <!-- 趋势 + 风险学生 -->
      <div class="grid-2">
        <div class="chart-card">
          <div class="chart-title">近 14 天教学活动趋势</div>
          <v-chart :option="trendOption" style="height: 300px" autoresize />
        </div>
        <div class="chart-card">
          <div class="chart-title">
            学习风险学生
            <el-tag size="small" type="danger" effect="light" round>{{ atRisk.length }}</el-tag>
          </div>
          <el-table :data="atRisk" size="small" max-height="300" empty-text="暂无风险学生">
            <el-table-column prop="studentName" label="学生" min-width="90" />
            <el-table-column label="进度" width="72">
              <template #default="{ row }">{{ pct(row.progress) }}</template>
            </el-table-column>
            <el-table-column label="成绩" width="64">
              <template #default="{ row }">{{ row.finalScore ?? '--' }}</template>
            </el-table-column>
            <el-table-column label="等级" width="80">
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
        </div>
      </div>

      <!-- AI 学情解读 -->
      <div v-if="aiInsight" class="chart-card ai-card">
        <div class="chart-title">
          <el-icon><MagicStick /></el-icon> AI 学情解读
        </div>
        <p class="ai-summary">{{ aiInsight.summary }}</p>
        <ul v-if="aiInsight.items && aiInsight.items.length" class="ai-items">
          <li v-for="(it, i) in aiInsight.items" :key="i">
            <strong>{{ it.title }}</strong
            ><span>{{ it.detail }}</span>
          </li>
        </ul>
      </div>

      <!-- 章节树（含任务点） -->
      <div class="chart-card">
        <div class="chart-title">章节与任务点</div>
        <div v-if="chapterTree.length" class="tree">
          <div v-for="node in chapterTree" :key="node.id" class="tree-node">
            <div class="tree-row">
              <span class="tree-title">{{ node.title }}</span>
              <span class="tree-meta"
                >任务点 {{ node.taskCount || 0 }} · 提交 {{ node.submittedCount || 0 }}/{{
                  node.studentCount || 0
                }}
                · 完成率 {{ pct(node.completionRate) }}</span
              >
              <el-tag size="small" :type="node.isPublished ? 'success' : 'info'" effect="light">{{
                node.isPublished ? '已发布' : '草稿'
              }}</el-tag>
            </div>
            <div v-if="node.taskPoints && node.taskPoints.length" class="tree-tasks">
              <span v-for="tp in node.taskPoints" :key="tp.id" class="task-chip">
                {{ tp.title }} <em>{{ tp.taskType }}</em>
              </span>
            </div>
            <div v-if="node.children && node.children.length" class="tree-children">
              <div v-for="c in node.children" :key="c.id" class="tree-node tree-node--child">
                <div class="tree-row">
                  <span class="tree-title">{{ c.title }}</span>
                  <span class="tree-meta"
                    >任务点 {{ c.taskCount || 0 }} · 完成率 {{ pct(c.completionRate) }}</span
                  >
                </div>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无章节" :image-size="70" />
      </div>
    </div>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import teachingApi from '@/api/teacher'
import { getChartTheme, getMonoSeries } from '@/utils/chartTheme'
import { User, Histogram, Bell, EditPen, MagicStick } from '@element-plus/icons-vue'

const route = useRoute()
const courseId = route.params.id

const loading = ref(false)
const ov = ref({})
const ct = ref({})
const lr = ref({})
const grading = ref({})
const trend = ref([])
const atRisk = ref([])
const chapterTree = ref([])
const aiInsight = ref(null)

function pct(v) {
  if (v == null) return '--'
  return typeof v === 'number' && v <= 1.0001 && v > 0 ? Math.round(v * 100) + '%' : v + '%'
}

const trendOption = ref({
  tooltip: { trigger: 'axis' },
  legend: { data: ['活跃学生', '提交数'], textStyle: { color: getChartTheme().subText } },
  grid: { left: 44, right: 20, bottom: 28, top: 36 },
  xAxis: {
    type: 'category',
    data: [],
    axisLabel: { color: getChartTheme().subText, fontSize: 11 },
    axisLine: { lineStyle: { color: getChartTheme().axis } },
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: getChartTheme().subText, fontSize: 11 },
    splitLine: { lineStyle: { color: getChartTheme().axis, type: 'dashed' } },
  },
  series: [
    {
      name: '活跃学生',
      type: 'line',
      smooth: true,
      symbolSize: 5,
      itemStyle: { color: getMonoSeries()[0] },
      lineStyle: { width: 2.5, color: getMonoSeries()[0] },
      areaStyle: { color: 'rgba(59,110,246,0.12)' },
      data: [],
    },
    {
      name: '提交数',
      type: 'bar',
      itemStyle: { color: getMonoSeries()[1] || getMonoSeries()[0], borderRadius: [4, 4, 0, 0] },
      data: [],
    },
  ],
})

async function load() {
  loading.value = true
  try {
    const res = await teachingApi.getCourseDashboard(courseId)
    const d = unwrap(res) || {}
    ov.value = d.overview || {}
    ct.value = d.content || {}
    lr.value = d.learning || {}
    grading.value = d.grading || {}
    trend.value = d.trend || []
    atRisk.value = d.atRiskStudents || []
    chapterTree.value = d.chapterTree || []
    aiInsight.value = d.aiInsight || null

    trendOption.value.xAxis.data = trend.value.map((t) => t.date)
    trendOption.value.series[0].data = trend.value.map((t) => t.activeStudents)
    trendOption.value.series[1].data = trend.value.map((t) => t.submissions)
  } catch (e) {
    logger.error('加载课程驾驶舱失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}
.stat-grid--sm {
  grid-template-columns: repeat(11, 1fr);
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

.grid-2 {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: var(--ac-spacing-md);
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

.ai-card {
  margin-bottom: var(--ac-spacing-md);
}
.ai-summary {
  margin: 0 0 10px;
  color: var(--ac-text-secondary);
  line-height: 1.6;
  font-size: 13.5px;
}
.ai-items {
  margin: 0;
  padding-left: 18px;
  color: var(--ac-text-secondary);
  font-size: 13px;
  line-height: 1.9;
}
.ai-items strong {
  color: var(--ac-text-primary);
  margin-right: 8px;
}

.tree {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.tree-node {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
}
.tree-node--child {
  background: transparent;
  border-style: dashed;
  margin-left: 22px;
}
.tree-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.tree-title {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 14px;
}
.tree-meta {
  font-size: 12px;
  color: var(--ac-text-muted);
}
.tree-tasks {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.task-chip {
  font-size: 12px;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
}
.task-chip em {
  font-style: normal;
  opacity: 0.7;
  margin-left: 4px;
}
.tree-children {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

@media (max-width: 1100px) {
  .stat-grid,
  .stat-grid--sm {
    grid-template-columns: repeat(2, 1fr);
  }
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
