<template>
  <div class="table-area">
    <el-table
      v-loading="loading"
      :data="filteredGrades"
      style="width: 100%"
      :header-cell-style="{
        background: 'var(--ac-bg)',
        color: 'var(--ac-text-secondary)',
        fontWeight: 600,
      }"
      size="default"
      @row-click="$emit('view-detail', $event)"
    >
      <el-table-column prop="studentNo" label="学号" width="100" />
      <el-table-column prop="studentName" label="姓名" width="85">
        <template #default="{ row }"
          ><span style="font-weight: 500">{{ row.studentName }}</span></template
        >
      </el-table-column>
      <el-table-column prop="homeworkScore" label="作业均分" width="90" align="center">
        <template #default="{ row }">
          <span
            v-if="row.homeworkScore != null"
            :style="{ color: getScoreColor(row.homeworkScore) }"
            >{{ row.homeworkScore }}</span
          >
          <span v-else style="color: var(--ac-text-muted)">--</span>
        </template>
      </el-table-column>
      <el-table-column prop="examScore" label="考试均分" width="90" align="center">
        <template #default="{ row }">
          <span v-if="row.examScore != null" :style="{ color: getScoreColor(row.examScore) }">{{
            row.examScore
          }}</span>
          <span v-else style="color: var(--ac-text-muted)">--</span>
        </template>
      </el-table-column>
      <el-table-column prop="totalScore" label="总评" width="70" align="center">
        <template #default="{ row }">
          <span
            v-if="row.totalScore != null"
            style="font-weight: 600; color: var(--ac-text-primary)"
            >{{ row.totalScore }}</span
          >
          <span v-else style="color: var(--ac-text-muted)">--</span>
        </template>
      </el-table-column>
      <el-table-column prop="gradeLevel" label="等级" width="60" align="center">
        <template #default="{ row }"
          ><el-tag v-if="row.gradeLevel" :type="getGradeTagType(row.gradeLevel)" size="small">{{
            row.gradeLevel
          }}</el-tag
          ><span v-else>--</span></template
        >
      </el-table-column>
      <el-table-column label="操作" width="84" align="center">
        <template #default="{ row }">
          <div class="action-row">
            <el-button text type="primary" size="small" @click.stop="$emit('view-detail', row)"
              >详情</el-button
            >
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && filteredGrades.length === 0" description="暂无成绩数据" />
  </div>
</template>

<script setup>
import { monoScore } from '@/utils/chartTheme'

defineProps({
  loading: { type: Boolean, default: false },
  filteredGrades: { type: Array, default: () => [] },
})
defineEmits(['view-detail'])

function getGradeTagType(l) {
  const m = { A: 'success', B: 'primary', C: 'warning', D: 'danger', F: 'info' }
  return m[l] || 'info'
}
function getScoreColor(score) {
  return monoScore(score)
}
</script>

<style scoped>
.table-area {
  min-width: 0;
}
.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  flex-wrap: nowrap;
}
</style>
