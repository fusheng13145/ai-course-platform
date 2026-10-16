<template>
  <!-- 发起签到 -->
  <el-dialog
    ref="dialogRef"
    :model-value="modelValue"
    title="发起签到"
    width="440px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="84px">
      <el-form-item label="签到标题" required>
        <el-input v-model="form.title" placeholder="如：第3章课堂签到" maxlength="40" />
      </el-form-item>
      <el-form-item label="时长(分钟)">
        <el-input-number v-model="form.duration" :min="1" :max="120" :step="5" />
      </el-form-item>
      <el-form-item label="签到方式">
        <el-select v-model="form.type" placeholder="选择方式" style="width: 100%">
          <el-option label="普通签到" value="NORMAL" />
          <el-option label="二维码签到" value="QR" />
          <el-option label="手势签到" value="GESTURE" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="$emit('create')">发起</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDialogFocus } from '@/composables/useDialogFocus'
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  form: { type: Object, default: () => ({}) },
})
const dialogRef = ref(null)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
useDialogFocus(dialogRef, computed(() => props.modelValue))

defineEmits(['update:modelValue', 'create'])
</script>
