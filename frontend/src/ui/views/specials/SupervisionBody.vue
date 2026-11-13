<template>
  <section class="supervision-body">
    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>
    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>
            课程运行总览 <span class="chip chip-mute">{{ rows.length }} 门</span>
          </h2>
        </div>
        <div class="filter-row class-tools">
          <button class="btn btn-sm" :disabled="loading" @click="load">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>
      <div v-if="rows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>课程</th>
              <th class="w-96">在学</th>
              <th class="w-96">平均进度</th>
              <th class="w-96">进行中考试</th>
              <th class="w-96">待批阅</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="String(row.courseId)">
              <td>
                <b>{{ row.courseName }}</b
                ><small class="cell-sub">{{ row.courseCode || '' }}</small>
              </td>
              <td>{{ row.studentCount }}</td>
              <td>{{ row.avgProgress != null ? row.avgProgress + '%' : '—' }}</td>
              <td>
                <span
                  class="chip"
                  :class="Number(row.activeExams) > 0 ? 'chip-warn' : 'chip-mute'"
                  >{{ row.activeExams }}</span
                >
              </td>
              <td>
                <span
                  class="chip"
                  :class="Number(row.pendingGrading) > 0 ? 'chip-warn' : 'chip-mute'"
                  >{{ row.pendingGrading }}</span
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载…</b></div>
      <div v-else class="empty-state compact"><b>暂无在册课程</b></div>
      <p class="hint">
        进行中考试 = STARTED 会话；待批阅 = SUBMITTED
        未出分的提交。逐课治理动作在「考试治理」与「选课与名单」。
      </p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Stat from '../../components/Stat.vue'
import { useUi } from '../../composables/ui'
import request from '@/api/request'
import { pickList, unwrap } from '@/utils/response'
import type { Metric } from '../../nav'

interface SummaryRow {
  courseId?: number | string
  courseName?: string
  courseCode?: string
  studentCount?: number
  avgProgress?: number | null
  activeExams?: number
  pendingGrading?: number
}

const { toast } = useUi()
const rows = ref<SummaryRow[]>([])
const loading = ref(false)

const metrics = computed<Metric[]>(() => [
  { label: '在册课程', value: rows.value.length, unit: '门', note: '在用课程', icon: 'building' },
  {
    label: '在学总人次',
    value: rows.value.reduce((sum, row) => sum + Number(row.studentCount || 0), 0),
    unit: '人',
    note: '在读学生',
    icon: 'users',
    tone: 'ok',
  },
  {
    label: '进行中考试',
    value: rows.value.reduce((sum, row) => sum + Number(row.activeExams || 0), 0),
    unit: '场',
    note: '开考中',
    icon: 'chart',
    tone: 'warn',
  },
  {
    label: '待批阅提交',
    value: rows.value.reduce((sum, row) => sum + Number(row.pendingGrading || 0), 0),
    unit: '份',
    note: '待批阅',
    icon: 'layers',
    tone: 'info',
  },
])

async function load() {
  loading.value = true
  try {
    rows.value = pickList<SummaryRow>(unwrap(await request.get('/analytics/teaching-summary')))
  } catch (error) {
    const err = error as { response?: { data?: { message?: string } } }
    toast(err?.response?.data?.message || '教学总览加载失败', 'danger')
  } finally {
    loading.value = false
  }
}

onMounted(() => void load())
</script>

<style scoped>
.supervision-body {
  display: grid;
  gap: 14px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
</style>
