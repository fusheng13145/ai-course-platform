<template>
  <el-dialog
    :model-value="modelValue"
    title="批阅作业"
    width="480px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form v-if="gradingStudent" label-width="80px">
      <el-form-item label="学生">
        <span>{{ gradingStudent.studentName }} ({{ gradingStudent.studentNo }})</span>
      </el-form-item>
      <el-form-item label="任务">
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
      <el-button type="primary" :loading="gradingLoading" @click="$emit('submit')"
        >确认评分</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 批阅作业对话框
 * <p>分数（共享 gradeForm 对象，编辑嵌套属性符合 shallowOnly 规范）+ 评语输入与提交。</p>
 */
defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 当前批阅的学生行 */
  gradingStudent: { type: Object, default: null },
  /** 批阅表单（共享对象引用：score/comment/fullScore） */
  gradeForm: { type: Object, required: true },
  /** 提交加载态 */
  gradingLoading: { type: Boolean, default: false },
  /** 任务名回退显示 */
  currentTaskName: { type: String, default: '' },
})

defineEmits(['update:modelValue', 'submit'])
</script>

<style scoped>
.w-100 {
  width: 100%;
}
</style>
