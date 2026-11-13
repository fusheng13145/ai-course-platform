<template>
  <section class="sessions-body">
    <!-- 口径必须写在页面上：GET /user-sessions/online 实际返回**全部**会话记录
         （后端当前未按 is_active / expires_at 过滤，注释与实现不一致）。
         前端不能把这件事藏起来，否则「在线 531 个会话」就是一句假话。 -->
    <div class="card caveat-card">
      <p class="card-kicker">SCOPE / 会话口径</p>
      <p class="scope-line">
        数据源 <code>user_sessions</code>。后端 <code>GET /user-sessions/online</code>
        当前返回<b>全部会话记录</b>（未按活跃／过期过滤），因此本页默认只展示
        <b>有效且未过期</b>的会话，并提供「显示全部」开关。
      </p>
      <p class="scope-note">
        失效会话不会被说成在线；强制下线会让该会话的令牌立即失效，操作前二次确认。
      </p>
      <p v-if="currentFlagUnreliable" class="scope-note warn-text">
        注意：后端 <code>is_current</code> 在 {{ currentFlagged.length }} 条会话上为
        true（正常应只有 1
        条），因此本页<b>不据此判定「当前会话」、也不据此禁用下线</b>；避免踢掉自己请核对账号与设备。
      </p>
    </div>

    <div v-if="listError" class="callout" role="alert">
      <span>ERROR / 会话列表</span>
      <p>{{ listError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="loadSessions()">
        {{ loading ? '重试中…' : '重试' }}
      </button>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">SESSIONS / DEVICES</p>
          <h2>
            会话列表 <span class="chip chip-mute">{{ visible.length }} 条</span>
          </h2>
        </div>
        <div class="filter-row">
          <form class="search-form" @submit.prevent>
            <input
              v-model="keyword"
              class="input search-input"
              type="search"
              placeholder="搜索账号 / IP / 设备"
              aria-label="搜索会话"
            />
          </form>
          <button
            class="btn btn-sm"
            :class="showAll ? 'btn-primary' : 'btn-ghost'"
            :aria-pressed="showAll"
            @click="toggleAll"
          >
            {{ showAll ? '仅看在线' : '显示全部' }}
          </button>
          <button class="btn btn-sm btn-ghost" :disabled="loading" @click="loadSessions()">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>

      <p v-if="userMapError" class="hint warn-text">
        账号名映射加载失败（{{ userMapError }}），表格按「用户 #ID」显示；这不影响会话与下线操作。
      </p>

      <div v-if="pageRows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>用户</th>
              <th>设备 / 浏览器</th>
              <th class="w-140">IP</th>
              <th class="w-140">登录时间</th>
              <th class="w-140">最后活动</th>
              <th class="w-96">状态</th>
              <th class="w-96 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in pageRows" :key="String(row.id)">
              <td>
                <b>{{ displayUser(row) }}</b>
                <small class="cell-sub">会话 #{{ row.id }}</small>
              </td>
              <td>
                {{ deviceText(row) }}
                <small v-if="row.operatingSystem" class="cell-sub">{{ row.operatingSystem }}</small>
              </td>
              <td>{{ row.ipAddress || '—' }}</td>
              <td>{{ formatDateTime(row.loginTime) }}</td>
              <td>{{ formatDateTime(row.lastActivity) }}</td>
              <td>
                <span class="chip" :class="stateChip(row)">{{ stateLabel(row) }}</span>
                <small v-if="isCurrentSession(row)" class="cell-sub">本机当前会话</small>
              </td>
              <td>
                <button
                  class="text-btn danger"
                  :disabled="!isOnline(row) || isCurrentSession(row) || forcingId === row.id"
                  :title="isCurrentSession(row) ? '当前登录会话不能在此下线' : ''"
                  @click="askForce(row)"
                >
                  {{ forcingId === row.id ? '处理中…' : '强制下线' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else-if="loading" class="empty-state compact">
        <b>正在加载会话…</b>
      </div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="users" /></span>
        <b>{{ emptyDescription() }}</b>
        <p>
          {{
            listError
              ? '请查看上方提示后重试。'
              : showAll
                ? '库内还没有任何会话记录。'
                : '当前没有有效且未过期的会话；可点「显示全部」查看历史会话。'
          }}
        </p>
      </div>

      <footer v-if="!listError && visible.length > PAGE_SIZE" class="card-foot">
        <span>第 {{ page }} / {{ pageCount }} 页 · 共 {{ visible.length }} 条</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1" @click="goPage(-1)">上一页</button>
          <button class="btn btn-sm" :disabled="page >= pageCount" @click="goPage(1)">
            下一页
          </button>
        </div>
      </footer>

      <p class="hint">
        列表与筛选均在浏览器侧完成：该端点返回的是整表，没有分页参数；强制下线走
        <code>DELETE /user-sessions/{id}/force-logout</code>（仅管理员）。
      </p>
    </section>

    <Modal v-model:open="confirmOpen" title="强制下线" kicker="CONFIRM / SESSION" width="460px">
      <p class="modal-text">
        确定让「{{ target ? displayUser(target) : '该用户' }}」在 「{{
          target ? deviceText(target) : '该设备'
        }}」上的会话立即失效？
      </p>
      <p class="modal-hint">该会话的刷新令牌会立即失效，对方需要重新登录；此操作不可撤销。</p>
      <p v-if="actionError" class="modal-error" role="alert">{{ actionError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="actionBusy" @click="closeConfirm">取消</button>
        <button class="btn btn-danger" :disabled="actionBusy" @click="confirmForce">
          {{ actionBusy ? '处理中…' : '确认下线' }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Icon from '../../components/Icon.vue'
import Stat from '../../components/Stat.vue'
import Modal from '../../components/Modal.vue'
import { useUi } from '../../composables/ui'
import { useListFeedback } from '@/composables/useListFeedback'
import { getAllUsers } from '@/api/admin'
import userSessionApi from '@/api/userSession'
import { unwrap } from '@/utils/response'
import { formatDateTime } from '@/utils/datetime'
import type { Metric } from '../../nav'

interface SessionRow {
  id?: number | string
  userId?: number | string | null
  deviceName?: string | null
  browser?: string | null
  operatingSystem?: string | null
  ipAddress?: string | null
  userAgent?: string | null
  isActive?: boolean | null
  isCurrent?: boolean | null
  loginTime?: string | null
  lastActivity?: string | null
  expiresAt?: string | null
}

interface UserLite {
  id?: number | string
  username?: string | null
  fullName?: string | null
}

const PAGE_SIZE = 12
/** 每次加载时取一次「现在」，避免渲染过程中每行各自 new Date() 造成同表口径不一致 */
const now = ref(Date.now())

const { toast } = useUi()

const rows = ref<SessionRow[]>([])
const userMap = ref<Record<string, string>>({})
const userMapError = ref('')
const keyword = ref('')
const showAll = ref(false)
const page = ref(1)

const confirmOpen = ref(false)
const target = ref<SessionRow | null>(null)
const forcingId = ref<SessionRow['id'] | null>(null)
const actionBusy = ref(false)
const actionError = ref('')

const { loading, listError, run, emptyDescription } = useListFeedback({
  failMessage: '会话列表加载失败，请稍后重试。',
  toastMessage: '会话列表加载失败',
  emptyText: '暂无有效会话',
  errorEmptyText: '无法确认会话数据，请查看上方提示后重试',
  notify: (message) => toast(message, 'danger'),
})

function isExpired(row: SessionRow): boolean {
  if (!row.expiresAt) return false
  const at = Date.parse(String(row.expiresAt))
  return Number.isFinite(at) && at <= now.value
}

function isOnline(row: SessionRow): boolean {
  return row.isActive === true && !isExpired(row)
}

/**
 * `is_current` 目前不可当作「当前会话」判据：真栈实测 536 条会话里有 450 条为 true
 * （与 is_active 同值，后端写入时没有区分）。所以只有当**恰好一条**为 true 时才采信；
 * 否则整个字段视为不可用——否则「当前会话」会标满整页，下线按钮也会被全量禁用。
 */
const currentFlagged = computed(() => rows.value.filter((row) => row.isCurrent === true))
const currentFlagUnreliable = computed(() => currentFlagged.value.length > 1)
const currentSessionId = computed(() =>
  currentFlagged.value.length === 1 ? currentFlagged.value[0].id : null,
)

function isCurrentSession(row: SessionRow): boolean {
  return currentSessionId.value !== null && row.id === currentSessionId.value
}

const onlineRows = computed(() => rows.value.filter(isOnline))
const onlineUsers = computed(
  () => new Set(onlineRows.value.map((row) => String(row.userId ?? ''))).size,
)

const baseRows = computed(() => (showAll.value ? rows.value : onlineRows.value))

const visible = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  const list = baseRows.value.filter((row) => {
    if (!q) return true
    return [
      displayUser(row),
      row.ipAddress,
      row.deviceName,
      row.browser,
      row.operatingSystem,
      row.userAgent,
    ]
      .map((item) => String(item || '').toLowerCase())
      .some((item) => item.includes(q))
  })
  return [...list].sort(
    (a, b) =>
      Date.parse(String(b.lastActivity || b.loginTime || '')) -
      Date.parse(String(a.lastActivity || a.loginTime || '')),
  )
})

const pageCount = computed(() => Math.max(1, Math.ceil(visible.value.length / PAGE_SIZE)))
const pageRows = computed(() =>
  visible.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
)

const metrics = computed<Metric[]>(() => [
  {
    label: '在线会话',
    value: countText(onlineRows.value.length),
    unit: '个',
    note: '有效且未过期',
    icon: 'users',
    tone: 'ok',
  },
  {
    label: '在线用户',
    value: countText(onlineUsers.value),
    unit: '人',
    note: '按用户去重',
    icon: 'user',
  },
  {
    label: '会话总量',
    value: listError.value ? '—' : countText(rows.value.length),
    unit: '个',
    note: '接口返回的全部记录（含失效）',
    icon: 'layers',
  },
  {
    label: '已失效',
    value: listError.value ? '—' : countText(rows.value.length - onlineRows.value.length),
    unit: '个',
    note: 'is_active=false 或已过期',
    icon: 'clock',
  },
])

function countText(value: number): string {
  if (Number.isNaN(Number(value))) return '—'
  return new Intl.NumberFormat('zh-CN').format(Number(value))
}

function displayUser(row: SessionRow): string {
  const key = String(row.userId ?? '')
  return (
    userMap.value[key] ||
    (row.userId === null || row.userId === undefined ? '未知用户' : `用户 #${key}`)
  )
}

function deviceText(row: SessionRow): string {
  return [row.deviceName, row.browser].filter(Boolean).join(' · ') || '—'
}

function stateLabel(row: SessionRow): string {
  if (isCurrentSession(row)) return '当前会话'
  if (isExpired(row)) return '已过期'
  if (row.isActive) return '在线'
  return '已失效'
}

function stateChip(row: SessionRow): string {
  if (isCurrentSession(row)) return 'chip-info'
  if (isExpired(row)) return 'chip-mute'
  return row.isActive ? 'chip-ok' : 'chip-mute'
}

watch([keyword, showAll], () => {
  page.value = 1
})

function toggleAll() {
  showAll.value = !showAll.value
  page.value = 1
}

function goPage(step: number) {
  page.value = Math.min(pageCount.value, Math.max(1, page.value + step))
}

async function loadUserMap() {
  try {
    const users = await getAllUsers()
    const map: Record<string, string> = {}
    for (const user of (users || []) as UserLite[]) {
      const key = String(user.id ?? '')
      if (!key) continue
      map[key] = String(user.fullName || user.username || `用户 #${key}`)
    }
    userMap.value = map
    userMapError.value = ''
  } catch (error) {
    // 账号名只是展示增强：失败时按 ID 显示并在页面上说明，不阻断会话管理
    userMap.value = {}
    userMapError.value = error instanceof Error ? error.message : '请求失败'
  }
}

async function loadSessions() {
  now.value = Date.now()
  await run(async () => {
    const data = unwrap(await userSessionApi.getOnlineUsers())
    if (!Array.isArray(data)) throw new Error('会话列表响应格式无效')
    rows.value = data as SessionRow[]
  })
}

function askForce(row: SessionRow) {
  if (!isOnline(row) || row.isCurrent) return
  target.value = row
  actionError.value = ''
  confirmOpen.value = true
}

function closeConfirm() {
  if (actionBusy.value) return
  confirmOpen.value = false
  target.value = null
  actionError.value = ''
}

async function confirmForce() {
  const row = target.value
  if (!row || row.id === undefined || row.id === null) return
  forcingId.value = row.id
  actionBusy.value = true
  actionError.value = ''
  try {
    await userSessionApi.forceLogout(row.id)
    toast('已强制下线，该会话令牌已失效', 'ok')
    confirmOpen.value = false
    target.value = null
    await loadSessions()
  } catch (error) {
    const err = error as { response?: { data?: { message?: string } } } | null
    const message = err?.response?.data?.message || '强制下线失败，请稍后重试'
    toast(message, 'danger')
    actionError.value = message
  } finally {
    actionBusy.value = false
    forcingId.value = null
  }
}

onMounted(() => {
  void loadSessions()
  void loadUserMap()
})
</script>

<style scoped>
.sessions-body {
  display: grid;
  gap: 14px;
}
.caveat-card {
  display: grid;
  gap: 6px;
}
.scope-line {
  margin: 0;
  color: var(--text-2);
}
.scope-note {
  margin: 0;
  color: var(--text-3);
  font-size: 13px;
}
.warn-text {
  color: var(--danger);
}
.modal-error {
  margin: 8px 0 0;
  color: var(--danger);
  font-size: 13px;
}
</style>
