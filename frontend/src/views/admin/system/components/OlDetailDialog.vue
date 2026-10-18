<template>
  <el-dialog
    ref="dialogRef"
    :model-value="modelValue"
    title="操作日志详情"
    width="560px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-descriptions v-if="log" :column="1" border>
      <el-descriptions-item label="日志 ID">{{ log.id }}</el-descriptions-item>
      <el-descriptions-item label="操作人">{{
        log.username || log.operatorName || '—'
      }}</el-descriptions-item>
      <el-descriptions-item label="模块">{{ moduleLabel(log.module) }}</el-descriptions-item>
      <el-descriptions-item label="操作类型">{{ actionLabel(log.action) }}</el-descriptions-item>
      <el-descriptions-item label="描述">{{
        log.description || log.detail || '—'
      }}</el-descriptions-item>
      <el-descriptions-item label="请求方法">{{ log.method || '—' }}</el-descriptions-item>
      <el-descriptions-item label="请求路径">{{
        log.requestUri || log.uri || '—'
      }}</el-descriptions-item>
      <el-descriptions-item label="IP 地址">{{
        log.ip || log.ipAddress || '—'
      }}</el-descriptions-item>
      <el-descriptions-item label="结果">
        <span
          class="status-dot"
          :class="isSuccess(log) ? 'status-dot--active' : 'status-dot--fail'"
        >
          <i></i>{{ isSuccess(log) ? '成功' : '失败' }}
        </span>
      </el-descriptions-item>
      <el-descriptions-item label="耗时">{{
        log.costTime != null ? log.costTime + ' ms' : '—'
      }}</el-descriptions-item>
      <el-descriptions-item label="时间">{{
        formatDateTime(log.createdAt || log.createTime, '—')
      }}</el-descriptions-item>
    </el-descriptions>
    <template #footer
      ><el-button type="primary" @click="$emit('update:modelValue', false)"
        >关闭</el-button
      ></template
    >
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDialogFocus } from '@/composables/useDialogFocus'
import { formatDateTime } from '@/utils/datetime'
import { useOperationLogs } from '@/composables/useOperationLogs'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  log: { type: Object, default: null },
})

const dialogRef = ref(null)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
useDialogFocus(
  dialogRef,
  computed(() => props.modelValue),
)
defineEmits(['update:modelValue'])

const { moduleLabel, actionLabel, isSuccess } = useOperationLogs()
</script>

<style scoped>
.status-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  padding: 2px 10px;
  border-radius: 20px;
  line-height: 20px;
}
.status-dot i {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot--active {
  color: var(--ok);
  background: rgba(22, 163, 74, 0.08);
}
.status-dot--active i {
  background: var(--ok);
}
.status-dot--fail {
  color: var(--accent-strong);
  background: rgba(176, 80, 69, 0.08);
}
.status-dot--fail i {
  background: var(--accent);
}
</style>
