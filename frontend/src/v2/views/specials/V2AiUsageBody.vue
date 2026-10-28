<template>
  <div>
    <div class="stat-grid kpi-grid">
      <V2Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <div class="analytics-toolbar">
      <div class="tabs" role="tablist" aria-label="时间范围">
        <button :class="{ 'is-active': range === '7' }" @click="range = '7'">近 7 天</button>
        <button :class="{ 'is-active': range === '30' }" @click="range = '30'">近 30 天</button>
      </div>
      <span class="compare-chip">对比上一周期 +12.6%</span>
    </div>

    <div class="module-grid">
      <section class="card aiusage-card">
        <header class="card-head">
          <div>
            <p class="card-kicker">AI CALLS / BY COURSE</p>
            <h2>课程调用分布</h2>
          </div>
          <span class="chip chip-warn">配额 68%</span>
        </header>
        <div class="hbar-list">
          <div v-for="row in rows" :key="row.name" class="hbar">
            <span>{{ row.name }}</span>
            <i :style="{ '--p': `${Math.round((row.calls / maxCalls) * 100)}%` }"></i>
            <b>{{ row.calls.toLocaleString('zh-CN') }}</b>
          </div>
        </div>
        <div class="card-foot"><span>今日 8,420 次</span><span>较昨日 +12.6%</span></div>
      </section>

      <aside class="card quota-card">
        <p class="card-kicker">QUOTA / WEEK</p>
        <h2>本周配额</h2>
        <div class="quota-ring">
          <svg viewBox="0 0 120 120">
            <circle cx="60" cy="60" r="50" class="q-track" />
            <circle cx="60" cy="60" r="50" class="q-fill" pathLength="100" style="--p: 68" />
          </svg>
          <b>68<small>%</small></b>
        </div>
        <p class="quota-note">预计周末达到 84%。超配额将限制教师出题，不影响学生问答。</p>
        <button class="btn btn-ghost btn-block" @click="notReady">调整教师额度</button>
        <p class="caveat">历史调用不可回填 · 成本仅展示，不做计费</p>
      </aside>
    </div>

    <section class="card table-card" style="margin-top: 14px">
      <header class="card-head">
        <div>
          <p class="card-kicker">USAGE / DETAIL</p>
          <h2>按课程拆解</h2>
        </div>
        <span class="chip chip-mute">{{ rows.length }} 条记录</span>
      </header>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>课程</th>
              <th>今日调用</th>
              <th>占比</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.name">
              <td>{{ row.name }}</td>
              <td>{{ row.calls.toLocaleString('zh-CN') }}</td>
              <td>{{ row.share }}%</td>
              <td>
                <span class="chip" :class="row.warn ? 'chip-warn' : 'chip-ok'">{{
                  row.warn ? '接近阈值' : '正常'
                }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="hint">
        示例数据：ai_call_logs 跨课程聚合端点尚未接入本页；旧版 /admin/ai-usage 可用。
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import V2Stat from '../../components/V2Stat.vue'
import { useV2Ui } from '../../composables/ui'
import type { V2Metric } from '../../nav'

const { toast } = useV2Ui()
const range = ref<'7' | '30'>('7')

const rows = [
  { name: '计算机网络', calls: 3240, share: 38, warn: false },
  { name: '数据结构与算法', calls: 2110, share: 25, warn: false },
  { name: '操作系统', calls: 1480, share: 18, warn: false },
  { name: '数据库原理与应用', calls: 1010, share: 12, warn: true },
  { name: '其他 8 门课程', calls: 580, share: 7, warn: false },
]

const maxCalls = Math.max(...rows.map((row) => row.calls), 1)
const metrics = computed<V2Metric[]>(() => [
  { label: 'AI 调用', value: 8420, unit: '次', note: '今日', icon: 'sparkle', tone: 'info' },
  { label: '配额', value: 68, unit: '%', note: '本周', icon: 'chart', tone: 'warn' },
  { label: '课程', value: rows.length, unit: '门', note: '有调用', icon: 'book' },
])

function notReady() {
  toast('演示数据：配额调整尚未接入', 'warn')
}
</script>
