<template>
  <div class="fade-in user-session-manage">
    <PageHeader
      title="会话管理"
      subtitle="在线用户监控、会话统计与异常检测，支持强制下线操作"
      icon="users"
      icon-bg="accent"
    />

    <div class="stat-grid">
      <StatCard
        label="在线用户数"
        :value="stats.active || 0"
        icon="users"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="总会话数"
        :value="stats.total || 0"
        icon="list"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="异常会话"
        :value="stats.abnormalCount || 0"
        icon="shield"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="游客会话"
        :value="stats.guestCount || 0"
        icon="clock"
        tone="var(--violet-soft)"
        tone-color="var(--violet)"
      />
    </div>

    <!-- 在线用户表格 -->
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
          <div class="l1-title">在线用户与会话</div>
          <div class="l1-sub">实时监控在线用户，支持强制下线与会话统计</div>
        </div>
      </div>
      <div class="l1-body">
        <div class="content-card">
          <div class="section-header">
            <span class="section-title">在线用户列表</span>
          </div>
          <el-table v-loading="loading" :data="onlineUsers" style="width: 100%">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
            <el-table-column prop="role" label="角色" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="roleTagType(row.role)" effect="plain">
                  {{ roleLabel(row.role) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ip" label="IP 地址" width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.ip || row.ipAddress || '—' }}</template>
            </el-table-column>
            <el-table-column prop="loginTime" label="登录时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.loginTime, '—') }}</template>
            </el-table-column>
            <el-table-column prop="lastActiveTime" label="最后活跃时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.lastActiveTime, '—') }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button type="danger" size="small" plain @click="handleForceLogout(row)">
                  强制下线
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无在线用户">
                <template #image><div class="empty-deco">👥</div></template>
              </el-empty>
            </template>
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
            <el-descriptions-item label="游客会话">{{
              stats.guestCount || 0
            }}</el-descriptions-item>
            <el-descriptions-item label="异常会话">{{
              stats.abnormalCount || 0
            }}</el-descriptions-item>
            <el-descriptions-item label="今日新增会话">{{
              stats.todayNew || 0
            }}</el-descriptions-item>
            <el-descriptions-item label="昨日新增会话">{{
              stats.yesterdayNew || 0
            }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { formatDateTime } from '@/utils/datetime'
import { ElMessage, ElMessageBox } from 'element-plus'
import userSessionApi from '@/api/userSession'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

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
      },
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

/* 空状态装饰 */
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
</style>
