<template>
  <section class="analytics">
    <div class="course-tab-bar">
      <div>
        <p class="card-kicker">COURSE / ANALYTICS</p>
        <h2>成绩与学情</h2>
      </div>
      <div class="course-tab-actions">
        <select
          v-if="!courseId"
          v-model.number="picked"
          class="input course-pick"
          aria-label="选择课程"
        >
          <option :value="0">选择课程</option>
          <option v-for="course in courses" :key="course.id" :value="Number(course.id)">
            {{ course.name || course.title }}
          </option>
        </select>
        <button class="btn btn-ghost" :disabled="loading" @click="load">刷新</button>
      </div>
    </div>

    <div v-if="loadError" class="callout">
      <span>NOTICE / 数据可用性</span>
      <p>{{ loadError }}</p>
    </div>

    <div v-if="loading && !metrics.length" class="empty-state compact">
      <b>正在加载课程指标…</b>
    </div>
    <div v-else-if="metrics.length" class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="teacher" />
    </div>

    <div class="analytics-grid">
      <section class="card">
        <header class="card-head">
          <div>
            <p class="card-kicker">DISTRIBUTION / GRADES</p>
            <h2>
              分数段分布 <span class="chip chip-mute">{{ extra.count ?? 0 }} 人</span>
            </h2>
          </div>
          <span class="head-hint">仅统计已录入成绩的学生</span>
        </header>
        <div v-if="buckets.length" class="bar-chart">
          <div v-for="bucket in buckets" :key="bucket.range" class="bar-col">
            <span class="bar-val">{{ bucket.count }}</span>
            <i class="bar" :style="{ '--v': `${bucket.pct}%` }"></i>
            <span class="bar-label">{{ bucket.range }}</span>
          </div>
        </div>
        <div v-else-if="histError" class="empty-state compact">
          <b>成绩分布未能加载</b>
          <p>请查看上方提示后重试。</p>
        </div>
        <div v-else class="empty-state compact">
          <b>尚无人成绩可统计</b>
          <p>批阅完成并录入总评后，这里会出现分数段分布。</p>
        </div>
        <footer v-if="extra.count" class="card-foot">
          <span>均分 {{ display(extra.avgScore) }}</span>
          <span>最高 {{ display(extra.maxScore) }} · 最低 {{ display(extra.minScore) }}</span>
        </footer>
      </section>

      <section class="card">
        <header class="card-head">
          <div>
            <p class="card-kicker">RATES / QUALITY</p>
            <h2>及格与优秀</h2>
          </div>
        </header>
        <ul class="rate-list">
          <li>
            <span class="r-label">及格率（≥60）</span>
            <span class="r-track"><i :style="{ width: `${passRatePct}%` }" /></span>
            <b class="r-num">{{ rateText(passRate) }}</b>
          </li>
          <li>
            <span class="r-label">优秀率（≥90）</span>
            <span class="r-track r-track--ok"
              ><i :style="{ width: `${excellentRatePct}%` }"
            /></span>
            <b class="r-num">{{ rateText(excellentRate) }}</b>
          </li>
        </ul>
        <p v-if="histError" class="modal-hint">读数不可用，未回退成 0%。</p>
        <p v-else-if="!extra.count" class="modal-hint">没有成绩样本，比率不计。</p>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Stat from '../../../components/Stat.vue'
import { useUi } from '../../../composables/ui'
import analyticsApi from '@/api/analytics'
import { getAllMyCourses } from '@/api/course'
import logger from '@/utils/logger'
import type { Metric } from '../../../nav'

const BUCKET_ORDER = [
  '0-59（不及格）',
  '60-69（及格）',
  '70-79（中等）',
  '80-89（良好）',
  '90-100（优秀）',
]

interface Bucket {
  range: string
  count: number
  pct: number
}

interface ExtraStats {
  count?: number
  avgScore?: number | null
  maxScore?: number | null
  minScore?: number | null
}

interface HistogramPayload {
  total?: number
  distribution?: Record<string, number>
  passRate?: number | null
  excellentRate?: number | null
  average?: number | null
  maxScore?: number | null
  minScore?: number | null
}

const props = defineProps<{ courseId?: number }>()
const { toast } = useUi()

const loading = ref(false)
const metrics = ref<Metric[]>([])
const buckets = ref<Bucket[]>([])
const extra = ref<ExtraStats>({})
const passRate = ref<number | null>(null)
const excellentRate = ref<number | null>(null)
const histError = ref(false)
const loadError = ref('')
const picked = ref(0)
const courses = ref<{ id: number | string; name?: string; title?: string }[]>([])

const passRatePct = computed(() => toPct(passRate.value))
const excellentRatePct = computed(() => toPct(excellentRate.value))

function toPct(value: number | null): number {
  if (value === null || value === undefined) return 0
  const number = Number(value)
  if (!Number.isFinite(number)) return 0
  return number >= 0 && number <= 1.0001 ? Math.round(number * 100) : Math.round(number)
}

function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

function pctValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return `${toPct(Number(value))}%`
}

/** 无样本或读数不可用时不谎报 0% */
function rateText(value: number | null): string {
  if (histError.value || value === null || !extra.value.count) return '—'
  return `${toPct(value)}%`
}

