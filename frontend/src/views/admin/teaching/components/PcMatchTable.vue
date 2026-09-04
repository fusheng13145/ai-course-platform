<template>
  <section v-if="selectedTask" class="l1-card">
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
        <div class="l1-title">查重匹配详情</div>
        <div class="l1-sub">
          任务 #{{ selectedTask.id
          }}<template v-if="selectedTask.assignmentTitle">
            · {{ selectedTask.assignmentTitle }}</template
          >
        </div>
      </div>
    </div>
    <div class="l1-body">
      <div class="content-card">
        <el-table v-loading="loading" :data="matchList" style="width: 100%" stripe>
          <el-table-column prop="studentA" label="学生A" min-width="160" show-overflow-tooltip />
          <el-table-column prop="studentB" label="学生B" min-width="160" show-overflow-tooltip />
          <el-table-column label="相似度" width="140" align="center">
            <template #default="{ row }">
              <el-progress
                :percentage="Math.round((row.similarity ?? 0) * 100)"
                :color="similarityColor(row.similarity)"
                :stroke-width="18"
                :text-inside="true"
              />
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="matchStatusTagType(row.status)" size="small" effect="light">
                {{ matchStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="matchedContent"
            label="匹配内容"
            min-width="220"
            show-overflow-tooltip
          />
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
  selectedTask: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  matchList: { type: Array, default: () => [] },
  page: { type: Object, default: () => ({ page: 1, size: 10, total: 0 }) },
})

const emit = defineEmits(['page-change', 'size-change'])

function matchStatusTagType(status) {
  const map = { CONFIRMED: 'danger', PENDING: 'warning', DISMISSED: 'info' }
  return map[status] || 'info'
}

function matchStatusText(status) {
  const map = { CONFIRMED: '已确认', PENDING: '待审核', DISMISSED: '已忽略' }
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
</style>
