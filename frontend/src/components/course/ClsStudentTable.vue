<template>
  <div class="table-section">
    <div class="section-header">
      <h3 class="section-title">学生学习详情</h3>
      <el-input
        :model-value="searchKeyword"
        placeholder="搜索学生姓名"
        clearable
        style="width: 200px"
        size="small"
        @update:model-value="$emit('update:searchKeyword', $event)"
      />
    </div>
    <div v-loading="loading" class="content-card">
      <el-table :data="paged" style="width: 100%">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="studentName" label="姓名" min-width="110" />
        <el-table-column prop="studentId" label="学号" width="120" />
        <el-table-column label="在线时长" width="110" sortable prop="onlineHours">
          <template #default="{ row }">
            <span class="metric-value">{{
              row.onlineHours != null ? row.onlineHours + 'h' : '-'
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="参与度" width="130" sortable prop="engagement">
          <template #default="{ row }">
            <div v-if="row.engagement != null" class="engagement-cell">
              <el-progress
                :percentage="row.engagement"
                :stroke-width="6"
                :show-text="false"
                style="width: 70px"
                :color="getEngagementColor(row.engagement)"
              />
              <span class="engagement-text">{{ row.engagement }}%</span>
            </div>
            <span v-else class="metric-value">-</span>
          </template>
        </el-table-column>
        <el-table-column label="学习完成率" width="140" sortable prop="completionRate">
          <template #default="{ row }">
            <div v-if="row.completionRate != null" class="engagement-cell">
              <el-progress
                :percentage="Math.round(row.completionRate * 100)"
                :stroke-width="6"
                :show-text="false"
                style="width: 70px"
              />
              <span class="engagement-text">{{ Math.round(row.completionRate * 100) }}%</span>
            </div>
            <span v-else class="metric-value">-</span>
          </template>
        </el-table-column>
        <el-table-column
          label="提交作业数"
          width="110"
          align="center"
          sortable
          prop="submissionCount"
        >
          <template #default="{ row }">
            <span class="metric-value">{{ row.submissionCount ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近活跃" width="140" sortable prop="lastActiveTime">
          <template #default="{ row }">
            <span class="time-text">{{ formatRelative(row.lastActiveTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row)" size="small" effect="plain">
              {{ getStatusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <!-- D2-5：课堂学生学情无界（大课程数百人），客户端分页仅超一页时出现 -->
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
      <EmptyState
        v-if="!loading && filteredStudents.length === 0"
        :title="emptyText"
        icon="users"
      />
    </div>
  </div>
</template>

<script setup>
import EmptyState from '@/components/ui/EmptyState.vue'
import { useClientPagination } from '@/composables/useClientPagination'

const props = defineProps({
  loading: { type: Boolean, default: false },
  searchKeyword: { type: String, default: '' },
  filteredStudents: { type: Array, default: () => [] },
  emptyText: { type: String, default: '暂无学生数据' },
})
defineEmits(['update:searchKeyword'])

// D2-5：课堂学生学情按课程人数伸缩（大课程数百行），渲染层客户端分页（每页 20）
const { page, pageSize, total, paged, showPager, onPageChange } = useClientPagination(
  () => props.filteredStudents,
)

function getEngagementColor(val) {
  if (val >= 80) return 'var(--ok)'
  if (val >= 50) return 'var(--warn)'
  return 'var(--accent)'
}

function getStatusTag(row) {
  const eng = row.engagement
  if (eng == null) return 'info'
  if (eng >= 70) return 'success'
  if (eng >= 40) return 'warning'
  return 'danger'
}

function getStatusLabel(row) {
  const eng = row.engagement
  if (eng == null) return '-'
  if (eng >= 70) return '活跃'
  if (eng >= 40) return '一般'
  return '预警'
}

function formatRelative(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now - d
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days} 天前`
  return d.toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.table-section {
  margin-bottom: 10px;
}
/* D2-5：分页器右对齐 */
.pager-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}

.engagement-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.engagement-text {
  font-size: 12px;
  color: var(--ac-text-secondary);
  min-width: 32px;
}
.metric-value {
  font-weight: 600;
  color: var(--ac-text-primary);
}
.time-text {
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}
</style>
