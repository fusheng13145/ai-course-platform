<template>
  <!-- 投票结果 -->
  <el-dialog
    :model-value="modelValue"
    title="投票结果"
    width="520px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="result" class="result-wrap">
      <div class="result-title">{{ result.title }}</div>
      <div v-for="(item, i) in result.options || []" :key="i" class="result-item">
        <div class="ri-head">
          <span>{{ item.optionText || item.text }}</span>
          <span class="ri-num">{{ item.count || 0 }} 票</span>
        </div>
        <div class="ri-bar">
          <div class="ri-fill" :style="{ width: resultPct(item.count) }"></div>
        </div>
      </div>
      <div class="result-total">总票数：{{ result.total || 0 }}</div>
    </div>
    <el-empty v-else description="暂无结果" :image-size="60" />
  </el-dialog>
</template>

<script setup>
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  result: { type: Object, default: null },
})
defineEmits(['update:modelValue'])

function resultPct(count) {
  const total = props.result?.total || 0
  if (!total) return '0%'
  return Math.min(100, Math.round(((Number(count) || 0) / total) * 100)) + '%'
}
</script>

<style scoped>
.result-wrap {
  padding: 4px 0;
}
.result-title {
  font-size: 15px;
  font-weight: 620;
  color: var(--ac-text-primary);
  margin-bottom: 14px;
}
.result-item {
  margin-bottom: 14px;
}
.ri-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--ac-text-secondary);
  margin-bottom: 6px;
}
.ri-num {
  color: var(--ac-primary);
  font-weight: 600;
}
.ri-bar {
  height: 10px;
  border-radius: 999px;
  background: var(--ac-surface-2);
  overflow: hidden;
}
.ri-fill {
  height: 100%;
  background: linear-gradient(135deg, var(--ac-primary), var(--ac-primary-light));
  border-radius: 999px;
  transition: width 0.4s ease;
}
.result-total {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--ac-text-muted);
}
</style>
