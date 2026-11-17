<template>
  <section class="admin-analytics">
    <div class="filter-row analytics-tools">
      <select v-model.number="days" class="input" aria-label="统计区间" @change="load">
        <option :value="7">近 7 天</option>
        <option :value="30">近 30 天</option>
        <option :value="90">近 90 天</option>
      </select>
      <button class="btn btn-sm" :disabled="loading" @click="load">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </div>

    <div v-if="overviewError" class="callout danger" role="alert">
      <span>平台总览</span>
      <p>{{ overviewError }}</p>
    </div>
    <div v-if="loading && !hasOverview" class="empty-state compact">
      <b>正在加载平台指标…</b>
    </div>
    <div v-else-if="hasOverview" class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <div class="analytics-grid">
      <section class="card">
        <header class="card-head">
          <div>
            <h2>
              用户增长 <span class="chip chip-mute">近 {{ days }} 天</span>
            </h2>
          </div>
        </header>
        <div v-if="growthError" class="empty-state compact">
          <b>增长趋势未能加载</b>
          <p>{{ growthError }}</p>
        </div>
        <div v-else-if="growthBars.length" class="bar-chart">
          <div v-for="point in growthBars" :key="point.date" class="bar-col">
            <span class="bar-val">{{ point.count }}</span>
            <i class="bar" :style="{ '--v': `${point.pct}%` }"></i>
            <span class="bar-label">{{ point.label }}</span>
          </div>
        </div>
        <div v-else class="empty-state compact">
          <b>暂无注册增长记录</b>
          <p>统计区间内没有新增用户。</p>
        </div>
      </section>

      <section class="card">
        <header class="card-head">
          <div>
            <h2>学习活跃热力</h2>
          </div>
          <span class="head-hint">按星期 × 时段统计学习记录</span>
        </header>
        <div v-if="heatmapError" class="empty-state compact">
          <b>热力图未能加载</b>
          <p>{{ heatmapError }}</p>
        </div>
        <div v-else class="heatmap">
          <div class="heat-corner"></div>
          <div v-for="slot in SLOTS" :key="slot" class="heat-head">{{ slot }}</div>
          <template v-for="day in DAYS" :key="day.id">
            <div class="heat-day">{{ day.label }}</div>
            <div
              v-for="slot in SLOTS.length"
              :key="`${day.id}-${slot}`"
              class="heat-cell"
              :style="heatStyle(heatValue(day.id, slot - 1))"
              :title="`${day.label} · ${SLOTS[slot - 1]} · ${heatValue(day.id, slot - 1)} 条`"
            >
              {{ heatValue(day.id, slot - 1) || '' }}
            </div>
          </template>
        </div>
      </section>
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>
            热门课程排行 <span class="chip chip-mute">TOP {{ ranking.length }}</span>
          </h2>
        </div>
      </header>
      <div v-if="rankingError" class="empty-state compact">
        <b>课程排行未能加载</b>
        <p>{{ rankingError }}</p>
      </div>
      <div v-else-if="!ranking.length" class="empty-state compact">
        <b>暂无在用课程</b>
        <p>课程启用并产生选课后，这里会出现排名。</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-64">#</th>
              <th>课程</th>
              <th class="w-120">课程代码</th>
              <th class="w-120">选课人数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in ranking" :key="String(row.courseId)">
              <td>{{ index + 1 }}</td>
              <td>
                <b>{{ row.courseName || `课程 #${row.courseId}` }}</b>
              </td>
              <td>{{ row.courseCode || '—' }}</td>
              <td>{{ row.enrollmentCount ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Stat from '../../components/Stat.vue'
import analyticsApi from '@/api/analytics'
import { firstNonEmptyArray, pickList, unwrap } from '@/utils/response'
import type { Metric } from '../../nav'

interface Overview {
  totalUsers?: number
  activeUsers?: number
  totalCourses?: number
  activeCourses?: number
  totalEnrollments?: number
  activeLearningUsers?: number
  completionRate?: number
  totalTasks?: number
  totalExams?: number
}

interface RankingRow {
  courseId?: number | string
  courseName?: string
  courseCode?: string
  enrollmentCount?: number
}

interface GrowthRow {
  date?: string
  newUsers?: number
  totalUsers?: number
}

interface HeatCell {
  dayOfWeek?: number
  hour?: number
  value?: number
}

const DAYS = [
  { id: 0, label: '周日' },
  { id: 1, label: '周一' },
  { id: 2, label: '周二' },
  { id: 3, label: '周三' },
  { id: 4, label: '周四' },
  { id: 5, label: '周五' },
  { id: 6, label: '周六' },
] as const
const SLOTS = ['0–6 时', '6–12 时', '12–18 时', '18–24 时'] as const

const days = ref(30)
const loading = ref(false)
const overview = ref<Overview>({})
const ranking = ref<RankingRow[]>([])
const growth = ref<GrowthRow[]>([])
const heatmap = ref<HeatCell[]>([])
const overviewError = ref('')
const rankingError = ref('')
const growthError = ref('')
const heatmapError = ref('')

const hasOverview = computed(() => Object.keys(overview.value).length > 0)
const heatMax = computed(() => Math.max(1, ...heatmap.value.map((cell) => Number(cell.value || 0))))

const metrics = computed<Metric[]>(() => [
  {
    label: '总用户',
    value: overview.value.totalUsers ?? '—',
    unit: '人',
    note: `启用 ${overview.value.activeUsers ?? '—'} 人`,
    icon: 'users',
  },
  {
    label: '在用课程',
    value: overview.value.activeCourses ?? '—',
    unit: '门',
    note: `课程总数 ${overview.value.totalCourses ?? '—'}`,
    icon: 'book',
    tone: 'ok',
  },
  {
    label: '选课记录',
    value: overview.value.totalEnrollments ?? '—',
    unit: '条',
    note: `近 7 天学习记录 ${overview.value.activeLearningUsers ?? '—'}`,
    icon: 'layers',
  },
  {
    label: '课程完成率',
    value: formatRate(overview.value.completionRate),
    note: '进度 100% 的选课占比',
    icon: 'check',
    tone: 'info',
  },
])

const growthBars = computed(() => {
  const rows = growth.value.slice(-14)
  const max = Math.max(1, ...rows.map((row) => Number(row.newUsers || 0)))
  return rows.map((row) => ({
    date: String(row.date || ''),
    label: String(row.date || '').slice(5),
    count: Number(row.newUsers || 0),
    pct: Math.round((Number(row.newUsers || 0) / max) * 100),
  }))
})

function formatRate(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const num = Number(value)
  return Number.isFinite(num) ? `${num.toFixed(2)}%` : '—'
}

function heatValue(day: number, slot: number): number {
  const cell = heatmap.value.find(
    (item) => Number(item.dayOfWeek) === day && Number(item.hour) === slot,
  )
  return Number(cell?.value || 0)
}

function heatStyle(value: number): Record<string, string> {
  const ratio = Math.min(1, value / heatMax.value)
  return { '--heat': String(0.06 + ratio * 0.55) }
}

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

async function load() {
  loading.value = true
  overviewError.value = ''
  rankingError.value = ''
  growthError.value = ''
  heatmapError.value = ''

  const [overviewRes, rankingRes, growthRes, heatmapRes] = await Promise.allSettled([
    analyticsApi.getPlatformOverview(),
    analyticsApi.getCourseRanking(),
    analyticsApi.getUserGrowth(days.value),
    analyticsApi.getPlatformHeatmap({ days: days.value }),
  ])

  if (overviewRes.status === 'fulfilled') {
    const payload = unwrap<Overview>(overviewRes.value)
    overview.value = payload && typeof payload === 'object' ? payload : {}
  } else {
    overviewError.value = errorText(overviewRes.reason, '平台总览加载失败')
  }

  if (rankingRes.status === 'fulfilled') {
    const payload = unwrap<unknown>(rankingRes.value)
    ranking.value = pickList<RankingRow>(payload)
  } else {
    rankingError.value = errorText(rankingRes.reason, '课程排行加载失败')
  }

  if (growthRes.status === 'fulfilled') {
    const payload = unwrap<unknown>(growthRes.value)
    growth.value = firstNonEmptyArray<GrowthRow>(payload, pickList<GrowthRow>(payload))
  } else {
    growthError.value = errorText(growthRes.reason, '增长趋势加载失败')
  }

  if (heatmapRes.status === 'fulfilled') {
    const payload = unwrap<unknown>(heatmapRes.value)
    heatmap.value = firstNonEmptyArray<HeatCell>(payload, pickList<HeatCell>(payload))
  } else {
    heatmapError.value = errorText(heatmapRes.reason, '热力图加载失败')
  }

  loading.value = false
}

onMounted(() => void load())
</script>

<style scoped>
.admin-analytics {
  display: grid;
  gap: 14px;
}
.analytics-tools {
  width: auto;
  justify-content: flex-end;
}
.analytics-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 1fr);
  gap: 14px;
}
.heatmap {
  display: grid;
  grid-template-columns: 54px repeat(4, minmax(0, 1fr));
  gap: 4px;
  padding: 12px 16px 16px;
}
.heat-corner,
.heat-head,
.heat-day {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--text-mute);
}
.heat-day {
  justify-content: flex-start;
  padding-left: 4px;
}
.heat-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  border-radius: 6px;
  font-size: 11px;
  color: var(--text);
  background: rgba(22, 163, 74, var(--heat, 0.06));
}
@media (max-width: 900px) {
  .analytics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
