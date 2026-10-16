<template>
  <el-dialog
    ref="dialogRef"
    :model-value="modelValue"
    title="录入成绩"
    width="420px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-descriptions v-if="target.studentName" :column="1" border size="small">
      <el-descriptions-item label="学生">{{ target.studentName }}</el-descriptions-item>
      <el-descriptions-item label="构成项">{{ target.componentName }}</el-descriptions-item>
    </el-descriptions>
    <el-form label-width="80px" class="form-top">
      <el-form-item label="分数" required>
        <el-input-number
          v-model="localScore"
          :min="0"
          :max="100"
          :precision="1"
          :step="1"
          class="w-100"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveScore">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'
import gradeApi from '@/api/grade'
import { useDialogFocus } from '@/composables/useDialogFocus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  target: { type: Object, default: () => ({}) },
  policyId: { type: [Number, String], default: null },
})
const dialogRef = ref(null)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
useDialogFocus(dialogRef, computed(() => props.modelValue))

const emit = defineEmits(['update:modelValue', 'saved'])

const saving = ref(false)
const localScore = ref(0)

watch(
  () => [props.target?.studentId, props.target?.componentKey, props.target?.score],
  () => {
    localScore.value = props.target?.score ?? 0
  },
  { immediate: true },
)

async function saveScore() {
  if (!props.policyId) return
  saving.value = true
  try {
    const payload = {
      policyId: props.policyId,
      studentId: props.target.studentId,
      componentKey: props.target.componentKey,
      score: localScore.value,
    }
    const r = await gradeApi.saveGradeItem(payload)
    unwrap(r)
    ElMessage.success('成绩已保存')
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) {
    logger.error('保存成绩项失败', e)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form-top {
  margin-top: 16px;
}
.w-100 {
  width: 100%;
}
</style>
