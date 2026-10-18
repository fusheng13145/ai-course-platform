<template>
  <div v-loading="loading" class="content-card">
    <el-table :data="chapterList" style="width: 100%" row-key="id" default-expand-all>
      <el-table-column prop="title" label="章节名称" min-width="220">
        <template #default="{ row }">
          <div class="chapter-name-cell">
            <el-icon
              :size="16"
              :color="row.isPublished ? 'var(--ac-success)' : 'var(--ac-text-tertiary)'"
            >
              <component :is="row.isPublished ? 'CircleCheckFilled' : 'CircleClose'" />
            </el-icon>
            <span>{{ row.title }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.isPublished ? 'success' : 'info'" size="small">
            {{ row.isPublished ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="任务点数" width="90" align="center">
        <template #default="{ row }">{{ row.taskCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="提交数" width="90" align="center">
        <template #default="{ row }">{{ row.submittedCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="完成率" width="150">
        <template #default="{ row }">
          <div class="completion-cell">
            <el-progress
              :percentage="Math.round((row.completionRate ?? 0) * 100)"
              :stroke-width="6"
              :show-text="false"
              style="width: 90px"
            />
            <span class="completion-text">{{ Math.round((row.completionRate ?? 0) * 100) }}%</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="时长(分)" width="80" align="center">
        <template #default="{ row }">{{ row.duration || 0 }}</template>
      </el-table-column>
    </el-table>
    <EmptyState v-if="!loading && chapterList.length === 0" :title="emptyText" icon="list" />
  </div>
</template>

<script setup>
import EmptyState from '@/components/ui/EmptyState.vue'

defineProps({
  loading: { type: Boolean, default: false },
  chapterList: { type: Array, default: () => [] },
  emptyText: { type: String, default: '暂无章节数据' },
})
</script>

<style scoped>
.content-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 14px;
}
.chapter-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.completion-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.completion-text {
  font-size: 12px;
  color: var(--ac-text-secondary);
}
</style>
