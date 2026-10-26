<template>
  <Modal
   
    :model-value="modelValue"
    title="AI 智能出题"
    width="600px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-alert
      title="AI 将依据课程、题型、难度与知识点自动生成题目"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 18px"
    />
    <el-form label-position="top">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="所属课程">
            <el-select
              v-model="aiForm.courseId"
              filterable
              clearable
              placeholder="选择课程（可选）"
              style="width: 100%"
            >
              <el-option
                v-for="c in courseList"
                :key="c.id"
                :label="c.name || c.title"
                :value="c.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="题型">
            <el-select v-model="aiForm.type" style="width: 100%">
              <el-option label="单选题" value="SINGLE_CHOICE" />
              <el-option label="多选题" value="MULTIPLE_CHOICE" />
              <el-option label="判断题" value="TRUE_FALSE" />
              <el-option label="填空题" value="FILL_BLANK" />
              <el-option label="简答题" value="SHORT_ANSWER" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="难度">
            <el-select v-model="aiForm.difficulty" style="width: 100%">
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="知识点">
            <el-select
              v-model="aiForm.knowledgePoints"
              multiple
              filterable
              allow-create
              default-first-option
              placeholder="输入知识点后回车"
              style="width: 100%"
            ></el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="aiGenerating" @click="$emit('generate')"
        ><el-icon><MagicStick /></el-icon>开始生成</el-button
      >
    </template>
  </Modal>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import Modal from '@/components/ui/Modal.vue'

/**
 * 题库管理 — AI 智能出题对话框
 * <p>课程/题型/难度/知识点（可自定义）配置，生成逻辑委托 composable。</p>
 */
defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** AI 表单（共享对象：courseId/type/difficulty/knowledgePoints） */
  aiForm: { type: Object, required: true },
  /** 课程列表 */
  courseList: {
    type: Array as PropType<{ id: number | string; name?: string; title?: string }[]>,
    required: true,
  },
  /** 生成加载态 */
  aiGenerating: { type: Boolean, default: false },
})
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）

defineEmits(['update:modelValue', 'generate'])
</script>
