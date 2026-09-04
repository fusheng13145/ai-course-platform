<template>
  <div>
    <PageHeader title="管理总览" subtitle="平台核心运营指标与常用管理入口" icon="dashboard" />
    <div v-loading="loading">
      <!-- 统计卡片 -->
      <AdStatCards :overview="overview" :submit-rate="submitRate" :excellent-rate="excellentRate" />

      <!-- 系统/教学管理快捷入口 -->
      <AdQuickEntries :overview="overview" />

      <!-- 数据看板 + 图表 + 课程运营 -->
      <AdChartPanels
        :trend-data="trendData"
        :grade-dist="gradeDist"
        :dept-data="deptData"
        :top-courses="topCourses"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import AdStatCards from './components/AdStatCards.vue'
import AdQuickEntries from './components/AdQuickEntries.vue'
import AdChartPanels from './components/AdChartPanels.vue'
import { getMonoSeries } from '@/utils/chartTheme'
import adminApi from '@/api/admin'
import analyticsApi from '@/api/analytics'
import { toast } from '@/composables/useToast'

const overview = ref({})
const dash = ref({})
const trendData = ref([])
const gradeDist = ref([])
const deptData = ref([])
const topCourses = ref([])
const loading = ref(false)

const submitRate = computed(() => {
  const v = dash.value.completionRate
  return v != null ? v + '%' : '—'
})
const excellentRate = computed(() => {
  const g = dash.value.gradeDistribution
  if (!Array.isArray(g) || g.length < 5) return '—'
  const total = g.reduce((s, x) => s + (Number(x) || 0), 0)
  if (!total) return '0'
  return Math.round((((Number(g[0]) || 0) + (Number(g[1]) || 0)) * 100) / total)
})

function fmtDate(d) {
  const [y, m, day] = String(d).split('-')
  return y ? `${m}-${day}` : d
}

async function load() {
  loading.value = true
  try {
    const [ovRes, dashRes, growthRes, courseRes] = await Promise.all([
      adminApi.getOverview(),
      analyticsApi.getDashboardStats(),
      adminApi.getUserGrowth({ days: 30 }),
      adminApi.getCourseStats(),
    ])
    const ov = ovRes?.data?.data || ovRes?.data || {}
    const dc = dashRes?.data?.data || dashRes?.data || {}
    const growth = growthRes?.data?.data || growthRes?.data || []
    const cs = courseRes?.data?.data || courseRes?.data || {}

    overview.value = ov
    dash.value = dc

    const mono = getMonoSeries()
    trendData.value = growth.map((item) => ({
      label: fmtDate(item.date),
      value: item.newUsers ?? 0,
      color: mono[1],
    }))

    const gLabels = ['优秀(90+)', '良好(80-89)', '中等(70-79)', '及格(60-69)', '不及格']
    gradeDist.value = (dc.gradeDistribution || []).slice(0, 5).map((v, i) => ({
      label: gLabels[i] || '分段' + (i + 1),
      value: Number(v) || 0,
      color: mono[i % mono.length],
    }))

    const catMap = cs.categoryDistribution || {}
    deptData.value = Object.entries(catMap).map(([label, value], i) => ({
      label,
      value: Number(value) || 0,
      color: mono[i % mono.length],
    }))

    topCourses.value = cs.topCourses || []
  } catch (e) {
    toast.show('看板数据加载失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
