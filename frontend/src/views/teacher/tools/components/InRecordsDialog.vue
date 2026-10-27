<template>
  <!-- 签到记录 -->
  <Modal
    :model-value="modelValue"
    title="签到记录"
    width="640px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="records-head">
      <span class="rh-title">{{ currentSign?.title }}</span>
      <el-button text type="primary" size="small" @click="$emit('open-mark')">
        <el-icon><EditPen /></el-icon>手动标记
      </el-button>
    </div>
    <el-table
      v-loading="recordLoading"
      :data="recordList"
      size="small"
      max-height="320"
      empty-text="暂无记录"
    >
      <el-table-column prop="studentName" label="学生" min-width="120" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="recordStatusType(row.status)" effect="light">{{
            recordStatusText(row.status)
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="signTime" label="签到时间" min-width="160" />
    </el-table>

    <div v-if="markVisible" class="mark-form">
      <el-divider>手动标记</el-divider>
      <el-form :inline="true" :model="markForm">
        <el-form-item label="学生">
          <el-select v-model="markForm.studentId" placeholder="选择学生" style="width: 160px">
            <el-option
              v-for="r in recordList"
              :key="r.studentId"
              :label="r.studentName"
              :value="r.studentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="markForm.status" style="width: 130px">
            <el-option label="已签到" value="SIGNED" />
            <el-option label="迟到" value="LATE" />
            <el-option label="缺勤" value="ABSENT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="markSubmitting" @click="$emit('mark-submit')"
            >确认</el-button
          >
        </el-form-item>
      </el-form>
    </div>
  </Modal>
</template>

<script setup lang="ts">
import { type PropType } from 'vue'
import { EditPen } from '@element-plus/icons-vue'
import Modal from '@/components/ui/Modal.vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  recordList: {
    type: Array as PropType<{ studentId: number | string; studentName: string }[]>,
    default: () => [],
  },
  recordLoading: { type: Boolean, default: false },
  currentSign: { type: Object, default: null },
  markVisible: { type: Boolean, default: false },
  markForm: { type: Object, default: () => ({}) },
  markSubmitting: { type: Boolean, default: false },
})

// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
defineEmits(['update:modelValue', 'open-mark', 'mark-submit'])

function recordStatusType(s) {
  return s === 'SIGNED' ? 'success' : s === 'LATE' ? 'warning' : s === 'ABSENT' ? 'danger' : 'info'
}
function recordStatusText(s) {
  return s === 'SIGNED' ? '已签到' : s === 'LATE' ? '迟到' : s === 'ABSENT' ? '缺勤' : '未签'
}
</script>

<style scoped>
.records-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.rh-title {
  font-weight: 600;
  color: var(--ac-text-primary);
}
.mark-form {
  margin-top: 4px;
}
</style>
