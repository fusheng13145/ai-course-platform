<template>
  <el-dialog
    ref="dialogRef"
    :model-value="modelValue"
    title="题目审核"
    width="520px"
    append-to-body
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form label-position="top">
      <el-form-item label="审核结果">
        <el-radio-group v-model="reviewForm.status">
          <el-radio value="APPROVED">通过</el-radio>
          <el-radio value="REJECTED">驳回</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="审核意见">
        <el-input
          v-model="reviewForm.comment"
          type="textarea"
          :rows="3"
          placeholder="可选：填写审核意见"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="reviewing" @click="$emit('submit')">提交审核</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDialogFocus } from '@/composables/useDialogFocus'
/**
 * 题库管理 — 题目审核对话框
 * <p>通过/驳回单选 + 审核意见，提交逻辑委托 composable。</p>
 */
const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 审核表单（共享对象：status/comment） */
  reviewForm: { type: Object, required: true },
  /** 提交加载态 */
  reviewing: { type: Boolean, default: false },
})
const dialogRef = ref(null)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
useDialogFocus(dialogRef, computed(() => props.modelValue))


defineEmits(['update:modelValue', 'submit'])
</script>
