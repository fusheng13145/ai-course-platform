<template>
  <div>
    <div class="analytics-toolbar">
      <div class="tabs" role="tablist" aria-label="时间范围">
        <button :class="{ 'is-active': range === 'midterm' }" @click="range = 'midterm'">
          本次期中
        </button>
        <button :class="{ 'is-active': range === 'term' }" @click="range = 'term'">学期累计</button>
      </div>
      <span class="compare-chip">对比上次 +3.1 分</span>
    </div>
    <div class="module-grid">
      <section class="card analytics-card">
        <header class="card-head">
          <div>
            <p class="card-kicker">DISTRIBUTION / MIDTERM</p>
            <h2>分数段分布</h2>
          </div>
          <span class="chart-unit">人</span>
        </header>
        <div class="bar-chart">
          <div v-for="(value, index) in distribution" :key="index" class="bar-col">
            <span class="bar-val">{{ value }}</span>
            <i class="bar" :style="{ '--v': `${Math.round((value / max) * 100)}%` }"></i>
            <span>{{ labels[index] }}</span>
          </div>
        </div>
        <div class="card-foot"><span>均分 82.4</span><span>8 人低于 60 分</span></div>
      </section>
      <section class="card analytics-card">
        <header class="card-head">
          <div>
            <p class="card-kicker">MASTERY / WEAK SPOTS</p>
            <h2>班级薄弱点</h2>
          </div>
        </header>
        <ul class="weak-list">
          <li v-for="item in weak" :key="item.label" class="weak-item" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <b>{{ item.value }}%</b>
            <span class="weak-bar"><i :style="{ '--p': `${item.value}%` }"></i></span>
          </li>
        </ul>
      </section>
    </div>
    <div class="module-grid" style="margin-top: 14px">
      <section class="card">
        <header class="card-head">
          <div>
            <p class="card-kicker">SIGNAL / AI</p>
            <h2>可执行建议</h2>
          </div>
        </header>
        <ol class="insight-steps">
          <li><b>先补概念</b><span>拥塞控制掌握度 42%，建议用 2 分钟讲解 + 5 道变式题。</span></li>
          <li><b>再练变式</b><span>子网划分错误集中在掩码借位，安排 10 道同类题。</span></li>
          <li><b>最后回访</b><span>对 8 名低于 60 分的学生发起一对一提醒。</span></li>
        </ol>
      </section>
      <aside class="card side-card">
        <p class="card-kicker">INSIGHT / 提示</p>
        <h2>统计口径</h2>
        <p class="side-body">分数段与薄弱点来自已批阅成绩；AI 建议分数不计入统计。</p>
        <ul class="side-list">
          <li>样本不足时不生成班级结论</li>
          <li>薄弱点阈值 60%</li>
          <li>课程级聚合，可切教学班</li>
        </ul>
        <p class="caveat">示例数据 · 学情聚合端点已存在，本版未接入</p>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const range = ref<'midterm' | 'term'>('midterm')
const distribution = computed(() =>
  range.value === 'midterm' ? [4, 9, 18, 21, 8] : [12, 24, 41, 52, 19],
)
const labels = ['<60', '60–69', '70–79', '80–89', '90+']
const max = computed(() => Math.max(...distribution.value, 1))
const weak = [
  { label: '拥塞控制', value: 42, tone: 'danger' },
  { label: '子网划分', value: 58, tone: 'warn' },
  { label: '路由算法', value: 61, tone: 'warn' },
  { label: '三次握手', value: 73, tone: 'ok' },
]
</script>
