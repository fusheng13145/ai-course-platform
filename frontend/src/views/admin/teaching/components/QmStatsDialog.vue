<template>
  <Modal
    :model-value="modelValue"
    title="题目统计分析"
    width="550px"
    @update:model-value="emit('update:model-value', $event)"
  >
    <div v-if="stats" class="stats-content">
      <div class="stats-grid">
        <div class="stats-item">
          <span class="label">使用次数</span>
          <span class="value primary">{{ stats.usageCount || 0 }}次</span>
        </div>
        <div class="stats-item">
          <span class="label">正确率</span>
          <span class="value success">{{
            stats.correctRate ? (stats.correctRate * 100).toFixed(1) + '%' : '--'
          }}</span>
        </div>
        <div class="stats-item">
          <span class="label">平均用时</span>
          <span class="value warning">{{ stats.avgTime || '--' }}秒</span>
        </div>
        <div class="stats-item">
          <span class="label">错误次数</span>
          <span class="value danger">{{ stats.errorCount || 0 }}次</span>
        </div>
      </div>

      <div v-if="recentUsage.length > 0" class="recent-usage">
        <h4>近期使用记录</h4>
        <el-timeline>
          <el-timeline-item
            v-for="(record, idx) in recentUsage"
            :key="idx"
            :timestamp="record.time || record.date"
            placement="top"
            size="small"
          >
            {{ record.examTitle || record.context }}
          </el-timeline-item>
        </el-timeline>
      </div>
    </div>
  </Modal>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Modal from '@/components/ui/Modal.vue'
/**
 * 题目统计对话框
 * <p>使用次数/正确率/平均用时/错误次数统计卡 + 近期使用记录时间线（纯展示）。</p>
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  stats: { type: Object, default: null },
})

const recentUsage = computed(() => props.stats?.recentUsage?.slice(0, 5) ?? [])

const emit = defineEmits(['update:model-value'])
</script>

<style scoped>
.stats-content {
  padding: 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}

.stats-item {
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  padding: 16px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stats-item .label {
  font-size: 13px;
  color: var(--ac-text-muted);
}

.stats-item .value {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
}

.stats-item .value.primary {
  color: var(--ac-primary);
}
.stats-item .value.success {
  color: var(--ac-success);
}
.stats-item .value.warning {
  color: var(--ac-warning);
}
.stats-item .value.danger {
  color: var(--ac-danger);
}

.recent-usage h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 12px;
}
</style>
