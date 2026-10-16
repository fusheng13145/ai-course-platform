<template>
  <div class="table-area">
    <el-table
      v-loading="loading"
      :data="paged"
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
    <!-- D2-5：课程成绩无界（大课程数百行），客户端分页仅超一页时出现 -->
    <div v-if="showPager" class="pager-wrap">
      <el-pagination
        small
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>
    <EmptyState v-if="!loading && filteredGrades.length === 0" title="暂无成绩数据" icon="chart" />
  </div>
</template>

<script setup>
import { monoScore } from '@/utils/chartTheme'
import EmptyState from '@/components/ui/EmptyState.vue'
import { useClientPagination } from '@/composables/useClientPagination'

const props = defineProps({
  loading: { type: Boolean, default: false },
  filteredGrades: { type: Array, default: () => [] },
})
defineEmits(['view-detail'])

// D2-5：课程成绩按课程人数伸缩（大课程数百行），渲染层客户端分页（每页 20）
const { page, pageSize, total, paged, showPager, onPageChange } = useClientPagination(
  () => props.filteredGrades,
)

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
/* D2-5：分页器右对齐 */
.pager-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  flex-wrap: nowrap;
}
</style>
