<template>
  <!-- 发起投票 -->
  <el-dialog
    :model-value="modelValue"
    title="发起投票"
    width="520px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="80px">
      <el-form-item label="投票标题" required>
        <el-input
          v-model="form.title"
          placeholder="如：你最感兴趣的方向"
          maxlength="50"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="选项" required>
        <div v-for="(opt, i) in form.options" :key="i" class="opt-row">
          <el-input v-model="form.options[i]" placeholder="选项内容" style="flex: 1" />
          <el-button
            text
            type="danger"
            :disabled="form.options.length <= 2"
            @click="form.options.splice(i, 1)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <el-button text type="primary" @click="form.options.push('')">
          <el-icon><Plus /></el-icon>添加选项
        </el-button>
      </el-form-item>
      <el-form-item label="多选">
        <el-switch v-model="form.multiple" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="$emit('submit')">发起</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { Delete, Plus } from '@element-plus/icons-vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  form: { type: Object, default: () => ({}) },
})
defineEmits(['update:modelValue', 'submit'])
</script>

<style scoped>
.opt-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
