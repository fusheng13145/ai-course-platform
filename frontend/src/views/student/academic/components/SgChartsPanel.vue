<template>
  <div>
    <!-- 图表区域：雷达图 + 趋势图 -->
    <div class="charts-row">
      <!-- 成绩分布雷达图 -->
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">成绩维度分析</span>
        </div>
        <v-chart
          v-if="!loading && groupedByCourse && Object.keys(groupedByCourse).length >= 3"
          :option="radarOption"
          class="chart-fixed"
          autoresize
        />
        <el-empty
          v-else-if="!loading && (!groupedByCourse || Object.keys(groupedByCourse).length < 3)"
          description="选课不足（至少3门），暂无法生成多维对比分析"
          :image-size="80"
        />
        <div v-else class="chart-loading">
          <span class="muted">加载中...</span>
        </div>
      </div>

      <!-- 成绩趋势折线图 -->
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">历次考试成绩趋势</span>
        </div>
        <v-chart
          v-if="!loading && list.length > 0"
          :option="trendOption"
          class="chart-fixed"
          autoresize
        />
        <el-empty
          v-else-if="!loading && list.length === 0"
          description="暂无考试记录"
          :image-size="80"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getChartTheme, getMonoSeries, monoScore } from '@/utils/chartTheme'

const props = defineProps({
  loading: { type: Boolean, default: false },
  groupedByCourse: { type: Object, default: () => ({}) },
  list: { type: Array, default: () => [] },
})

/* ---- 雷达图配置 ---- */
const radarOption = computed(() => {
  const t = getChartTheme()
  const mono = getMonoSeries()
  // 从各课程平均分构建维度数据
  const dimensions = []
  const values = []

  const sortedCourses = Object.entries(props.groupedByCourse)
    .sort((a, b) => b[1].avgScore - a[1].avgScore)
    .slice(0, 6)

  sortedCourses.forEach(([name, g]) => {
    dimensions.push({ name: name.length > 6 ? name.substring(0, 6) + '..' : name, max: 100 })
    values.push(g.avgScore)
  })

  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: t.tooltipBg,
      borderColor: t.tooltipBorder,
      textStyle: { color: t.tooltipText, fontSize: 13 },
    },
    legend: {
      data: ['我的成绩'],
      bottom: 0,
      textStyle: { fontSize: 12, color: t.subText },
    },
    radar: {
      indicator: dimensions,
      shape: 'polygon',
      splitNumber: 4,
      axisName: {
        color: t.text,
        fontSize: 11,
      },
      splitLine: {
        lineStyle: { color: t.split },
      },
      splitArea: {
        areaStyle: {
          color: [`${mono[0]}0A`, `${mono[0]}14`, `${mono[0]}0A`, `${mono[0]}05`],
        },
      },
      axisLine: {
        lineStyle: { color: t.axis },
      },
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: values,
            name: '我的成绩',
            symbol: 'circle',
            symbolSize: 6,
            lineStyle: {
              width: 2,
              color: mono[0],
            },
            areaStyle: {
              color: {
                type: 'linear',
                x: 0,
                y: 0,
                x2: 0,
                y2: 1,
                colorStops: [
                  { offset: 0, color: `${mono[0]}59` },
                  { offset: 1, color: `${mono[0]}0D` },
                ],
              },
            },
            itemStyle: { color: mono[0] },
          },
        ],
      },
    ],
  }
})

/* ---- 趋势图配置 ---- */
const trendOption = computed(() => {
  const t = getChartTheme()
  const mono = getMonoSeries()
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: t.tooltipBg,
      borderColor: t.tooltipBorder,
      textStyle: { color: t.tooltipText },
    },
    grid: { left: 48, right: 20, bottom: 36, top: 20 },
    xAxis: {
      type: 'category',
      data: props.list.map((_, i) => `第${i + 1}次`),
      axisLine: { lineStyle: { color: t.axis } },
      axisLabel: { color: t.subText, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: t.split, type: 'dashed' } },
      axisLabel: { color: t.subText, fontSize: 11 },
    },
    series: [
      {
        name: '成绩',
        type: 'line',
        data: props.list.map((r) => Number(r.score) || 0),
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { width: 2.5, color: mono[0] },
        itemStyle: { color: mono[0] },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: `${mono[0]}40` },
              { offset: 1, color: `${mono[0]}05` },
            ],
          },
        },
        markLine: {
          silent: true,
          lineStyle: { color: monoScore(60), type: 'dashed', width: 1 },
          data: [{ yAxis: 60, label: { formatter: '及格线', color: t.subText, fontSize: 11 } }],
        },
      },
    ],
  }
})
</script>

<style scoped>
.charts-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}

.chart-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  transition: all var(--ac-transition-normal);
}

.chart-card:hover {
  box-shadow: var(--ac-shadow-md);
}

.chart-header {
  margin-bottom: var(--ac-spacing-md);
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.chart-fixed {
  height: 320px;
}

.chart-loading {
  height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 900px) {
  .charts-row {
    grid-template-columns: 1fr;
  }
}
</style>
