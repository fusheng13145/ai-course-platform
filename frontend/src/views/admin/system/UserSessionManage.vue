<template>
  <div class="fade-in user-session-manage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">会话管理</h2>
        <p class="page-desc">在线用户监控、会话统计与异常检测，支持强制下线操作</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-mini-card">
        <span class="label">在线用户数</span>
        <span class="value">{{ stats.active || 0 }}</span>
      </div>
      <div class="stat-mini-card student">
        <span class="label">总会话数</span>
        <span class="value">{{ stats.total || 0 }}</span>
      </div>
      <div class="stat-mini-card admin">
        <span class="label">异常会话</span>
        <span class="value">{{ stats.abnormalCount || 0 }}</span>
      </div>
      <div class="stat-mini-card teacher">
        <span class="label">游客会话</span>
        <span class="value">{{ stats.guestCount || 0 }}</span>
      </div>
    </div>

    <!-- 在线用户表格 -->
    <div class="content-card">
      <div class="section-header">
        <span class="section-title">在线用户列表</span>
      </div>
      <el-table v-loading="loading" :data="onlineUsers" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="role" label="角色" width="110" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="roleTagType(row.role)"
              effect="plain"
            >
              {{ roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP 地址" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ip || row.ipAddress || '—' }}</template>
        </el-table-column>
        <el-table-column prop="loginTime" label="登录时间" width="170">
          <template #default="{ row }">{{ formatTime(row.loginTime) }}</template>
        </el-table-column>
        <el-table-column prop="lastActiveTime" label="最后活跃时间" width="170">
          <template #default="{ row }">{{ formatTime(row.lastActiveTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button
              type="danger"
              size="small"
              plain
              @click="handleForceLogout(row)"
            >
              强制下线
            </el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无在线用户" /></template>
      </el-table>
    </div>

    <!-- 会话统计概览 -->
    <div class="content-card session-stats-section">
      <div class="section-header">
        <span class="section-title">会话统计概览</span>
      </div>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="总会话数">{{ stats.total || 0 }}</el-descriptions-item>
        <el-descriptions-item label="活跃会话">{{ stats.active || 0 }}</el-descriptions-item>
        <el-descriptions-item label="游客会话">{{ stats.guestCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="异常会话">{{ stats.abnormalCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="今日新增会话">{{ stats.todayNew || 0 }}</el-descriptions-item>
        <el-descriptions-item label="昨日新增会话">{{ stats.yesterdayNew || 0 }}</el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import userSessionApi from '@/api/userSession'

const loading = ref(false)
const onlineUsers = ref([])
const stats = reactive({
  total: 0,
  active: 0,
  guestCount: 0,
  abnormalCount: 0,
  todayNew: 0,
  yesterdayNew: 0,
})

const roleMap = {
  ADMIN: '管理员',
  TEACHER: '教师',
  STUDENT: '学生',
  GUEST: '游客',
}

function roleLabel(role) {
  return roleMap[role] || role || '—'
}

function roleTagType(role) {
  switch (role) {
    case 'ADMIN':
      return 'danger'
    case 'TEACHER':
      return 'warning'
    case 'STUDENT':
      return 'success'
    case 'GUEST':
      return 'info'
    default:
      return ''
  }
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

async function loadOnlineUsers() {
  loading.value = true
  try {
    const res = await userSessionApi.getOnlineUsers()
    const data = res.data || res
    onlineUsers.value = Array.isArray(data) ? data : data.records || data.list || data.content || []
  } catch (e) {
    onlineUsers.value = []
    ElMessage.error('加载在线用户失败')
  } finally {
    loading.value = false
  }
}

async function loadSessionStats() {
  try {
    const res = await userSessionApi.getSessionStats()
    const data = res.data || res
    stats.total = data.total ?? data.totalCount ?? 0
    stats.active = data.active ?? data.activeCount ?? 0
    stats.guestCount = data.guestCount ?? 0
    stats.abnormalCount = data.abnormalCount ?? data.abnormal ?? 0
    stats.todayNew = data.todayNew ?? 0
    stats.yesterdayNew = data.yesterdayNew ?? 0
  } catch (e) {
    // 静默失败，仅使用默认值
  }
}

async function handleForceLogout(row) {
  try {
    await ElMessageBox.confirm(
      `确定要将用户「${row.username}」强制下线吗？该操作会立即断开其当前会话。`,
      '强制下线确认',
      {
        confirmButtonText: '确定下线',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      }
    )
    await userSessionApi.forceLogout(row.id)
    ElMessage.success(`用户「${row.username}」已强制下线`)
    await loadOnlineUsers()
    await loadSessionStats()
  } catch (e) {
    // 取消操作或请求失败
    if (e !== 'cancel') {
      ElMessage.error('强制下线失败')
    }
  }
}

onMounted(() => {
  loadOnlineUsers()
  loadSessionStats()
})
</script>

<style scoped>
.user-session-manage {
  max-width: 1400px;
  margin: 0 auto;
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
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
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
.session-stats-section {
  margin-top: var(--ac-spacing-md);
}

.section-header {
  margin-bottom: 14px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

:deep(.el-descriptions__title) {
  font-size: 15px;
  font-weight: 600;
}
:deep(.el-descriptions__cell) {
  font-size: 13px;
}

@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
