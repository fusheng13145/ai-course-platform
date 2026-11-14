<template>
  <div class="lc-panel">
    <div class="lc-toolbar">
      <button class="btn btn-ghost btn-sm" :disabled="loading" @click="loadAll">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </div>

    <div v-if="statsError" class="callout danger" role="alert">
      <span>学习统计</span>
      <p>{{ statsError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="loadStats">重试</button>
    </div>
    <div v-else-if="statsLoading && !stats" class="empty-state compact">
      <b>正在加载学习统计…</b>
    </div>
    <div v-else-if="stats" class="stat-grid kpi-grid">
      <div v-for="item in kpis" :key="item.label" class="stat">
        <span class="stat-label">{{ item.label }}</span>
        <b class="stat-value">{{ item.value }}</b>
        <span class="stat-note">{{ item.note }}</span>
      </div>
    </div>

    <section class="card">
      <header class="card-head">
        <div>
          <h2>
            {{ monthLabel }} <span class="chip chip-mute">{{ monthDays.length }} 天</span>
          </h2>
        </div>
        <div class="course-tab-actions">
          <button class="btn btn-ghost btn-sm" @click="shiftMonth(-1)">上月</button>
          <button class="btn btn-ghost btn-sm" @click="shiftMonth(1)">下月</button>
        </div>
      </header>
      <p v-if="calendarError" class="form-error">{{ calendarError }}</p>
      <div v-if="calendarLoading" class="empty-state compact"><b>正在加载日历…</b></div>
      <template v-else>
        <div class="lc-weekhead" aria-hidden="true">
          <span v-for="d in WEEKDAYS" :key="d">{{ d }}</span>
        </div>
        <div class="lc-grid" role="list" :aria-label="`${monthLabel}学习日历`">
          <span v-for="pad in leadingBlanks" :key="`pad${pad}`" class="lc-cell is-blank" />
          <span
            v-for="day in monthDays"
            :key="day.date"
            role="listitem"
            class="lc-cell"
            :class="`is-l${day.level}`"
            :title="`${day.date} · ${day.duration} 分钟 · ${day.count} 次`"
          >
            {{ day.dayOfMonth }}
          </span>
        </div>
        <p v-if="!monthTotal" class="academic-note">本月暂无学习记录。</p>
        <p v-else class="academic-note">
          本月累计 <b>{{ monthTotal }}</b> 分钟，覆盖 <b>{{ activeDays }}</b> 天。
        </p>
      </template>
    </section>

    <section class="card">
      <header class="card-head">
        <div>
          <h2>最近学习记录</h2>
        </div>
      </header>
      <p v-if="recordsError" class="form-error">{{ recordsError }}</p>
      <div v-else-if="recordsLoading" class="empty-state compact"><b>正在加载记录…</b></div>
      <div v-else-if="!records.length" class="empty-state compact">
        <b>还没有学习记录</b>
        <p>在课程「章节与资料」里标记完成后，这里会出现记录。</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>时间</th>
              <th>动作</th>
              <th>课程</th>
              <th>章节</th>
              <th class="num">时长（分钟）</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in recent" :key="row.id">
              <td>{{ row.createdAt || '—' }}</td>
              <td>
                <span class="chip chip-mute">{{ actionText(row.actionType) }}</span>
              </td>
              <td>{{ courseName(row.courseId) }}</td>
              <td>{{ row.chapterId ?? '—' }}</td>
              <td class="num">{{ row.duration ?? 0 }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getHeatmap, getLearningStats, getMyLearningRecords } from '@/api/learningRecord'
import { getAllMyCourses } from '@/api/student'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'

interface StatBody {
  totalDuration?: number
  learningDays?: number
  completedCourses?: number
  currentCourses?: number
  todayDuration?: number
}

interface CalendarRow {
  date: string
  duration: number
  count: number
}
interface RecordRow {
  id?: number | string
  courseId?: number | null
  chapterId?: number | null
  actionType?: string
  duration?: number | null
  createdAt?: string
}

const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']
const ACTION_LABEL: Record<string, string> = {
  COMPLETE: '章节完成',
  VIEW: '浏览',
  PRACTICE: '练习',
  EXAM: '考试',
}

const now = new Date()
const year = ref(now.getFullYear())
const month = ref(now.getMonth() + 1)

const stats = ref<StatBody | null>(null)
const statsLoading = ref(false)
const statsError = ref('')
const calendar = ref<CalendarRow[]>([])
const calendarLoading = ref(false)
const calendarError = ref('')
const records = ref<RecordRow[]>([])
const recordsLoading = ref(false)
const recordsError = ref('')
const courses = ref<{ id?: number | string; name?: string }[]>([])

const loading = computed(() => statsLoading.value || calendarLoading.value || recordsLoading.value)
const monthLabel = computed(() => `${year.value} 年 ${month.value} 月`)

const kpis = computed(() => [
  { label: '今日学习', value: `${stats.value?.todayDuration ?? 0} 分钟`, note: '当天累计' },
  { label: '累计时长', value: `${stats.value?.totalDuration ?? 0} 分钟`, note: '全部记录合计' },
  { label: '学习天数', value: `${stats.value?.learningDays ?? 0} 天`, note: '有记录的日期数' },
  {
    label: '在学课程',
    value: `${stats.value?.currentCourses ?? 0} 门`,
    note: `已修完 ${stats.value?.completedCourses ?? 0} 门`,
  },
])

const leadingBlanks = computed(() => {
  const first = new Date(year.value, month.value - 1, 1)
  return (first.getDay() + 6) % 7
})

const monthDays = computed(() =>
  calendar.value.map((row) => {
    const dayOfMonth = Number(row.date.slice(-2))
    const duration = Number(row.duration || 0)
    const level = duration <= 0 ? 0 : duration < 15 ? 1 : duration < 60 ? 2 : 3
    return { date: row.date, dayOfMonth, duration, count: row.count || 0, level }
  }),
)

const monthTotal = computed(() => monthDays.value.reduce((sum, d) => sum + d.duration, 0))
const activeDays = computed(() => monthDays.value.filter((d) => d.duration > 0).length)

const recent = computed(() =>
  [...records.value]
    .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
    .slice(0, 12),
)

function courseName(id?: number | null) {
  if (id == null) return '—'
  const hit = courses.value.find((c) => String(c.id) === String(id))
  return hit?.name || `课程 ${id}`
}
function actionText(type?: string) {
  return (type && ACTION_LABEL[type]) || type || '—'
}

async function loadStats() {
  statsLoading.value = true
  statsError.value = ''
  try {
    stats.value = (unwrap(await getLearningStats()) || {}) as StatBody
  } catch (error) {
    logger.error('加载学习统计失败', error)
    statsError.value = '学习统计加载失败，请稍后重试'
    stats.value = null
  } finally {
    statsLoading.value = false
  }
}

async function loadCalendar() {
  calendarLoading.value = true
  calendarError.value = ''
  try {
    const body = unwrap(await getHeatmap({ year: year.value, month: month.value }))
    calendar.value = Array.isArray(body) ? (body as CalendarRow[]) : []
  } catch (error) {
    logger.error('加载学习日历失败', error)
    calendarError.value = '学习日历加载失败，请稍后重试'
    calendar.value = []
  } finally {
    calendarLoading.value = false
  }
}

async function loadRecords() {
  recordsLoading.value = true
  recordsError.value = ''
  try {
    const body = unwrap(await getMyLearningRecords())
    records.value = Array.isArray(body) ? (body as RecordRow[]) : []
  } catch (error) {
    logger.error('加载学习记录失败', error)
    recordsError.value = '学习记录加载失败，请稍后重试'
    records.value = []
  } finally {
    recordsLoading.value = false
  }
}

async function loadCourses() {
  try {
    const body = unwrap(await getAllMyCourses())
    courses.value = Array.isArray(body) ? (body as { id?: number; name?: string }[]) : []
  } catch {
    courses.value = []
  }
}

function shiftMonth(delta: number) {
  const d = new Date(year.value, month.value - 1 + delta, 1)
  year.value = d.getFullYear()
  month.value = d.getMonth() + 1
  void loadCalendar()
}

function loadAll() {
  void loadStats()
  void loadCalendar()
  void loadRecords()
}

onMounted(() => {
  loadAll()
  void loadCourses()
})
</script>

<style scoped>
.lc-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.lc-weekhead,
.lc-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
}
.lc-weekhead {
  margin-top: 8px;
  color: var(--ink-3);
  font-size: 11px;
  text-align: center;
}
.lc-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1 / 1;
  border-radius: var(--r-xs);
  border: 1px solid var(--line);
  font-size: 11px;
  color: var(--ink-2);
}
.lc-cell.is-blank {
  border-color: transparent;
}
.lc-cell.is-l1 {
  background: color-mix(in srgb, var(--accent) 14%, transparent);
}
.lc-cell.is-l2 {
  background: color-mix(in srgb, var(--accent) 32%, transparent);
}
.lc-cell.is-l3 {
  background: color-mix(in srgb, var(--accent) 52%, transparent);
  color: var(--on-brand);
}
table td.num,
table th.num {
  text-align: right;
}
</style>
