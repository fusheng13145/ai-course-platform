<template>
  <el-dialog
    :model-value="modelValue"
    title="创建查重任务"
    width="480px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form
      ref="createFormRef"
      :model="createForm"
      :rules="createRules"
      label-width="100px"
      label-position="top"
      scroll-to-error
    >
      <el-form-item label="作业 ID" prop="assignmentId">
        <el-input v-model="createForm.assignmentId" placeholder="请输入作业 ID" clearable />
      </el-form-item>
      <p class="form-tip">请输入需要查重的作业 ID，系统将基于该作业的全部提交进行相似度比对。</p>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="creating" @click="handleCreate"> 确认创建 </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import plagiarismApi from '@/api/plagiarismCheck'

defineProps({
  modelValue: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'created'])

const createFormRef = ref(null)
const creating = ref(false)
const createForm = reactive({
  assignmentId: '',
})
const createRules = {
  assignmentId: [{ required: true, message: '请输入作业 ID', trigger: 'blur' }],
}

async function handleCreate() {
  if (!createFormRef.value) return
  try {
    await createFormRef.value.validate()
  } catch {
    return
  }
  creating.value = true
  try {
    await plagiarismApi.createCheck({ assignmentId: createForm.assignmentId })
    ElMessage.success('查重任务创建成功')
    emit('update:modelValue', false)
    createForm.assignmentId = ''
    emit('created')
  } catch (error) {
    console.error('创建查重任务失败:', error)
    ElMessage.error('创建失败，请稍后重试')
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: -8px;
  line-height: 1.5;
}
</style>
