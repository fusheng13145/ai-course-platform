<template>
  <el-dialog
    :model-value="modelValue"
    :title="editing ? '编辑班级' : '新建班级'"
    width="520px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" scroll-to-error>
      <el-form-item label="班级名称" prop="name"
        ><el-input v-model="form.name" placeholder="如：计算机2201班"
      /></el-form-item>
      <el-form-item label="班级编码" prop="code"
        ><el-input v-model="form.code" placeholder="如：CS2201"
      /></el-form-item>
      <el-form-item label="专业" prop="major"
        ><el-input v-model="form.major" placeholder="如：计算机科学与技术"
      /></el-form-item>
      <el-form-item label="年级" prop="gradeLevel"
        ><el-input v-model="form.gradeLevel" placeholder="如：2022"
      /></el-form-item>
      <el-form-item label="最大人数"
        ><el-input-number v-model="form.maxStudents" :min="0" :max="500" style="width: 100%"
      /></el-form-item>
      <el-form-item label="班级简介"
        ><el-input v-model="form.description" type="textarea" :rows="2"
      /></el-form-item>
      <el-form-item label="状态"
        ><el-switch v-model="form.isActive" active-text="启用" inactive-text="停用"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="emit('save')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

/**
 * 班级新建/编辑对话框
 * <p>表单对象与校验规则由父组件（useClassrooms）注入（共享 reactive 对象编辑嵌套属性）；
 * 保存动作经 save 事件上抛；表单校验能力经 defineExpose 暴露给父组件调用。</p>
 */
defineProps({
  /** 对话框可见性（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 编辑态（true=编辑 / false=新建） */
  editing: { type: Boolean, default: false },
  /** 表单对象（name/code/major/gradeLevel/maxStudents/description/isActive） */
  form: { type: Object, required: true },
  /** 校验规则 */
  rules: { type: Object, default: () => ({}) },
  /** 保存中状态 */
  saving: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'save'])

// 暴露表单校验能力：父组件经 ref 绑定实例后调用（useClassrooms.saveClassroom）
const formRef = ref(null)
defineExpose({
  validate: (cb) => formRef.value?.validate(cb),
})
</script>
