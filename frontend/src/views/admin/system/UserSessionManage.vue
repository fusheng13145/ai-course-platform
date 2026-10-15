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
        :value="statsError ? '--' : (stats.active || 0)"
        icon="users"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="总会话数"
        :value="statsError ? '--' : (stats.total || 0)"
        icon="list"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="异常会话"
        :value="statsError ? '--' : (stats.abnormalCount || 0)"
        icon="shield"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="游客会话"
        :value="statsError ? '--' : (stats.guestCount || 0)"
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
          <!-- 加载失败提示：非空即表示下方列表不可信（失败不得渲染成「暂无在线用户」） -->
          <el-alert
            v-if="usersError"
            :title="usersError"
            type="error"
            :closable="false"
            show-icon
            style="margin-bottom: var(--ac-spacing-md)"
          />
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
              <EmptyState :title="usersEmptyText()" icon="users" />
            </template>
          </el-table>
        </div>

        <!-- 会话统计概览 -->
        <div class="content-card session-stats-section">
          <div class="section-header">
            <span class="section-title">会话统计概览</span>
          </div>
          <!-- 加载失败提示：非空即表示下方读数不可信（原静默失败会谎报 0） -->
          <el-alert
            v-if="statsError"
            :title="statsError"
            type="error"
            :closable="false"
            show-icon
            style="margin-bottom: var(--ac-spacing-md)"
          />
          <el-descriptions :column="3" border>
            <el-descriptions-item label="总会话数">{{
              statsError ? '--' : (stats.total || 0)
            }}</el-descriptions-item>
            <el-descriptions-item label="活跃会话">{{
              statsError ? '--' : (stats.active || 0)
            }}</el-descriptions-item>
            <el-descriptions-item label="游客会话">{{
              statsError ? '--' : (stats.guestCount || 0)
            }}</el-descriptions-item>
            <el-descriptions-item label="异常会话">{{
              statsError ? '--' : (stats.abnormalCount || 0)
            }}</el-descriptions-item>
            <el-descriptions-item label="今日新增会话">{{
              statsError ? '--' : (stats.todayNew || 0)
            }}</el-descriptions-item>
            <el-descriptions-item label="昨日新增会话">{{
              statsError ? '--' : (stats.yesterdayNew || 0)
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
import { ElMessage } from 'element-plus'
import userSessionApi from '@/api/userSession'
import { confirmDanger } from '@/utils/dangerConfirm'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { useListFeedback } from '@/composables/useListFeedback'

// D2-1：两路数据（在线用户 / 会话统计）各自独立归因——单路失败只污染该路提示，
// 不把另一路数据与提示一起清掉（以 LoginLogs 多路聚合为参照，原统计路为静默失败）。
const usersFeedback = useListFeedback({
  failMessage: '在线用户加载失败，请稍后刷新；已保留上次显示的数据。',
  toastMessage: '在线用户加载失败',
  emptyText: '暂无在线用户',
  errorEmptyText: '在线用户未能加载，请查看上方提示后重试',
})
const statsFeedback = useListFeedback({
  failMessage: '会话统计加载失败，上方与会话概览读数暂不可信。',
  toastMessage: '会话统计加载失败',
})
const { loading, listError: usersError, run: runUsers, emptyDescription: usersEmptyText } =
  usersFeedback
const { listError: statsError, run: runStats } = statsFeedback
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
  await runUsers(async () => {
    const res = await userSessionApi.getOnlineUsers()
    const data = res?.data ?? res
    const list = Array.isArray(data)
      ? data
      : Array.isArray(data?.records)
        ? data.records
        : Array.isArray(data?.list)
          ? data.list
          : Array.isArray(data?.content)
            ? data.content
            : null
    // 响应格式校验：无法识别为列表时进入失败态，避免把异常响应当成空列表
    if (!list) throw new Error('在线用户响应格式无效')
    onlineUsers.value = list
  })
}

async function loadSessionStats() {
  await runStats(async () => {
    const res = await userSessionApi.getSessionStats()
    const data = res?.data ?? res
    // 响应格式校验：非对象即失败态，杜绝「静默失败 + 默认 0 值」谎报读数
    if (!data || typeof data !== 'object') throw new Error('会话统计响应格式无效')
    stats.total = data.total ?? data.totalCount ?? 0
    stats.active = data.active ?? data.activeCount ?? 0
    stats.guestCount = data.guestCount ?? 0
    stats.abnormalCount = data.abnormalCount ?? data.abnormal ?? 0
    stats.todayNew = data.todayNew ?? 0
    stats.yesterdayNew = data.yesterdayNew ?? 0
  })
}

async function handleForceLogout(row) {
  try {
    // D2-2：明确影响范围（立即断会话、未保存操作可能丢失）与恢复路径（重新登录）
    const ok = await confirmDanger({
      verb: '强制下线',
      target: '用户',
      subject: row.username,
      impact: '将立即断开该用户的当前会话，其未保存的操作可能丢失',
      irreversible: false,
      note: '用户重新登录即可恢复使用',
      confirmText: '确定下线',
    })
    if (!ok) return
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
</style>