async function load() {
  const target = props.courseId || picked.value
  if (!target) {
    metrics.value = []
    buckets.value = []
    extra.value = {}
    passRate.value = null
    excellentRate.value = null
    histError.value = false
    loadError.value = ''
    return
  }
  loading.value = true
  loadError.value = ''
  histError.value = false
  const notices: string[] = []
  // 两路端点相互独立：任一路失败不得拖垮另一路，也不得把失败伪装成 0 值
  const [courseRes, histRes] = await Promise.allSettled([
    analyticsApi.getCourseAnalytics(target),
    analyticsApi.getGradeHistogram(target),
  ])

  if (courseRes.status === 'fulfilled') {
    const body = courseRes.value as { data?: Record<string, unknown> }
    const a = (body?.data ?? body ?? {}) as Record<string, unknown>
    metrics.value = [
      {
        label: '选课学生',
        value: display(a.enrollmentCount ?? 0),
        unit: '人',
        note: '课程级',
        icon: 'users',
      },
      {
        label: '平均分',
        value: display(a.averageScore),
        unit: '分',
        note: '已录入成绩',
        icon: 'chart',
        tone: 'warn',
      },
      {
        label: '平均进度',
        value: pctValue(a.averageProgress),
        note: '章节完成度口径',
        icon: 'check',
        tone: 'ok',
      },
    ]
  } else {
    logger.error('加载课程指标失败', courseRes.reason)
    metrics.value = []
    notices.push('课程指标未能加载。')
  }

  if (histRes.status === 'fulfilled') {
    const body = histRes.value as { data?: HistogramPayload }
    const h = (body?.data ?? body ?? {}) as HistogramPayload
    const total = Number(h.total) || 0
    buckets.value =
      total > 0
        ? BUCKET_ORDER.map((range) => ({
            range,
            count: Number(h.distribution?.[range] ?? 0),
            pct: 0,
          })).map((bucket) => ({
            ...bucket,
            pct: Math.round((bucket.count / Math.max(1, total)) * 100),
          }))
        : []
    passRate.value = h.passRate ?? 0
    excellentRate.value = h.excellentRate ?? 0
    extra.value =
      total > 0
        ? { count: total, avgScore: h.average, maxScore: h.maxScore, minScore: h.minScore }
        : {}
  } else {
    logger.error('加载成绩分布失败', histRes.reason)
    buckets.value = []
    extra.value = {}
    passRate.value = null
    excellentRate.value = null
    histError.value = true
    notices.push('成绩分布未能加载。')
  }

  loadError.value = notices.join('')
  if (notices.length) toast('部分学情读数不可用', 'warn')
  loading.value = false
}

watch(() => props.courseId, load, { immediate: true })
watch(picked, load)

async function loadCourses() {
  try {
    courses.value = (await getAllMyCourses()) as typeof courses.value
  } catch (error) {
    logger.error('加载教师课程失败', error)
    courses.value = []
  }
}

// 全局「学情分析」页没有课程上下文，需要自带课程选择器
onMounted(() => {
  if (!props.courseId) void loadCourses()
})
</script>

<style scoped>
.analytics {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.analytics-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 14px;
}
.head-hint {
  font-size: 10.5px;
  color: var(--ink-3);
}
.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  height: 190px;
  padding: 8px 4px 0;
  border-bottom: 1px solid var(--line);
}
.bar-col {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  height: 100%;
}
.bar-col .bar {
  width: 100%;
  max-width: 46px;
  height: var(--v, 0%);
  min-height: 2px;
  border-radius: var(--r-bar) var(--r-bar) 0 0;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--accent) 66%, var(--surface)),
    var(--accent)
  );
  box-shadow: 0 2px 7px color-mix(in srgb, var(--accent) 22%, transparent);
  transition:
    height 0.3s var(--ease),
    filter 0.15s ease,
    transform 0.15s ease;
  transform-origin: bottom;
}
.bar-col:hover .bar {
  filter: brightness(1.05);
  transform: translateY(-2px);
}
.bar-val {
  font-family: var(--font-display);
  font-size: 13px;
  color: var(--ink-2);
}
.bar-label {
  font-size: 9px;
  color: var(--ink-3);
  text-align: center;
}
.rate-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.rate-list li {
  display: grid;
  grid-template-columns: 1fr;
  gap: 6px;
}
.r-label {
  font-size: 11px;
  color: var(--ink-2);
}
.r-track {
  height: 10px;
  border-radius: var(--r-ctl);
  background: var(--surface-3);
  overflow: hidden;
}
.r-track i {
  display: block;
  height: 100%;
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--accent) 64%, var(--surface)),
    var(--accent)
  );
  border-radius: inherit;
  transition: width 0.3s var(--ease);
}
.r-track--ok {
  background: var(--ok-soft);
}
.r-track--ok i {
  background: linear-gradient(90deg, color-mix(in srgb, var(--ok) 64%, var(--surface)), var(--ok));
}
.r-num {
  font-family: var(--font-display);
  font-size: 18px;
}
@media (max-width: 1100px) {
  .analytics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
