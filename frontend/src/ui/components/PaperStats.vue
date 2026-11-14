<template>
  <div>
    <div class="stats-grid">
      <div class="stats-item">
        <span>题目数</span><b>{{ display(stats?.questionCount) }}</b>
      </div>
      <div class="stats-item">
        <span>卷面总分</span><b>{{ display(stats?.totalPoints) }}</b>
      </div>
      <div class="stats-item">
        <span>状态</span>
        <b>{{ examPaperStatusView(String(paperInfo?.status ?? '')).label }}</b>
      </div>
      <div class="stats-item">
        <span>已阅卷</span><b>{{ display(stats?.submittedCount) }}</b>
      </div>
      <div class="stats-item">
        <span>平均分</span><b>{{ display(stats?.averageScore) }}</b>
      </div>
      <div class="stats-item">
        <span>及格率</span><b>{{ passRateText }}</b>
      </div>
    </div>

    <p class="section-kicker">题型分布</p>
    <div v-if="typeRows.length" class="hbar-list">
      <div v-for="item in typeRows" :key="item.label" class="hbar">
        <span>{{ item.label }}</span>
        <i :style="{ '--p': `${item.percent}%` }"></i>
        <b>{{ item.count }}</b>
      </div>
    </div>
    <p v-else class="modal-hint">暂无题型分布。</p>

    <p class="section-kicker">分值分布</p>
    <div v-if="scoreRows.length" class="hbar-list">
      <div v-for="item in scoreRows" :key="item.label" class="hbar">
        <span>{{ item.label }}</span>
        <i :style="{ '--p': `${item.percent}%` }"></i>
        <b>{{ item.count }}</b>
      </div>
    </div>
    <p v-else class="modal-hint">暂无分值分布。</p>

    <p class="modal-hint">
      成绩聚合只统计已阅卷会话，按百分制；及格线固定 60，无样本时均分与及格率显示「—」。
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { examPaperStatusView } from '@/constants/examPaperStatus'
import { questionTypeLabel } from '@/constants/questionTypes'

const props = defineProps<{ stats: Record<string, unknown> | null }>()

const paperInfo = computed(() => (props.stats?.paperInfo || null) as Record<string, unknown> | null)

function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

function distributionRows(
  value: unknown,
  labelOf: (key: string) => string = (key) => key,
): { label: string; count: number; percent: number }[] {
  const source = value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
  const entries = Object.entries(source).map(([key, raw]) => ({
    label: labelOf(key),
    count: Number(raw) || 0,
  }))
  const max = Math.max(...entries.map((item) => item.count), 1)
  return entries.map((item) => ({ ...item, percent: Math.round((item.count / max) * 100) }))
}

const typeRows = computed(() => distributionRows(props.stats?.typeDistribution, questionTypeLabel))
const scoreRows = computed(() => distributionRows(props.stats?.scoreDistribution))

const passRateText = computed(() => {
  const value = props.stats?.passRate
  return value === null || value === undefined || value === '' ? '—' : `${value}%`
})
</script>

<style scoped>
.section-kicker {
  margin: 16px 0 6px;
  font-family: var(--font-mono);
  font-size: 7.5px;
  letter-spacing: 0.08em;
  color: var(--ink-3);
}
</style>
