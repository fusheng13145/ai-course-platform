<template>
  <div v-loading="loading" class="content-card">
    <el-table :data="paged" style="width: 100%">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="studentName" label="姓名" min-width="110" />
      <el-table-column prop="studentId" label="学生 ID" width="120" />
      <el-table-column label="已提交/总作业" width="130" align="center">
        <template #default="{ row }">
          <span class="ratio">{{ row.submittedCount }} / {{ row.totalAssignments }}</span>
        </template>
      </el-table-column>
      <el-table-column label="提交率" width="150">
        <template #default="{ row }">
          <div class="completion-cell">
            <el-progress
              :percentage="row.submissionRate"
              :stroke-width="7"
              :show-text="false"
              style="width: 80px"
              :color="getRateColor(row.submissionRate)"
              :status="row.submissionRate === 100 ? 'success' : undefined"
            />
            <span class="completion-text">{{ row.submissionRate }}%</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="平均成绩" width="100" align="center" sortable prop="avgScore">
        <template #default="{ row }">
          <span class="avg-score" :class="{ 'low-score': row.avgScore != null && row.avgScore < 60 }">
            {{ row.avgScore ?? '—' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="最高分" width="80" align="center">
        <template #default="{ row }">{{ row.maxScore }}</template>
      </el-table-column>
      <el-table-column label="最低分" width="80" align="center">
        <template #default="{ row }">{{ row.minScore }}</template>
      </el-table-column>
      <el-table-column label="提交状态" width="130">
        <template #default="{ row }">
          <el-tag
            :type="
              row.submissionRate === 100
                ? 'success'
                : row.submissionRate >= 60
                  ? 'warning'
                  : 'danger'
            "
            size="small"
            effect="plain"
          >
            {{
              row.submissionRate === 100
                ? '全部完成'
                : row.submissionRate >= 60
                  ? '部分完成'
                  : '较多缺失'
            }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
    <!-- D2-5：课程学生提交统计无界（大课程数百人），客户端分页仅超一页时出现 -->
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
    <EmptyState v-if="!loading && data.length === 0" title="暂无学生数据" icon="users" />
  </div>
</template>

<script setup>
import EmptyState from '@/components/ui/EmptyState.vue'
import { useClientPagination } from '@/composables/useClientPagination'

const props = defineProps({
  loading: { type: Boolean, default: false },
  data: { type: Array, default: () => [] },
})

// D2-5：课程学生提交统计按课程人数伸缩（大课程数百行），渲染层客户端分页（每页 20）
const { page, pageSize, total, paged, showPager, onPageChange } = useClientPagination(
  () => props.data,
)

function getRateColor(rate) {
  if (rate >= 90) return 'var(--ok)'
  if (rate >= 60) return 'var(--warn)'
  return 'var(--accent)'
}
</script>

<style scoped>
.content-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 14px;
}
/* D2-5：分页器右对齐 */
.pager-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
.completion-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.completion-text {
  font-size: 12px;
  color: var(--ac-text-secondary);
  min-width: 32px;
}
.avg-score {
  font-weight: 600;
  color: var(--ac-text-primary);
}
.low-score {
  color: var(--ac-danger, var(--accent));
}
.ratio {
  font-size: 13px;
  color: var(--ac-text-primary);
  font-weight: 500;
}
</style>
