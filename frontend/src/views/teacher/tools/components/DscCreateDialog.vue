<template>
  <!-- 发布话题 -->
  <el-dialog
    :model-value="modelValue"
    title="发布话题"
    width="560px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="80px">
      <el-form-item label="章节" prop="chapterId">
        <el-input v-model="form.chapterId" placeholder="可选，填写章节标识" style="width: 100%" />
      </el-form-item>
      <el-form-item label="标题" required>
        <el-input v-model="form.title" placeholder="话题标题" maxlength="80" show-word-limit />
      </el-form-item>
      <el-form-item label="内容" required>
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="4"
          placeholder="话题内容"
          maxlength="1000"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="$emit('submit')">发布</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
defineProps({
  modelValue: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  form: { type: Object, default: () => ({}) },
})
defineEmits(['update:modelValue', 'submit'])
</script>
