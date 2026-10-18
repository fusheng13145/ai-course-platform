<template>
  <el-dialog
    ref="dialogRef"
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

    <!-- 缺评维度不进分母，必须让学生看得见依据 -->
    <el-alert
      v-for="(w, i) in data?.warnings || []"
      :key="'w' + i"
      class="warn-line"
      :title="w"
      type="warning"
      :closable="false"
      show-icon
    />

    <el-table v-if="data?.components?.length" :data="data.components" size="small" border>
      <el-table-column prop="name" label="构成项" min-width="90" />
      <el-table-column label="权重" width="72" align="center">
        <template #default="{ row }">{{ row.weight }}%</template>
      </el-table-column>
      <el-table-column label="得分" width="72" align="center">
        <template #default="{ row }">
          <span v-if="row.graded">{{ row.score }}</span>
          <el-tag v-else size="small" type="info" effect="plain">未评分</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="contribution" label="计入总评" width="86" align="center" />
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useDialogFocus } from '@/composables/useDialogFocus'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'
import gradeApi from '@/api/grade'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  policyId: { type: [Number, String], default: null },
  student: { type: Object, default: () => ({}) },
})

const dialogRef = ref(null)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
useDialogFocus(
  dialogRef,
  computed(() => props.modelValue),
)
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
    // 后端出参只有分数语义，姓名仍从入口行数据补齐
    data.value = body
      ? { studentName: props.student.studentName, ...body }
      : { studentName: props.student.studentName, finalScore: props.student.finalScore }
  } catch (e) {
    logger.error('计算总评失败', e)
    data.value = {
      studentName: props.student?.studentName,
      finalScore: props.student?.finalScore,
    }
  }
}
</script>

<style scoped>
.warn-line {
  margin-top: 8px;
}
</style>
