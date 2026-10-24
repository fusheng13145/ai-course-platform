<template>
  <div class="course-overview">
    <PageHeader compact title="课程总览" subtitle="教学数据、学习进度与风险学生一览" icon="grid" />
    <!-- 加载失败提示：非空即表示下方读数不可信（失败不得渲染成「暂无风险学生」） -->
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: var(--ac-spacing-md)"
    />
    <div v-loading="loading">
      <!-- 概览 + 内容与学习指标 -->
      <CoMetricsCards :ov="ov" :ct="ct" :lr="lr" :grading="grading" />

      <!-- 趋势 + 风险学生 -->
      <CoTrendRisk
        :trend-option="trendOption"
        :at-risk="atRisk"
        :empty-text="loadError ? '风险学生未能加载，请查看上方提示后重试' : '暂无风险学生'"
      />

      <!-- AI 学情解读 -->
      <CoAiInsight :ai-insight="aiInsight" />

      <!-- 章节树（含任务点） -->
      <CoChapterTree
        :chapter-tree="chapterTree"
        :empty-text="loadError ? '章节未能加载，请查看上方提示后重试' : '暂无章节'"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import teachingApi from '@/api/teacher'
import { getChartTheme, getMonoSeries } from '@/utils/chartTheme'
import PageHeader from '@/components/PageHeader.vue'
import CoMetricsCards from './components/CoMetricsCards.vue'
import CoTrendRisk from './components/CoTrendRisk.vue'
import CoAiInsight from './components/CoAiInsight.vue'
import CoChapterTree from './components/CoChapterTree.vue'

const route = useRoute()
const courseId = route.params.id

const loading = ref(false)
/**
 * 加载失败提示文案：非空即表示课程总览读数不可信。
 * 原实现 getCourseDashboard 失败仅 logger.error 后静默 → 各子块照常显示「暂无风险学生 / 暂无章节」，
 * 教师会误判该课程确实没有数据（失败可见）。
 */
const loadError = ref('')
const ov = ref({})
const ct = ref({})
const lr = ref({})
const grading = ref({})
const trend = ref([])
const atRisk = ref([])
const chapterTree = ref([])
const aiInsight = ref(null)

// 近 14 天趋势（主题化配置，跟随明暗主题）
function buildTrendOption() {
  const t = getChartTheme()
  const mono = getMonoSeries()
  const n = parseInt(mono[0].slice(1), 16)
  const areaColor = `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, 0.12)`
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: t.tooltipBg,
      borderColor: t.tooltipBorder,
      textStyle: { color: t.tooltipText },
    },
    legend: { data: ['活跃学生', '提交数'], textStyle: { color: t.subText } },
    grid: { left: 44, right: 20, bottom: 28, top: 36 },
    xAxis: {
      type: 'category',
      data: [],
      axisLabel: { color: t.subText, fontSize: 11 },
      axisLine: { lineStyle: { color: t.axis } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: t.subText, fontSize: 11 },
      splitLine: { lineStyle: { color: t.split, type: 'dashed' } },
    },
    series: [
      {
        name: '活跃学生',
        type: 'line',
        smooth: true,
        symbolSize: 5,
        itemStyle: { color: mono[0] },
        lineStyle: { width: 2.5, color: mono[0] },
        areaStyle: { color: areaColor },
        data: [],
      },
      {
        name: '提交数',
        type: 'bar',
        itemStyle: { color: mono[1] || mono[0], borderRadius: [4, 4, 0, 0] },
        data: [],
      },
    ],
  }
}
const trendOption = ref(buildTrendOption())

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await teachingApi.getCourseDashboard(courseId)
    const d = unwrap(res) || {}
    ov.value = d.overview || {}
    ct.value = d.content || {}
    lr.value = (d.learning || {}) as typeof lr.value
    grading.value = (d.grading || {}) as typeof grading.value
    trend.value = (d.trend || []) as typeof trend.value
    atRisk.value = (d.atRiskStudents || []) as typeof atRisk.value
    chapterTree.value = (d.chapterTree || []) as typeof chapterTree.value
    aiInsight.value = d.aiInsight || null

    trendOption.value.xAxis.data = trend.value.map((t) => t.date)
    trendOption.value.series[0].data = trend.value.map((t) => t.activeStudents)
    trendOption.value.series[1].data = trend.value.map((t) => t.submissions)
  } catch (e) {
    logger.error('加载课程驾驶舱失败', e)
    ov.value = {}
    ct.value = {}
    lr.value = {}
    grading.value = {}
    trend.value = []
    atRisk.value = []
    chapterTree.value = []
    aiInsight.value = null
    loadError.value = '课程总览加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<style scoped>
.course-overview {
  padding: 4px 0;
}
</style>
