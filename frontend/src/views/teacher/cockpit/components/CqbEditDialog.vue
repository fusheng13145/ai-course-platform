<template>
  <!-- 新建 / 编辑 对话框 -->
  <Modal
    :model-value="modelValue"
    :title="editing ? '编辑题目' : '新建题目'"
    width="560px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
    @closed="$emit('closed')"
  >
    <el-form :model="form" label-width="80px">
      <el-form-item label="类型">
        <el-select v-model="form.type" style="width: 100%" :disabled="editing">
          <el-option v-for="o in typeOpts" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="难度">
        <el-select v-model="form.difficulty" style="width: 100%">
          <el-option v-for="o in diffOpts" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="题干">
        <el-input v-model="form.content" type="textarea" :rows="3" placeholder="请输入题干" />
      </el-form-item>
      <el-form-item label="答案">
        <el-input
          v-model="form.correctAnswers"
          type="textarea"
          :rows="2"
          placeholder="请输入标准答案"
        />
      </el-form-item>
      <el-form-item label="解析">
        <el-input
          v-model="form.explanation"
          type="textarea"
          :rows="2"
          placeholder="请输入答案解析"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="$emit('save')">保存</el-button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import Modal from '@/components/ui/Modal.vue'
defineProps({
  modelValue: { type: Boolean, default: false },
  editing: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  form: { type: Object, default: () => ({}) },
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

defineEmits(['update:modelValue', 'closed', 'save'])
</script>
