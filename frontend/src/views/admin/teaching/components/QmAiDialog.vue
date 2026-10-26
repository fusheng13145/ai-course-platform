<template>
  <Modal
    :model-value="modelValue"
    title="AI 智能出题"
    width="650px"
    @update:model-value="emit('update:model-value', $event)"
  >
    <el-alert
      title="AI将根据您提供的信息自动生成高质量题目，支持多种题型和难度级别"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 20px"
    />

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" scroll-to-error>
      <el-form-item label="知识点/主题" prop="topic">
        <el-input v-model="form.topic" placeholder="例如：Python基础语法、机器学习算法等" />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="题型" prop="type">
            <el-select v-model="form.type" style="width: 100%">
              <el-option label="自动混合" value="MIXED" />
              <el-option label="单选题" value="SINGLE_CHOICE" />
              <el-option label="多选题" value="MULTIPLE_CHOICE" />
              <el-option label="填空题" value="FILL_BLANK" />
              <el-option label="简答题" value="SHORT_ANSWER" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="难度" prop="difficulty">
            <el-select v-model="form.difficulty" style="width: 100%">
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
              <el-option label="混合难度" value="MIXED" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="生成数量" prop="count">
        <el-slider v-model="form.count" :min="1" :max="20" :step="1" show-stops />
      </el-form-item>

      <el-form-item label="所属课程">
        <el-select
          v-model="form.courseId"
          filterable
          clearable
          style="width: 100%"
          placeholder="选择关联课程（可选）"
        >
          <el-option
            v-for="course in courseList"
            :key="course.id"
            :label="course.title || course.name"
            :value="course.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="额外要求">
        <el-input
          v-model="form.requirements"
          type="textarea"
          :rows="2"
          placeholder="可选：描述特殊要求，如'需要包含代码示例'、'侧重实际应用场景'等"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:model-value', false)">取消</el-button>
      <el-button type="primary" :loading="generating" @click="emit('generate')">
        <el-icon><MagicStick /></el-icon>开始生成
      </el-button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import { ref } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import Modal from '@/components/ui/Modal.vue'

/**
 * AI 智能出题对话框
 * <p>知识点/主题 + 题型/难度/数量/关联课程/额外要求表单。
 * 表单实例经 defineExpose 暴露 validate 供父组件触发校验（useQuestions.generateByAI）。</p>
 */
defineProps({
  modelValue: { type: Boolean, default: false },
  form: { type: Object, required: true },
  rules: { type: Object, default: () => ({}) },
  courseList: {
    type: Array as PropType<{ id: number | string; name?: string; title?: string }[]>,
    default: () => [],
  },
  generating: { type: Boolean, default: false },
})

const emit = defineEmits(['update:model-value', 'generate'])

const formRef = ref(null)

defineExpose({
  validate: () => formRef.value?.validate(),
})
</script>
