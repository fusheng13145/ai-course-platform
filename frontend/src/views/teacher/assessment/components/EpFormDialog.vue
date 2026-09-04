<template>
  <!-- 新建/编辑试卷对话框 -->
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑试卷' : '新建试卷'"
    width="640px"
    append-to-body
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="所属课程" prop="courseId">
        <el-select
          v-model="form.courseId"
          filterable
          clearable
          placeholder="选择课程"
          style="width: 100%"
        >
          <el-option v-for="c in courseList" :key="c.id" :label="c.name || c.title" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="试卷标题" prop="title">
        <el-input
          v-model="form.title"
          placeholder="请输入试卷标题"
          maxlength="80"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="说明">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="试卷说明（可选）"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="考试时长(分钟)" prop="duration">
            <el-input-number
              v-model="form.duration"
              :min="10"
              :max="300"
              :step="5"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="总分" prop="totalScore">
            <el-input-number
              v-model="form.totalScore"
              :min="1"
              :max="500"
              :step="5"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">{{
        isEdit ? '保存修改' : '创建试卷'
      }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  isEdit: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  form: { type: Object, default: () => ({}) },
  rules: { type: Object, default: () => ({}) },
  courseList: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref(null)

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  emit('submit', formRef.value)
}
</script>
