<template>
  <div v-if="aiInsight" class="l1-card ai-card">
    <div class="l1-head">
      <span class="l1-ico"><el-icon><MagicStick /></el-icon></span>
      <div><div class="l1-title">AI 学情解读</div></div>
    </div>
    <div class="l1-body">
      <p class="ai-summary">{{ aiInsight.summary }}</p>
      <ul v-if="aiInsight.items && aiInsight.items.length" class="ai-items">
        <li v-for="(it, i) in aiInsight.items" :key="i">
          <strong>{{ it.title }}</strong><span>{{ it.detail }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { MagicStick } from '@element-plus/icons-vue'

defineProps({
  aiInsight: { type: Object, default: null },
})
</script>

<style scoped>
.ai-card {
  margin-bottom: var(--ac-spacing-md);
}
.ai-summary {
  margin: 0 0 10px;
  color: var(--ac-text-secondary);
  line-height: 1.6;
  font-size: 13.5px;
}
.ai-items {
  margin: 0;
  padding-left: 18px;
  color: var(--ac-text-secondary);
  font-size: 13px;
  line-height: 1.9;
}
.ai-items strong {
  color: var(--ac-text-primary);
  margin-right: 8px;
}
</style>
