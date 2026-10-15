<template>
  <!-- 添加/编辑对话框 -->
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑权限' : '添加权限'"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" scroll-to-error>
      <el-form-item label="角色" prop="role">
        <el-select v-model="form.role" placeholder="请选择角色" style="width: 100%">
          <el-option label="管理员 (ADMIN)" value="ADMIN" />
          <el-option label="教师 (TEACHER)" value="TEACHER" />
          <el-option label="学生 (STUDENT)" value="STUDENT" />
        </el-select>
      </el-form-item>
      <el-form-item label="菜单权限 Key" prop="menuKey">
        <el-input v-model="form.menuKey" placeholder="如：course-manage、exam-list" />
      </el-form-item>
      <el-form-item label="按钮权限 Key" prop="buttonKey">
        <el-input v-model="form.buttonKey" placeholder="如：create、edit、delete（可选）" />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="权限描述（可选）"
        />
      </el-form-item>
      <el-form-item label="是否启用">
        <el-switch v-model="form.isActive" active-text="启用" inactive-text="停用" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  isEdit: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  form: { type: Object, default: () => ({}) },
  rules: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref(null)

// 打开对话框时清空历史校验状态
watch(
  () => props.modelValue,
  (v) => {
    if (v) formRef.value?.clearValidate()
  },
)

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate((valid) => {
    if (valid) emit('submit')
  })
}
</script>
