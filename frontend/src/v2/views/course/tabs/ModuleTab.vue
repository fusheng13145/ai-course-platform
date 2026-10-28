<template>
  <div v-if="block">
    <div v-if="block.metrics.length" class="stat-grid kpi-grid">
      <V2Stat v-for="metric in block.metrics" :key="metric.label" :metric="metric" :role="role" />
    </div>
    <div class="module-grid">
      <V2Table v-if="block.table" :table="block.table" />
      <aside v-if="block.side" class="card side-card">
        <p class="card-kicker">INSIGHT / 提示</p>
        <h2>{{ block.side.title }}</h2>
        <p class="side-body">{{ block.side.body }}</p>
        <ul class="side-list">
          <li v-for="item in block.side.items" :key="item">{{ item }}</li>
        </ul>
        <p class="caveat">示例数据 · 该页签的后端端点尚未接入</p>
      </aside>
    </div>
    <p v-if="note" class="hint">{{ note }}</p>
  </div>
  <div v-else class="card empty-state">
    <span class="e-ico"><V2Icon name="layers" /></span>
    <b>该页签尚未接入</b>
    <p>{{ note || '后端聚合端点尚未实现；旧版对应页面仍可继续使用。' }}</p>
  </div>
</template>

<script setup lang="ts">
import V2Stat from '../../../components/V2Stat.vue'
import V2Table from '../../../components/V2Table.vue'
import V2Icon from '../../../components/V2Icon.vue'
import type { V2DemoBlock } from '../../../data/pageData'
import type { V2Role } from '../../../nav'

defineProps<{ block: V2DemoBlock | null; role: V2Role; note?: string }>()
</script>
