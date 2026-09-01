<template>
  <el-dialog
    :model-value="modelValue"
    title="发起讨论"
    width="560px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form :model="topicForm" label-width="80px">
      <el-form-item label="标题" required>
        <el-input
          v-model="topicForm.title"
          placeholder="话题标题"
          maxlength="80"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="内容" required>
        <el-input
          v-model="topicForm.content"
          type="textarea"
          :rows="4"
          placeholder="想讨论什么？"
          maxlength="1000"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="createSubmitting" @click="$emit('submit')">发布</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 发起话题对话框
 * <p>标题（80 字）+ 内容（1000 字）表单，发布逻辑委托 composable。</p>
 */
defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 话题表单（共享对象：title/content） */
  topicForm: { type: Object, required: true },
  /** 发布加载态 */
  createSubmitting: { type: Boolean, default: false },
})

defineEmits(['update:modelValue', 'submit'])
</script>
