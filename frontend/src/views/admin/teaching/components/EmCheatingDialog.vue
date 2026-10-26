<template>
  <Modal
    :model-value="modelValue"
    title="作弊检测记录"
    width="700px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="cheatingLogs.length > 0">
      <div v-for="(log, idx) in cheatingLogs" :key="idx" class="cheating-item">
        <div class="cheating-header">
          <el-tag type="danger" size="small" effect="dark">异常</el-tag>
          <span class="student-name">{{ log.studentName || log.studentId }}</span>
          <span class="cheating-time">{{ log.detectedAt || log.time }}</span>
        </div>
        <div class="cheating-content">
          <p><strong>异常类型：</strong>{{ log.type || '未知' }}</p>
          <p><strong>详细描述：</strong>{{ log.description || log.detail }}</p>
          <p>
            <strong>风险等级：</strong>
            <el-rate
              :model-value="getRiskLevel(log.riskLevel)"
              disabled
              :colors="['#8BA888', '#CBB58A', '#C48080']"
            />
          </p>
        </div>
      </div>
    </div>
    <EmptyState v-else title="暂无作弊检测记录" icon="alert" />
  </Modal>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import Modal from '@/components/ui/Modal.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

/**
 * 作弊检测日志对话框
 * <p>异常记录列表（学生/时间/类型/风险等级）。</p>
 */
defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 作弊检测日志列表（studentName/studentId/detectedAt|time/type/description|detail/riskLevel） */
  cheatingLogs: {
    type: Array as PropType<
      {
        studentName?: string
        studentId?: number | string
        detectedAt?: string
        time?: string
        type?: string
        description?: string
        detail?: string
        riskLevel?: string
      }[]
    >,
    required: true,
  },
  /** 风险等级 → 评分映射函数 */
  getRiskLevel: { type: Function, required: true },
})

defineEmits(['update:modelValue'])
</script>

<style scoped>
.cheating-item {
  padding: 16px;
  margin-bottom: 12px;
  background: var(--ac-bg);
  border-radius: var(--ac-radius-md);
  border-left: 3px solid var(--ac-danger);
}

.cheating-item:last-child {
  margin-bottom: 0;
}

.cheating-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.student-name {
  font-weight: 600;
  color: var(--ac-text-primary);
}

.cheating-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--ac-text-muted);
}

.cheating-content p {
  font-size: 13px;
  color: var(--ac-text-secondary);
  margin-bottom: 4px;
  line-height: 1.6;
}

.cheating-content p:last-child {
  margin-bottom: 0;
}
</style>
