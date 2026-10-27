<template>
  <!-- AI 生成 对话框 -->
  <Modal
    :model-value="modelValue"
    title="AI 智能生成题目"
    width="520px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
    @closed="$emit('closed')"
  >
    <el-form :model="form" label-width="80px">
      <el-form-item label="类型">
        <el-select v-model="form.type" style="width: 100%">
          <el-option v-for="o in typeOpts" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="难度">
        <el-select v-model="form.difficulty" style="width: 100%">
          <el-option v-for="o in diffOpts" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="知识点">
        <el-input
          :model-value="kpText"
          type="textarea"
          :rows="2"
          placeholder="多个知识点用逗号分隔，可留空"
          @update:model-value="$emit('update:kpText', $event)"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" :icon="MagicStick" @click="$emit('generate')"
        >生成</el-button
      >
    </template>
  </Modal>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import Modal from '@/components/ui/Modal.vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  form: { type: Object, default: () => ({}) },
  kpText: { type: String, default: '' },
  // 选项来自 constants/questionTypes 的 readonly 契约常量（实库值），声明为只读接收
  typeOpts: {
    type: Array as PropType<readonly { value: string; label: string }[]>,
    default: () => [],
  },
  diffOpts: {
    type: Array as PropType<readonly { value: string; label: string }[]>,
    default: () => [],
  },
})
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）

defineEmits(['update:modelValue', 'closed', 'update:kpText', 'generate'])
</script>
