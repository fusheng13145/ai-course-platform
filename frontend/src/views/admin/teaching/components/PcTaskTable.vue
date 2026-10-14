<template>
  <section class="l1-card">
    <div class="l1-head">
      <span class="l1-ico"
        ><svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /></svg
      ></span>
      <div>
        <h3 class="l1-title">查重任务与匹配</h3>
      </div>
    </div>
    <div class="l1-body">
      <div class="section-header">
        <h3 class="section-title">查重任务</h3>
      </div>

      <div class="content-card">
        <el-table v-loading="loading" :data="taskList" style="width: 100%" stripe>
          <el-table-column prop="id" label="任务ID" width="90" />
          <el-table-column
            prop="assignmentTitle"
            label="作业名称"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small" effect="light">
                {{ statusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="totalSubmissions" label="提交数" width="80" align="center" />
          <el-table-column prop="matchCount" label="匹配数" width="80" align="center" />
          <el-table-column prop="avgSimilarity" label="平均相似度" width="120" align="center">
            <template #default="{ row }">
              <el-progress
                :percentage="Math.round((row.avgSimilarity ?? 0) * 100)"
                :color="similarityColor(row.avgSimilarity)"
                :stroke-width="14"
                :text-inside="true"
              />
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button
                text
                type="warning"
                size="small"
                :disabled="row.status === 'RUNNING'"
                @click="emit('execute', row)"
              >
                执行查重
              </el-button>
              <el-button text type="primary" size="small" @click="emit('view-detail', row)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="emptyText">
              <template #image><div class="empty-deco">🔍</div></template>
            </el-empty>
          </template>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            :current-page="page.page"
            :page-size="page.size"
            :total="page.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            @size-change="emit('size-change', $event)"
            @current-change="emit('page-change', $event)"
          />
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps({
  loading: { type: Boolean, default: false },
  taskList: { type: Array, default: () => [] },
  page: { type: Object, default: () => ({ page: 1, size: 10, total: 0 }) },
  /** 空态文案：父页面加载失败时透传诚实提示（默认保留原语义） */
  emptyText: { type: String, default: '暂无查重任务' },
})

const emit = defineEmits(['execute', 'view-detail', 'page-change', 'size-change'])

function statusTagType(status) {
  const map = { PENDING: 'info', RUNNING: 'warning', COMPLETED: 'success' }
  return map[status] || 'info'
}

function statusText(status) {
  const map = { PENDING: '待执行', RUNNING: '执行中', COMPLETED: '已完成' }
  return map[status] || status || '未知'
}

function similarityColor(similarity) {
  if (similarity == null) return '#7A8BA5'
  const pct = similarity * 100
  if (pct >= 80) return '#C48080'
  if (pct >= 60) return '#CBB58A'
  return '#8BA888'
}
</script>

<style scoped>
.section-header {
  margin-bottom: var(--ac-spacing-md);
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
}
.pagination-wrapper {
  margin-top: var(--ac-spacing-md);
  display: flex;
  justify-content: flex-end;
}
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
</style>
