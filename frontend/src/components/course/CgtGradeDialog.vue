<template>
  <el-dialog
    ref="dialogRef"
    :model-value="modelValue"
    title="批阅作业"
    width="480px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form v-if="gradingStudent" label-width="80px">
      <el-form-item label="学生">
        <span>{{ gradingStudent.studentName }} ({{ gradingStudent.studentNo }})</span>
      </el-form-item>
      <el-form-item v-if="showTask" label="任务">
        <span>{{ gradingStudent.taskName || currentTaskName }}</span>
      </el-form-item>
      <el-form-item label="分数" required>
        <el-input-number
          v-model="gradeForm.score"
          :min="0"
          :max="gradeForm.fullScore || 100"
          class="w-100"
        />
        / {{ gradeForm.fullScore || 100 }}
      </el-form-item>
      <el-form-item label="评语">
        <el-input
          v-model="gradeForm.comment"
          type="textarea"
          :rows="3"
          placeholder="输入评语（可选）"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="gradingLoading" @click="$emit('submit')">
        确认评分
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDialogFocus } from '@/composables/useDialogFocus'
/**
 * 批阅作业对话框（成绩域共享实现）
 * <p>教师成绩页与学生课程成绩页共用；角色差异以 props 表达：
 * `showTask`（是否显示「任务」行）、`currentTaskName`（任务名回退）。</p>
 */
const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 当前批阅的学生行 */
  gradingStudent: { type: Object, default: null },
  /** 批阅表单（共享对象引用：score / comment / fullScore） */
  gradeForm: { type: Object, default: () => ({}) },
  /** 提交加载态 */
  gradingLoading: { type: Boolean, default: false },
  /** 是否显示「任务」行（教师成绩页为 true） */
  showTask: { type: Boolean, default: false },
  /** 任务名回退显示 */
  currentTaskName: { type: String, default: '' },
})
const dialogRef = ref(null)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
useDialogFocus(dialogRef, computed(() => props.modelValue))


defineEmits(['update:modelValue', 'submit'])
</script>

<style scoped>
.w-100 {
  width: 100%;
}
</style>
