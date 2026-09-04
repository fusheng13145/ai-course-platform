<template>
  <el-dialog
    :model-value="modelValue"
    title="总评预览"
    width="420px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-descriptions v-if="data" :column="1" border size="small">
      <el-descriptions-item label="学生">{{ data.studentName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="总评分数">{{ data.finalScore ?? '—' }}</el-descriptions-item>
      <el-descriptions-item v-if="data.detail" label="明细">{{ data.detail }}</el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'
import gradeApi from '@/api/grade'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  policyId: { type: [Number, String], default: null },
  student: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['update:modelValue'])

const data = ref(null)

watch(
  () => props.modelValue,
  (open) => {
    if (open && props.policyId && props.student?.studentId) computeFinal()
  },
)

async function computeFinal() {
  try {
    const r = await gradeApi.computeFinal(props.policyId, props.student.studentId)
    const body = unwrap(r)
    data.value = body || {
      studentName: props.student.studentName,
      finalScore: props.student.finalScore,
    }
  } catch (e) {
    logger.error('计算总评失败', e)
    data.value = {
      studentName: props.student?.studentName,
      finalScore: props.student?.finalScore,
    }
  }
}
</script>
