<template>
  <!-- 投票结果 -->
  <el-dialog
    :model-value="modelValue"
    title="投票结果"
    width="480px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="result" class="result-wrap">
      <div class="result-total">总票数：{{ result.total ?? 0 }}</div>
      <div v-for="(o, i) in result.options" :key="i" class="result-item">
        <div class="result-head">
          <span>{{ o.text }}</span
          ><b>{{ o.count }} 票</b>
        </div>
        <el-progress
          :percentage="pctOf(o.count, result.total)"
          :stroke-width="12"
          :show-text="false"
        />
      </div>
    </div>
    <el-empty v-else description="暂无结果" />
  </el-dialog>
</template>

<script setup lang="ts">
defineProps({
  modelValue: { type: Boolean, default: false },
  result: { type: Object, default: null },
})
defineEmits(['update:modelValue'])

function pctOf(v, total) {
  const t = Number(total) || 0
  if (!t) return 0
  return Math.round(((Number(v) || 0) / t) * 100)
}
</script>

<style scoped>
.result-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.result-total {
  font-size: 13px;
  color: var(--ac-text-secondary);
}
.result-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--ac-text-primary);
}
.result-head b {
  color: var(--ac-primary);
}
</style>
