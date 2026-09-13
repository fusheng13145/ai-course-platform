<template>
  <div>
    <!-- 统计概览 -->
    <div class="stat-grid">
      <StatCard
        label="登录总次数"
        :value="loginStats.totalCount || 0"
        icon="list"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="成功登录"
        :value="loginStats.successCount || 0"
        icon="check"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="失败登录"
        :value="loginStats.failedCount || 0"
        icon="warn"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="今日活跃"
        :value="loginStats.todayCount || 0"
        icon="clock"
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
          <div class="l1-title">登录日志</div>
          <div class="l1-sub">按用户名 / 结果检索登录审计</div>
        </div>
      </div>
      <div class="l1-body">
        <div class="content-card filter-card">
          <div class="filter-row">
            <el-input
              v-model="loginFilters.username"
              placeholder="用户名"
              clearable
              style="width: 160px"
            />
            <el-select
              v-model="loginFilters.loginStatus"
              placeholder="登录结果"
              clearable
              style="width: 140px"
            >
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-button type="primary" @click="applyLoginFilter"
              ><el-icon><Search /></el-icon>查询</el-button
            >
            <el-button @click="resetLoginFilter">重置</el-button>
          </div>
        </div>

        <!-- 登录日志表格 -->
        <div class="content-card">
          <el-table v-loading="loginLoading" :data="loginList" style="width: 100%">
            <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
            <el-table-column prop="loginStatus" label="结果" width="90" align="center">
              <template #default="{ row }">
                <span
                  class="status-dot"
                  :class="isLoginSuccess(row) ? 'status-dot--active' : 'status-dot--fail'"
                >
                  <i></i>{{ isLoginSuccess(row) ? '成功' : '失败' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="ipAddress" label="IP 地址" width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.ipAddress || '—' }}</template>
            </el-table-column>
            <el-table-column prop="location" label="登录地点" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.location || '—' }}</template>
            </el-table-column>
            <el-table-column prop="browser" label="浏览器" width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.browser || '—' }}</template>
            </el-table-column>
            <el-table-column
              prop="operatingSystem"
              label="操作系统"
              width="120"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{ row.operatingSystem || '—' }}</template>
            </el-table-column>
            <el-table-column
              prop="failureReason"
              label="失败原因"
              min-width="140"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{ row.failureReason || '—' }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="登录时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt, '—') }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无登录日志">
                <template #image><div class="empty-deco">🔐</div></template>
              </el-empty>
            </template>
          </el-table>

          <div class="pager">
            <el-pagination
              layout="prev, pager, next, total, sizes"
              :total="loginTotal"
              :page-size="loginPageSize"
              :current-page="loginPage"
              :page-sizes="[15, 30, 50, 100]"
              @current-change="onLoginPageChange"
              @size-change="onLoginSizeChange"
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
import { useLoginLogs } from '@/composables/useLoginLogs'

const {
  loginLoading,
  loginList,
  loginTotal,
  loginPage,
  loginPageSize,
  loginStats,
  loginFilters,
  isLoginSuccess,
  loadLoginLogs,
  loadLoginStats,
  applyLoginFilter,
  resetLoginFilter,
  onLoginPageChange,
  onLoginSizeChange,
} = useLoginLogs()

onMounted(() => {
  loadLoginLogs()
  loadLoginStats()
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
  color: var(--ok);
  background: rgba(22, 163, 74, 0.08);
}
.status-dot--active i {
  background: var(--ok);
}
.status-dot--fail {
  color: var(--accent-strong);
  background: rgba(176, 80, 69, 0.08);
}
.status-dot--fail i {
  background: var(--accent);
}

/* 空状态装饰 */
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
</style>
