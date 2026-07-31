<template>
  <div class="course-analytics fade-in">
    <PageHeader compact title="课程分析" subtitle="成绩分布、及格率与优秀率一览" icon="chart" />
    <div v-loading="loading">
      <!-- 指标卡片 -->
      <div class="stat-grid animate-in">
        <StatCard
          v-for="m in metrics"
          :key="m.key"
          :label="m.label"
          :value="m.value"
          :icon="m.icon"
          :tone="m.tone"
          :tone-color="m.toneColor"
        />
      </div>

      <!-- 成绩分布直方图 + 比率 -->
      <div class="grid-2">
        <div class="l1-card">
          <div class="l1-head">
            <span class="l1-ico"><el-icon><Histogram /></el-icon></span>
            <div><div class="l1-title">成绩分布直方图</div></div>
          </div>
          <div class="l1-body">
            <v-chart v-if="buckets.length" :option="histogramOption" class="dist-chart" autoresize />
            <el-empty v-else-if="!loading" description="暂无成绩分布数据" :image-size="72" />
          </div>
        </div>
        <div class="l1-card">
          <div class="l1-head">
            <span class="l1-ico"><el-icon><TrendCharts /></el-icon></span>
            <div><div class="l1-title">及格率 / 优秀率</div></div>
          </div>
          <div class="l1-body">
            <div class="rate-wrap">
            <div class="rate-item">
              <div class="rate-head">
                <span>及格率</span><b>{{ pctData(passRate) }}</b>
              </div>
              <el-progress
                :percentage="passRatePct"
                :color="varSuccess"
                :stroke-width="12"
                :show-text="false"
              />
            </div>
            <div class="rate-item">
              <div class="rate-head">
                <span>优秀率</span><b>{{ pctData(excellentRate) }}</b>
              </div>
              <el-progress
                :percentage="excellentRatePct"
                :color="varPrimary"
                :stroke-width="12"
                :show-text="false"
              />
            </div>
            <div v-if="extra.count != null" class="rate-extra">
              <div class="re">
                <b>{{ extra.count }}</b
                ><span>参考人数</span>
              </div>
              <div class="re">
                <b>{{ extra.avgScore ?? '--' }}</b
                ><span>平均分</span>
              </div>
              <div class="re">
                <b>{{ extra.maxScore ?? '--' }}</b
                ><span>最高分</span>
              </div>
              <div class="re">
                <b>{{ extra.minScore ?? '--' }}</b
                ><span>最低分</span>
              </div>
            </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import analyticsApi from '@/api/analytics'
import { getChartTheme, getMonoSeries } from '@/utils/chartTheme'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { Histogram, TrendCharts } from '@element-plus/icons-vue'

const route = useRoute()
const courseId = route.params.id

const loading = ref(false)
const stats = ref({})
const passRate = ref(0)
const excellentRate = ref(0)
const buckets = ref([])
const extra = ref({})

const varPrimary = 'var(--ac-primary)'
const varSuccess = 'var(--ac-success)'

const metrics = ref([])

function pctData(v) {
  if (v == null) return '--'
  if (typeof v === 'number' && v <= 1.0001 && v >= 0) return Math.round(v * 100) + '%'
  return v + '%'
}
const passRatePct = ref(0)
const excellentRatePct = ref(0)

const histogramOption = ref(buildHistogramOption())

function buildHistogramOption() {
  const t = getChartTheme()
  const mono = getMonoSeries()
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: t.tooltipBg,
      borderColor: t.tooltipBorder,
      textStyle: { color: t.tooltipText },
    },
    grid: { left: 44, right: 20, bottom: 32, top: 24 },
    xAxis: {
      type: 'category',
      data: [],
      axisLabel: { color: t.subText, fontSize: 11 },
      axisLine: { lineStyle: { color: t.axis } },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: t.subText, fontSize: 11 },
      splitLine: { lineStyle: { color: t.axis, type: 'dashed' } },
    },
    series: [
      {
        name: '人数',
        type: 'bar',
        data: [],
        itemStyle: { color: mono[0], borderRadius: [5, 5, 0, 0] },
        barMaxWidth: 46,
      },
    ],
  }
}

async function load() {
  loading.value = true
  try {
    const r1 = await analyticsApi.getCourseAnalytics(courseId)
    const a = r1?.data ?? r1 ?? {}
    stats.value = a

    metrics.value = [
      {
        key: 'totalStudents',
        label: '选课学生',
        value: a.totalStudents ?? a.studentCount ?? 0,
        icon: 'users',
        tone: 'var(--accent-soft)',
        toneColor: 'var(--accent)',
      },
      {
        key: 'activeStudents',
        label: '活跃学生',
        value: a.activeStudents ?? 0,
        icon: 'chart',
        tone: 'var(--info-soft)',
        toneColor: 'var(--info)',
      },
      {
        key: 'avgScore',
        label: '平均分',
        value: a.avgScore ?? '--',
        icon: 'award',
        tone: 'var(--warn-soft)',
        toneColor: 'var(--warn)',
      },
      {
        key: 'completionRate',
        label: '完成率',
        value: pctData(a.completionRate),
        icon: 'check',
        tone: 'var(--ok-soft)',
        toneColor: 'var(--ok)',
      },
    ]

    const r2 = await analyticsApi.getGradeHistogram(courseId)
    const h = r2?.data ?? r2 ?? {}
    buckets.value = h.buckets || []
    passRate.value = h.passRate ?? 0
    excellentRate.value = h.excellentRate ?? 0
    passRatePct.value = toPct(passRate.value)
    excellentRatePct.value = toPct(excellentRate.value)
    extra.value = h.summary || h || {}

    histogramOption.value = buildHistogramOption()
    histogramOption.value.xAxis.data = buckets.value.map((b) => b.range)
    histogramOption.value.series[0].data = buckets.value.map((b) => b.count)
  } catch (e) {
    logger.error('加载课程分析失败', e)
  } finally {
    loading.value = false
  }
}

function toPct(v) {
  if (v == null) return 0
  if (typeof v === 'number' && v <= 1.0001 && v >= 0) return Math.round(v * 100)
  return Number(v) || 0
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<style scoped>
.grid-2 {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}
.dist-chart {
  height: 320px;
}

.rate-wrap {
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.rate-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--ac-text-secondary);
}
.rate-head b {
  font-size: 18px;
  color: var(--ac-text-primary);
}
.rate-extra {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-top: 6px;
}
.re {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 8px;
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
}
.re b {
  font-size: 18px;
  color: var(--ac-text-primary);
}
.re span {
  font-size: 11.5px;
  color: var(--ac-text-muted);
  margin-top: 3px;
}

@media (max-width: 1100px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
