<template>
  <!-- 统计对话框 -->
  <!-- v-loading 不能绑在 <el-dialog> 上：弹层内容在 teleport 的非元素根上，运行时指令无处安放
       （Vue 告警 + 遮罩静默失效）。改由插槽内的包裹层承担，同 CourseProctor.vue:88 的写法。 -->
  <el-dialog
    :model-value="modelValue"
    title="试卷统计"
    width="560px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="stats-body">
      <template v-if="stats">
        <div class="stats-grid">
          <div class="stats-item">
            <span class="label">参考人数</span
            ><span class="value primary">{{
              stats.attemptCount ?? stats.participantCount ?? '—'
            }}</span>
          </div>
          <div class="stats-item">
            <span class="label">平均分</span
            ><span class="value success">{{ stats.avgScore ?? '—' }}</span>
          </div>
          <div class="stats-item">
            <span class="label">最高分</span
            ><span class="value warning">{{ stats.maxScore ?? '—' }}</span>
          </div>
          <div class="stats-item">
            <span class="label">最低分</span
            ><span class="value danger">{{ stats.minScore ?? '—' }}</span>
          </div>
          <div class="stats-item">
            <span class="label">及格率</span
            ><span class="value">{{ formatRate(stats.passRate) }}</span>
          </div>
          <div class="stats-item">
            <span class="label">提交数</span
            ><span class="value">{{ stats.submitCount ?? '—' }}</span>
          </div>
        </div>
        <el-empty v-if="!hasStats(stats)" description="暂无统计数据" />
      </template>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
defineProps({
  modelValue: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  stats: { type: Object, default: null },
})
defineEmits(['update:modelValue'])

function hasStats(s) {
  return (
    s &&
    (s.attemptCount != null ||
      s.avgScore != null ||
      s.maxScore != null ||
      s.minScore != null ||
      s.passRate != null)
  )
}
function formatRate(v) {
  if (v == null) return '—'
  return typeof v === 'number' && v <= 1.0001 && v > 0 ? Math.round(v * 100) + '%' : v + '%'
}
</script>

<style scoped>
/* stats 尚未到达时插槽是空的，遮罩需要一个高度才看得到加载态 */
.stats-body {
  min-height: 160px;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
.stats-item {
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: var(--r-l3);
  padding: 16px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.stats-item .label {
  font-size: 13px;
  color: var(--ink-3);
}
.stats-item .value {
  font-size: 22px;
  font-weight: 700;
  color: var(--ink);
}
.stats-item .value.primary {
  color: var(--accent);
}
.stats-item .value.success {
  color: var(--ok);
}
.stats-item .value.warning {
  color: var(--warn);
}
.stats-item .value.danger {
  color: var(--accent);
}
</style>
