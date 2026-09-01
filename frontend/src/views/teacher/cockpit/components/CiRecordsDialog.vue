<template>
  <!-- 签到记录 -->
  <el-dialog
    :model-value="modelValue"
    title="签到记录"
    width="520px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-table :data="records" size="small" border max-height="420" empty-text="暂无记录">
      <el-table-column prop="studentName" label="学生" min-width="120" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.status === 'SIGNED' ? 'success' : 'info'"
            effect="light"
            >{{ row.status === 'SIGNED' ? '已签到' : row.status || '未签' }}</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="时间" min-width="150">
        <template #default="{ row }">{{ row.time || '—' }}</template>
      </el-table-column>
      <el-table-column label="标记" width="100" align="center">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :icon="Check"
            :disabled="row.status === 'SIGNED'"
            @click="$emit('mark', row)"
            >标记到</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { Check } from '@element-plus/icons-vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  records: { type: Array, default: () => [] },
})
defineEmits(['update:modelValue', 'mark'])
</script>
