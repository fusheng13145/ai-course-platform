<template>
  <!-- 详情弹窗 -->
  <el-dialog
    :model-value="modelValue"
    title="学生成绩详情"
    width="560px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template v-if="currentStudent">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="学号">{{
          currentStudent.studentNo || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{
          currentStudent.studentName || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="总评">
          <span style="font-weight: 700; font-size: 18px; color: var(--ac-primary)">{{
            currentStudent.totalScore || '--'
          }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-table
        :data="currentStudent.details || []"
        size="small"
        style="width: 100%; margin-top: 16px"
      >
        <el-table-column prop="taskName" label="任务/考试" />
        <el-table-column prop="type" label="类型" width="80" align="center">
          <template #default="{ row }"
            ><el-tag size="small" :type="row.type === 'exam' ? 'primary' : 'info'">{{
              row.type === 'exam' ? '考试' : '作业'
            }}</el-tag></template
          >
        </el-table-column>
        <el-table-column prop="score" label="得分" width="70" align="center" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }"
            ><el-tag size="small" :type="row.status === 'graded' ? 'success' : 'warning'">{{
              row.status === 'graded' ? '已批阅' : '待批阅'
            }}</el-tag></template
          >
        </el-table-column>
      </el-table>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  currentStudent: { type: Object, default: null },
})
defineEmits(['update:modelValue'])
</script>
