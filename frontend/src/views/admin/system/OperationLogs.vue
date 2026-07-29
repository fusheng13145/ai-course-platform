<template>
  <div class="fade-in operation-logs">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">日志审计</h2>
        <p class="page-desc">操作日志与登录日志统一审计，可回溯到人 / 时间 / 模块 / 结果</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="log-tabs">
      <!-- ================= 操作日志 ================= -->
      <el-tab-pane label="操作日志" name="operation">
    <!-- 统计概览 -->
    <div class="stats-row">
      <div class="stat-mini-card">
        <span class="label">日志总数</span>
        <span class="value">{{ stats.totalCount || 0 }}</span>
      </div>
      <div class="stat-mini-card student">
        <span class="label">成功操作</span>
        <span class="value">{{ stats.successCount || 0 }}</span>
      </div>
      <div class="stat-mini-card admin">
        <span class="label">失败操作</span>
        <span class="value">{{ stats.failedCount || 0 }}</span>
      </div>
      <div class="stat-mini-card teacher">
        <span class="label">成功率</span>
        <span class="value">{{ successRate }}%</span>
      </div>
    </div>

    <!-- 筛选工具栏 -->
    <div class="content-card filter-card">
      <div class="filter-row">
        <el-input
          v-model="filters.username"
          placeholder="操作人用户名"
          clearable
          style="width: 160px"
        />
        <el-select v-model="filters.module" placeholder="模块" clearable style="width: 150px">
          <el-option v-for="m in moduleOptions" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
        <el-select v-model="filters.action" placeholder="操作类型" clearable style="width: 150px">
          <el-option v-for="a in actionOptions" :key="a.value" :label="a.label" :value="a.value" />
        </el-select>
        <el-select v-model="filters.status" placeholder="结果" clearable style="width: 130px">
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
        <el-table-column prop="username" label="操作人" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.username || row.operatorName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ moduleLabel(row.module) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作" width="110" align="center">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || row.detail || '—' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP 地址" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ip || row.ipAddress || '—' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="结果" width="90" align="center" class-name="tag-col">
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
          <template #default="{ row }">{{ formatTime(row.createdAt || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <button class="act-btn--edit" @click="viewDetail(row)">详情</button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无操作日志" /></template>
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
      </el-tab-pane>

      <!-- ================= 登录日志 ================= -->
      <el-tab-pane label="登录日志" name="login" lazy>
        <!-- 统计概览 -->
        <div class="stats-row">
          <div class="stat-mini-card">
            <span class="label">登录总次数</span>
            <span class="value">{{ loginStats.totalCount || 0 }}</span>
          </div>
          <div class="stat-mini-card student">
            <span class="label">成功登录</span>
            <span class="value">{{ loginStats.successCount || 0 }}</span>
          </div>
          <div class="stat-mini-card admin">
            <span class="label">失败登录</span>
            <span class="value">{{ loginStats.failedCount || 0 }}</span>
          </div>
          <div class="stat-mini-card teacher">
            <span class="label">今日活跃</span>
            <span class="value">{{ loginStats.todayCount || 0 }}</span>
          </div>
        </div>

        <!-- 筛选工具栏 -->
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
            <el-table-column prop="operatingSystem" label="操作系统" width="120" show-overflow-tooltip>
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
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <template #empty><el-empty description="暂无登录日志" /></template>
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
      </el-tab-pane>
    </el-tabs>

    <!-- 详情弹窗 -->
    <el-dialog v-model="showDetail" title="操作日志详情" width="560px" append-to-body>
      <el-descriptions v-if="currentLog" :column="1" border>
        <el-descriptions-item label="日志 ID">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{
          currentLog.username || currentLog.operatorName || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{
          moduleLabel(currentLog.module)
        }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{
          actionLabel(currentLog.action)
        }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{
          currentLog.description || currentLog.detail || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ currentLog.method || '—' }}</el-descriptions-item>
        <el-descriptions-item label="请求路径">{{
          currentLog.requestUri || currentLog.uri || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="IP 地址">{{
          currentLog.ip || currentLog.ipAddress || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <span
            class="status-dot"
            :class="isSuccess(currentLog) ? 'status-dot--active' : 'status-dot--fail'"
          >
            <i></i>{{ isSuccess(currentLog) ? '成功' : '失败' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{
          currentLog.costTime != null ? currentLog.costTime + ' ms' : '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{
          formatTime(currentLog.createdAt || currentLog.createTime)
        }}</el-descriptions-item>
      </el-descriptions>
      <template #footer
        ><el-button type="primary" @click="showDetail = false">关闭</el-button></template
      >
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import operationLogApi from '@/api/operationLog'
import loginLogApi from '@/api/loginLog'

const activeTab = ref('operation')
const loading = ref(false)
const logList = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(15)
const stats = ref({ totalCount: 0, successCount: 0, failedCount: 0 })

const filters = reactive({ username: '', module: '', action: '', status: '' })

const moduleOptions = [
  { label: '用户管理', value: 'USER' },
  { label: '课程管理', value: 'COURSE' },
  { label: '考试管理', value: 'EXAM' },
  { label: '成绩管理', value: 'GRADE' },
  { label: '题库管理', value: 'QUESTION' },
  { label: '通知管理', value: 'NOTIFICATION' },
  { label: '系统配置', value: 'SYSTEM' },
  { label: '权限管理', value: 'PERMISSION' },
  { label: '认证登录', value: 'AUTH' },
]
const actionOptions = [
  { label: '新增', value: 'CREATE' },
  { label: '修改', value: 'UPDATE' },
  { label: '删除', value: 'DELETE' },
  { label: '查询', value: 'QUERY' },
  { label: '导入', value: 'IMPORT' },
  { label: '导出', value: 'EXPORT' },
  { label: '登录', value: 'LOGIN' },
  { label: '登出', value: 'LOGOUT' },
]
const moduleMap = Object.fromEntries(moduleOptions.map((m) => [m.value, m.label]))
const actionMap = Object.fromEntries(actionOptions.map((a) => [a.value, a.label]))
function moduleLabel(v) {
  return moduleMap[v] || v || '—'
}
function actionLabel(v) {
  return actionMap[v] || v || '—'
}

const successRate = computed(() => {
  const t = stats.value.totalCount || 0
  if (!t) return 0
  return Math.round(((stats.value.successCount || 0) / t) * 100)
})

function isSuccess(row) {
  const s = row.status
  if (s == null) return true
  return s === 'SUCCESS' || s === 1 || s === '1' || s === true || s === 'success'
}

function formatTime(t) {
  if (!t) return '—'
  try {
    const d = new Date(t)
    if (isNaN(d.getTime())) return t
    const p = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  } catch {
    return t
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize.value }
    if (filters.username) params.username = filters.username
    if (filters.module) params.module = filters.module
    if (filters.action) params.action = filters.action
    if (filters.status) params.status = filters.status
    const res = await operationLogApi.getOperationLogs(params)
    const data = res.data || res
    logList.value = data.records || data.list || data.content || (Array.isArray(data) ? data : [])
    total.value = data.total ?? data.totalElements ?? logList.value.length
  } catch (e) {
    logger.error('加载操作日志失败:', e)
    logList.value = []
    total.value = 0
    ElMessage.error('加载操作日志失败')
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res = await operationLogApi.getOperationLogStatistics()
    const data = res.data || res
    stats.value = {
      totalCount: data.totalCount ?? data.total ?? 0,
      successCount: data.successCount ?? data.success ?? 0,
      failedCount: data.failedCount ?? data.failed ?? 0,
    }
  } catch (e) {
    logger.error('加载日志统计失败:', e)
  }
}

function applyFilter() {
  page.value = 1
  loadLogs()
}
function resetFilter() {
  filters.username = ''
  filters.module = ''
  filters.action = ''
  filters.status = ''
  page.value = 1
  loadLogs()
}
function onPageChange(p) {
  page.value = p
  loadLogs()
}
function onSizeChange(s) {
  pageSize.value = s
  page.value = 1
  loadLogs()
}

/* 详情 */
const showDetail = ref(false)
const currentLog = ref(null)
function viewDetail(row) {
  currentLog.value = row
  showDetail.value = true
}

/* ================= 登录日志 ================= */
const loginLoading = ref(false)
const loginList = ref([])
const loginTotal = ref(0)
const loginPage = ref(1)
const loginPageSize = ref(15)
const loginStats = ref({ totalCount: 0, successCount: 0, failedCount: 0, todayCount: 0 })
const loginFilters = reactive({ username: '', loginStatus: '' })

function isLoginSuccess(row) {
  const s = row.loginStatus
  return s === 'SUCCESS' || s === 1 || s === '1' || s === true || s === 'success'
}

async function loadLoginLogs() {
  loginLoading.value = true
  try {
    const params = { page: loginPage.value, size: loginPageSize.value }
    if (loginFilters.username) params.username = loginFilters.username
    if (loginFilters.loginStatus) params.loginStatus = loginFilters.loginStatus
    const res = await loginLogApi.getList(params)
    const data = res.data || res
    loginList.value =
      data.records || data.list || data.content || (Array.isArray(data) ? data : [])
    loginTotal.value = data.total ?? data.totalElements ?? loginList.value.length
  } catch (e) {
    logger.error('加载登录日志失败:', e)
    loginList.value = []
    loginTotal.value = 0
    ElMessage.error('加载登录日志失败')
  } finally {
    loginLoading.value = false
  }
}

async function loadLoginStats() {
  try {
    const res = await loginLogApi.getStats()
    const data = res.data || res
    loginStats.value = {
      totalCount: data.totalCount ?? data.total ?? 0,
      successCount: data.successCount ?? data.success ?? 0,
      failedCount: data.failedCount ?? data.failed ?? 0,
      todayCount: data.todayCount ?? data.today ?? 0,
    }
  } catch (e) {
    logger.error('加载登录统计失败:', e)
  }
}

function applyLoginFilter() {
  loginPage.value = 1
  loadLoginLogs()
}
function resetLoginFilter() {
  loginFilters.username = ''
  loginFilters.loginStatus = ''
  loginPage.value = 1
  loadLoginLogs()
}
function onLoginPageChange(p) {
  loginPage.value = p
  loadLoginLogs()
}
function onLoginSizeChange(s) {
  loginPageSize.value = s
  loginPage.value = 1
  loadLoginLogs()
}

onMounted(() => {
  loadLogs()
  loadStats()
  loadLoginLogs()
  loadLoginStats()
})
</script>

<style scoped>
.operation-logs {
  max-width: 1400px;
  margin: 0 auto;
}

.log-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--ac-spacing-md);
}
.log-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

.page-header {
  margin-bottom: var(--ac-spacing-lg);
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  letter-spacing: -0.3px;
}
.page-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-top: 4px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}
.stat-mini-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: all var(--ac-transition-normal);
}
.stat-mini-card:hover {
  box-shadow: var(--ac-shadow-md);
  transform: translateY(-2px);
}
.stat-mini-card .label {
  font-size: 13px;
  color: var(--ac-text-muted);
  font-weight: 500;
}
.stat-mini-card .value {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
}
.stat-mini-card.admin .value {
  color: #b05045;
}
.stat-mini-card.teacher .value {
  color: #166534;
}
.stat-mini-card.student .value {
  color: #15803d;
}

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

@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
