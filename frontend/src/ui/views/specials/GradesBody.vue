<template>
  <section class="grades-body">
    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">ADMIN GRADES / OVERVIEW</p>
          <h2>
            成绩概览（按课程） <span class="chip chip-mute">{{ rows.length }} 门</span>
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
              <th class="w-96">平均总评</th>
              <th class="w-96">预警（&lt;60）</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="String(row.courseId)">
              <td>
                <b>{{ row.courseName }}</b
                ><small class="cell-sub">{{ row.courseCode || '' }}</small>
              </td>
              <td>{{ row.studentCount }}</td>
              <td>{{ row.avgFinalScore != null ? row.avgFinalScore : '—' }}</td>
              <td>
                <span
                  class="chip"
                  :class="Number(row.atRiskCount) > 0 ? 'chip-warn' : 'chip-mute'"
                  >{{ row.atRiskCount }}</span
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载…</b></div>
      <div v-else class="empty-state compact"><b>暂无在册课程</b></div>
    </section>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">ADMIN GRADES / AT RISK</p>
          <h2>学习预警名单</h2>
        </div>
        <div class="filter-row class-tools">
          <select
            v-model="groupBy"
            class="input filter-select"
            aria-label="预警分组维度"
            @change="loadRisk"
          >
            <option value="course">按课程</option>
            <option value="adminClass">按行政班</option>
            <option value="teachingClass">按教学班</option>
          </select>
        </div>
      </header>
      <div v-if="riskRows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>分组</th>
              <th class="w-96">总人数</th>
              <th class="w-96">预警人数</th>
              <th class="w-96">预警率</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in riskRows" :key="String(index)">
              <td>
                <b>{{ row.group }}</b>
              </td>
              <td>{{ row.total }}</td>
              <td>{{ row.atRisk }}</td>
              <td>
                {{ row.total ? Math.round((Number(row.atRisk) * 100) / row.total) + '%' : '—' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载预警…</b></div>
      <div v-else class="empty-state compact"><b>暂无预警数据</b></div>
      <p class="hint">
        预警口径 = 进度 &lt; 40 或 总评 &lt;
        60（与分析侧双口径一致）。改分只在教师批阅流，本页只读。
      </p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import request from '@/api/request'
import { useUi } from '../../composables/ui'
import { pickList, unwrap } from '@/utils/response'

interface SummaryRow {
  courseId?: number | string
  courseName?: string
  courseCode?: string
  studentCount?: number
  avgFinalScore?: number | null
  atRiskCount?: number
}

interface RiskRow {
  group?: string
  total?: number
  atRisk?: number
}

const { toast } = useUi()
const rows = ref<SummaryRow[]>([])
const riskRows = ref<RiskRow[]>([])
const loading = ref(false)
const groupBy = ref('course')

async function load() {
  loading.value = true
  try {
    rows.value = pickList<SummaryRow>(unwrap(await request.get('/analytics/teaching-summary')))
  } catch (error) {
    const err = error as { response?: { data?: { message?: string } } }
    toast(err?.response?.data?.message || '成绩概览加载失败', 'danger')
  } finally {
    loading.value = false
  }
}

async function loadRisk() {
  try {
    riskRows.value = pickList<RiskRow>(
      unwrap(
        await request.get('/analytics/at-risk/students', { params: { groupBy: groupBy.value } }),
      ),
    )
  } catch (error) {
    const err = error as { response?: { data?: { message?: string } } }
    toast(err?.response?.data?.message || '预警数据加载失败', 'danger')
  }
}

onMounted(() => {
  void load()
  void loadRisk()
})
</script>

<style scoped>
.grades-body {
  display: grid;
  gap: 14px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.filter-select {
  width: 140px;
  height: 30px;
}
</style>
