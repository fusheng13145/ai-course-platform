<template>
  <!-- AI提取对话框 -->
  <el-dialog
    :model-value="modelValue"
    title="AI提取知识点"
    width="560px"
    append-to-body
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form>
      <el-form-item label="输入文本"
        ><el-input
          :model-value="aiText"
          type="textarea"
          :rows="6"
          placeholder="课程描述、教学大纲等..."
          maxlength="3000"
          show-word-limit
          @update:model-value="$emit('update:aiText', $event)"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="aiExtracting" @click="$emit('extract')"
        >开始提取</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  aiText: { type: String, default: '' },
  aiExtracting: { type: Boolean, default: false },
})
defineEmits(['update:modelValue', 'update:aiText', 'extract'])
</script>
