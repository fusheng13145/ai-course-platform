<template>
  <div>
    <div class="grades-layout">
      <section class="card grade-summary">
        <p class="card-kicker">ACADEMIC ASSETS / 2026 FALL</p>
        <h2>{{ role === 'student' ? '加权总评' : '课程成绩概览' }}</h2>
        <div class="grade-total"><b>82.4</b><span>/ 100</span><em class="trend-up">+3.1</em></div>
        <p class="grade-sub">课程均分 85.4 · 绩点 3.6 / 4.0 · 专业前 18%</p>
        <div class="grade-trend">
          <V2Sparkline :values="[74, 77, 79, 81, 82.4]" :width="260" :height="54" />
        </div>
        <div class="grade-foot"><span>较上学期 +3.1</span><span>2 项待出分</span></div>
      </section>
      <section class="card grade-breakdown">
        <header class="card-head">
          <div>
            <p class="card-kicker">GPA / DISTRIBUTION</p>
            <h2>绩点构成</h2>
          </div>
          <span class="chip chip-ok">稳定</span>
        </header>
        <div class="grade-ring-wrap">
          <svg viewBox="0 0 120 120" class="grade-ring">
            <circle cx="60" cy="60" r="50" class="q-track" />
            <circle cx="60" cy="60" r="50" class="q-fill" pathLength="100" style="--p: 90" />
          </svg>
          <div class="grade-ring-label"><b>3.6</b><small>/ 4.0</small></div>
        </div>
        <div class="grade-legend">
          <span><i class="dot ok"></i>专业核心 62%</span>
          <span><i class="dot info"></i>通识 24%</span>
          <span><i class="dot warn"></i>实践 14%</span>
        </div>
      </section>
    </div>
    <div class="module-grid" style="margin-top: 14px">
      <V2Table :table="table" kicker="COURSES / GRADES" />
      <aside class="card side-card">
        <p class="card-kicker">INSIGHT / 提示</p>
        <h2>成绩口径</h2>
        <p class="side-body">成绩以教师提交批阅为准；AI 建议分不直接写入成绩。</p>
        <ul class="side-list">
          <li>总评由教师确认后生效</li>
          <li>过程性成绩在课程空间维护</li>
          <li>导出前脱敏</li>
        </ul>
        <p class="caveat">示例数据 · 成绩聚合端点尚未接入</p>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import V2Table from '../../../components/V2Table.vue'
import V2Sparkline from '../../../components/V2Sparkline.vue'
import { findModule } from '../../../data/modules'
import type { V2Role, V2Table as TableType } from '../../../nav'

const props = defineProps<{ role: V2Role }>()

const table = computed<TableType>(() => {
  const module = findModule(props.role === 'teacher' ? 'teacher' : props.role, 'grades')
  return (
    module?.table || {
      title: '课程成绩',
      columns: ['课程', '成绩', '绩点', '状态'],
      rows: [
        ['计算机网络', '88', '3.7', { text: '已出分', chip: 'ok' }],
        ['数据结构与算法', '92', '4.0', { text: '已出分', chip: 'ok' }],
        ['操作系统', '79', '3.0', { text: '已出分', chip: 'info' }],
        ['数据库原理与应用', '85', '3.5', { text: '已出分', chip: 'ok' }],
        ['人工智能导论', '—', '—', { text: '待出分', chip: 'warn' }],
      ],
    }
  )
})
</script>
