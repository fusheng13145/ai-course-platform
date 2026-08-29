<template>
  <!-- 新建/编辑对话框 -->
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑知识点' : '新建知识点'"
    width="520px"
    append-to-body
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="名称" prop="name"
        ><el-input v-model="form.name" placeholder="输入名称" maxlength="50" show-word-limit
      /></el-form-item>
      <el-form-item label="难度" prop="difficulty">
        <el-select v-model="form.difficulty" style="width: 100%"
          ><el-option label="简单" value="EASY" /><el-option
            label="中等"
            value="MEDIUM" /><el-option label="困难" value="HARD"
        /></el-select>
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12"
          ><el-form-item label="重要度"
            ><el-rate v-model="form.importance" :max="3" /><br /><span
              style="font-size: 11px; color: var(--ac-text-secondary)"
              >{{ ['低', '中', '高'][form.importance - 1] || '中' }}</span
            ></el-form-item
          ></el-col
        >
        <el-col :span="12"
          ><el-form-item label="父级节点">
            <el-tree-select
              v-model="form.parentId"
              :data="flatTreeOptions"
              :props="{ label: 'name', value: 'id', children: 'children' }"
              check-strictly
              clearable
              placeholder="可选"
              style="width: 100%"
              filterable
            /> </el-form-item
        ></el-col>
      </el-row>
      <el-form-item label="描述"
        ><el-input v-model="form.description" type="textarea" :rows="2" maxlength="200"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">{{
        isEdit ? '保存' : '创建'
      }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  isEdit: { type: Boolean, default: false },
  form: { type: Object, default: () => ({}) },
  rules: { type: Object, default: () => ({}) },
  submitting: { type: Boolean, default: false },
  flatTreeOptions: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref(null)

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate((valid) => {
    if (valid) emit('submit')
  })
}
</script>
