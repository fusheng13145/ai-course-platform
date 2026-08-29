<template>
  <!-- 批阅弹窗 -->
  <el-dialog
    :model-value="modelValue"
    title="批阅作业"
    width="440px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form v-if="gradingStudent" label-width="72px">
      <el-form-item label="学生"
        ><span
          >{{ gradingStudent.studentName }} ({{ gradingStudent.studentNo }})</span
        ></el-form-item
      >
      <el-form-item label="分数">
        <el-input-number
          v-model="gradeForm.score"
          :min="0"
          :max="gradeForm.fullScore || 100"
          style="width: 100%"
        />
        / {{ gradeForm.fullScore || 100 }}
      </el-form-item>
      <el-form-item label="评语"
        ><el-input v-model="gradeForm.comment" type="textarea" :rows="2" placeholder="可选"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="gradingLoading" @click="$emit('submit')"
        >确认评分</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  gradingStudent: { type: Object, default: null },
  gradingLoading: { type: Boolean, default: false },
  gradeForm: { type: Object, default: () => ({}) },
})
defineEmits(['update:modelValue', 'submit'])
</script>
