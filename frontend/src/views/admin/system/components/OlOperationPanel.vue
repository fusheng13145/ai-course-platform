<template>
  <div>
    <!-- 统计概览 -->
    <div class="stat-grid">
      <StatCard
        label="日志总数"
        :value="stats.totalCount || 0"
        icon="list"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="成功操作"
        :value="stats.successCount || 0"
        icon="check"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="失败操作"
        :value="stats.failedCount || 0"
        icon="warn"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="成功率"
        :value="successRate + '%'"
        icon="chart"
        tone="var(--violet-soft)"
        tone-color="var(--violet)"
      />
    </div>

    <!-- 筛选工具栏 -->
    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background: var(--accent-soft); color: var(--accent)">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" />
          </svg>
        </span>
        <div>
          <div class="l1-title">操作日志</div>
          <div class="l1-sub">按操作人 / 模块 / 结果检索审计</div>
        </div>
      </div>
      <div class="l1-body">
        <div class="content-card filter-card">
          <div class="filter-row">
            <el-input
              v-model="filters.username"
              placeholder="操作人用户名"
              clearable
              style="width: 160px"
            />
            <el-select
              v-model="filters.module"
              placeholder="模块"
              clearable
              style="width: 150px"
            >
              <el-option
                v-for="m in moduleOptions"
                :key="m.value"
                :label="m.label"
                :value="m.value"
              />
            </el-select>
            <el-select
              v-model="filters.action"
              placeholder="操作类型"
              clearable
              style="width: 150px"
            >
              <el-option
                v-for="a in actionOptions"
                :key="a.value"
                :label="a.label"
                :value="a.value"
              />
            </el-select>
            <el-select
              v-model="filters.status"
              placeholder="结果"
              clearable
              style="width: 130px"
            >
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-button type="primary" @click="applyFilter"
              ><el-icon><Search /></el-icon>查询</el-button
            >
            <el-button @click="resetFilter">重置</el-button>
          </div>
        </div>

        <!-- 日志表格 -->
        <div class="content-card">
          <el-table v-loading="loading" :data="logList" style="width: 100%">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column
              prop="username"
              label="操作人"
              min-width="110"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{
                row.username || row.operatorName || '—'
              }}</template>
            </el-table-column>
            <el-table-column prop="module" label="模块" width="120" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ moduleLabel(row.module) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="action" label="操作" width="110" align="center">
              <template #default="{ row }">{{ actionLabel(row.action) }}</template>
            </el-table-column>
            <el-table-column
              prop="description"
              label="描述"
              min-width="200"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{ row.description || row.detail || '—' }}</template>
            </el-table-column>
            <el-table-column prop="ip" label="IP 地址" width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.ip || row.ipAddress || '—' }}</template>
            </el-table-column>
            <el-table-column
              prop="status"
              label="结果"
              width="90"
              align="center"
              class-name="tag-col"
            >
              <template #default="{ row }">
                <span
                  class="status-dot"
                  :class="isSuccess(row) ? 'status-dot--active' : 'status-dot--fail'"
                >
                  <i></i>{{ isSuccess(row) ? '成功' : '失败' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="170">
              <template #default="{ row }">{{
                formatDateTime(row.createdAt || row.createTime, '—')
              }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <button class="act-btn--edit" @click="$emit('view-detail', row)">详情</button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无操作日志">
                <template #image><div class="empty-deco">📋</div></template>
              </el-empty>
            </template>
          </el-table>

          <div class="pager">
            <el-pagination
              layout="prev, pager, next, total, sizes"
              :total="total"
              :page-size="pageSize"
              :current-page="page"
              :page-sizes="[15, 30, 50, 100]"
              @current-change="onPageChange"
              @size-change="onSizeChange"
            />
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { formatDateTime } from '@/utils/datetime'
import { onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useOperationLogs } from '@/composables/useOperationLogs'

defineEmits(['view-detail'])

const {
  loading,
  logList,
  total,
  page,
  pageSize,
  stats,
  filters,
  moduleOptions,
  actionOptions,
  moduleLabel,
  actionLabel,
  successRate,
  isSuccess,
  loadLogs,
  loadStats,
  applyFilter,
  resetFilter,
  onPageChange,
  onSizeChange,
} = useOperationLogs()

onMounted(() => {
  loadLogs()
  loadStats()
})
</script>

<style scoped>
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px;
}
.filter-card {
  margin-bottom: var(--ac-spacing-md);
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

:deep(.tag-col .cell) {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0;
}

.status-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  padding: 2px 10px;
  border-radius: 20px;
  line-height: 20px;
}
.status-dot i {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot--active {
  color: #15803d;
  background: rgba(22, 163, 74, 0.08);
}
.status-dot--active i {
  background: #22c55e;
}
.status-dot--fail {
  color: #b05045;
  background: rgba(176, 80, 69, 0.08);
}
.status-dot--fail i {
  background: #ef4444;
}

.act-btn--edit {
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
  font-weight: 500;
  background: transparent;
  color: var(--ac-primary, #3b82f6);
}
.act-btn--edit:hover {
  background: rgba(59, 130, 246, 0.08);
  transform: translateY(-1px);
}

/* 空状态装饰 */
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
</style>
