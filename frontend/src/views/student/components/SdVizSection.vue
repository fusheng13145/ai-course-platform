<template>
  <section v-if="vizReady" class="viz-grid animate-in d2">
    <!-- 可视化为增强项：加载失败给出可见提示（不静默伪装成「无数据」，D2-1） -->
    <p v-if="vizError" class="viz-error">{{ vizError }}</p>
    <div class="viz-card viz-wide">
      <div class="viz-head">
        <span class="viz-title">近 14 天学习时长</span>
        <span class="viz-sub">单位：分钟 · learning_records 聚合</span>
      </div>
      <VChart :option="durationOption" autoresize style="height: 220px" />
    </div>
    <div class="viz-card">
      <div class="viz-head">
        <span class="viz-title">课程薄弱度</span>
        <span class="viz-sub">未掌握错题占比（%）</span>
      </div>
      <VChart
        v-if="radarDimensions.length"
        :option="radarOption"
        autoresize
        style="height: 220px"
      />
      <EmptyState
        v-else
        :title="vizError ? '暂无错题数据（数据加载失败，请查看上方提示）' : '暂无错题数据'"
        icon="book"
        size="compact"
      />
    </div>
    <div class="viz-card viz-wide">
      <div class="viz-head">
        <span class="viz-title">学习活跃热力图</span>
        <span class="viz-sub">课程 × 近 7 天 · 学习时长（分钟）</span>
      </div>
      <VChart v-if="heatRows.length" :option="heatOption" autoresize style="height: 180px" />
      <EmptyState
        v-else
        :title="vizError ? '近 7 天暂无学习记录（数据加载失败，请查看上方提示）' : '近 7 天暂无学习记录'"
        icon="chart"
        size="compact"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import studentApi from '@/api/student'
import EmptyState from '@/components/ui/EmptyState.vue'

/**
 * 学生驾驶舱 — 学情可视化区（近 14 天时长 / 课程薄弱度雷达 / 活跃热力图）。
 * <p>拆分子组件：自包含数据加载（getVisualizationData）与 ECharts option 构造，
 * 加载失败降级为空图但以 `vizError` 可见提示（不再静默伪装成「无数据」，D2-1）。</p>
 */
const vizData = ref({ durationTrend: [], radarData: [], heatmapData: [] })
const vizReady = ref(false)
/** 加载失败提示（增强项降级但须可见）；空串表示加载成功 */
const vizError = ref('')

async function loadVisualization() {
  vizError.value = ''
  try {
    const res = await studentApi.getVisualizationData()
    const d = res?.data?.data || res?.data || {}
    vizData.value = {
      durationTrend: Array.isArray(d.durationTrend) ? d.durationTrend : [],
      radarData: Array.isArray(d.radarData) ? d.radarData : [],
      heatmapData: Array.isArray(d.heatmapData) ? d.heatmapData : [],
    }
  } catch {
    // 可视化为增强项：加载失败降级为空图，但必须提示失败而非静默（D2-1）
    vizData.value = { durationTrend: [], radarData: [], heatmapData: [] }
    vizError.value = '学情可视化数据加载失败，以下图表可能不完整'
  } finally {
    vizReady.value = true
  }
}

onMounted(loadVisualization)

const radarDimensions = computed(() => vizData.value.radarData.map((r) => r.dimension || ''))
const heatRows = computed(() => vizData.value.heatmapData)

// 近 14 天学习时长折线（秒 → 分钟）
const durationOption = computed(() => {
  const trend = vizData.value.durationTrend
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 42, right: 16, top: 24, bottom: 26 },
    xAxis: {
      type: 'category',
      data: trend.map((t) => t.date.slice(5)),
      axisLabel: { fontSize: 11, color: 'var(--ac-text-tertiary, var(--ink-3))' },
    },
    yAxis: {
      type: 'value',
      name: '分钟',
      nameTextStyle: { fontSize: 11 },
      axisLabel: { fontSize: 11, color: 'var(--ac-text-tertiary, var(--ink-3))' },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trend.map((t) => Math.round((Number(t.seconds) || 0) / 60)),
        lineStyle: { color: 'var(--accent, var(--info))', width: 2.5 },
        itemStyle: { color: 'var(--accent, var(--info))' },
        areaStyle: { opacity: 0.1 },
      },
    ],
  }
})

// 课程薄弱度雷达（未掌握错题率，百分比）
const radarOption = computed(() => ({
  tooltip: { trigger: 'item' },
  radar: {
    indicator: radarDimensions.value.map((d) => ({ name: d, max: 100 })),
    radius: '62%',
    splitNumber: 4,
    axisName: { fontSize: 11, color: 'var(--ac-text-secondary, var(--ink-2))' },
  },
  series: [
    {
      type: 'radar',
      data: [
        {
          value: vizData.value.radarData.map((r) => Number(r.weakRate) || 0),
          name: '薄弱度',
          areaStyle: { opacity: 0.25 },
          lineStyle: { color: 'var(--ac-danger, var(--accent))' },
          itemStyle: { color: 'var(--ac-danger, var(--accent))' },
        },
      ],
    },
  ],
}))

// 课程 × 近 7 天学习活跃热力图
const heatOption = computed(() => {
  const rows = heatRows.value
  const dates = [...new Set(rows.map((r) => r.date))].sort()
  const courses = [
    ...new Map(rows.map((r) => [r.courseId, r.courseName || `课程 #${r.courseId}`])).values(),
  ]
  const idxDate = new Map(dates.map((d, i) => [d, i]))
  const idxCourse = new Map(courses.map((c, i) => [c, i]))
  const data = rows.map((r) => [
    idxCourse.get(r.courseName || `课程 #${r.courseId}`),
    idxDate.get(r.date),
    Math.round((Number(r.seconds) || 0) / 60),
  ])
  return {
    tooltip: {
      formatter: (p) => `${courses[p.value[0]]}<br/>${dates[p.value[1]]}<br/>${p.value[2]} 分钟`,
    },
    grid: { left: 80, right: 16, top: 16, bottom: 30 },
    xAxis: {
      type: 'category',
      data: dates.map((d) => d.slice(5)),
      axisLabel: { fontSize: 11, color: 'var(--ac-text-tertiary, var(--ink-3))' },
      splitArea: { show: true },
    },
    yAxis: {
      type: 'category',
      data: courses,
      axisLabel: { fontSize: 11, color: 'var(--ac-text-tertiary, var(--ink-3))' },
      splitArea: { show: true },
    },
    visualMap: {
      min: 0,
      max: 60,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: { color: ['#eef4fb', '#c9dcf2', '#8fb6e0', '#3d78c4', '#1d4e8f'] },
      textStyle: { fontSize: 10, color: 'var(--ac-text-tertiary, var(--ink-3))' },
    },
    series: [{ type: 'heatmap', data, label: { show: false } }],
  }
})
</script>

<style scoped>
/* 加载失败提示：跨整行显示，弱化为增强项提示（不阻塞驾驶舱主内容） */
.viz-error {
  grid-column: 1 / -1;
  margin: 0;
  padding: 8px 12px;
  border: 1px solid var(--accent-soft, #f3d9d6);
  border-radius: var(--ac-radius-md, 8px);
  background: var(--accent-soft, #fdf3f2);
  color: var(--accent-strong, #b05045);
  font-size: 12.5px;
  line-height: 1.6;
}
</style>
