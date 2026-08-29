<template>
  <!-- 发起投票 -->
  <el-dialog
    :model-value="modelValue"
    title="发起投票"
    width="480px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="84px">
      <el-form-item label="投票标题" required>
        <el-input v-model="form.title" placeholder="如：最喜欢的教学方式" maxlength="40" />
      </el-form-item>
      <el-form-item label="选项">
        <div class="opt-list">
          <div v-for="(o, i) in form.options" :key="i" class="opt-row">
            <el-input v-model="form.options[i]" placeholder="选项内容" />
            <el-button
              :icon="Delete"
              circle
              plain
              type="danger"
              @click="form.options.splice(i, 1)"
            />
          </div>
          <el-button :icon="Plus" plain @click="form.options.push('')">添加选项</el-button>
        </div>
      </el-form-item>
      <el-form-item label="是否多选">
        <el-switch v-model="form.multiple" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="$emit('create')">发起</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { Delete, Plus } from '@element-plus/icons-vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  form: { type: Object, default: () => ({}) },
})
defineEmits(['update:modelValue', 'create'])
</script>

<style scoped>
.opt-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}
.opt-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.opt-row .el-input {
  flex: 1;
}
</style>
