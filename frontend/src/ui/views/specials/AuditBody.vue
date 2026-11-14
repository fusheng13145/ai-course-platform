<template>
  <section class="audit-body">
    <!-- 口径先摆在前头：这一页只有「查」——没有删除、改写或导出日志的入口。
         审计记录的完整性由后端保证，前端不提供任何会让留痕变浅的操作。 -->
    <div class="card caveat-card">
      <p class="card-kicker">审计面</p>
      <p class="scope-line">操作日志记录关键写操作；登录日志记录每一次登录尝试（含失败原因）。</p>
      <p class="scope-note">
        本页只提供查询与筛选，<b>不提供删除或改写日志</b>；模块与操作按<b>精确匹配</b>筛选，
        需与系统内记录的取值一致。
      </p>
    </div>

    <div class="tabs" role="tablist" aria-label="审计视图">
      <button
        v-for="item in TABS"
        :key="item.id"
        role="tab"
        :aria-selected="tab === item.id"
        :class="{ 'is-active': tab === item.id }"
        @click="switchTab(item.id)"
      >
        {{ item.label }}
      </button>
    </div>

    <!-- ============ 操作日志 ============ -->
    <template v-if="tab === 'operation'">
      <div v-if="opError" class="callout" role="alert">
        <span>操作日志</span>
        <p>{{ opError }}</p>
        <button class="btn btn-sm" :disabled="opLoading" @click="reloadOp()">
          {{ opLoading ? '重试中…' : '重试' }}
        </button>
      </div>

      <div class="stat-grid kpi-grid">
        <Stat v-for="metric in opMetrics" :key="metric.label" :metric="metric" role="admin" />
      </div>

      <section class="card table-card">
        <header class="card-head">
          <div>
            <h2>
              操作日志 <span class="chip chip-mute">{{ opTotal }} 条</span>
            </h2>
          </div>
          <div class="filter-row">
            <form class="search-form" @submit.prevent="applyOpFilters">
              <input
                v-model="opFilters.username"
                class="input search-input"
                type="search"
                placeholder="按账号筛选（精确）"
                aria-label="按账号筛选操作日志"
              />
              <input
                v-model="opFilters.module"
                class="input filter-input"
                type="search"
                placeholder="模块，如 Auth"
                aria-label="按模块筛选操作日志"
              />
              <input
                v-model="opFilters.action"
                class="input filter-input"
                type="search"
                placeholder="操作，如 CREATE"
                aria-label="按操作类型筛选操作日志"
              />
              <select v-model="opFilters.status" class="input" aria-label="按状态筛选操作日志">
                <option value="">全部状态</option>
                <option value="SUCCESS">成功</option>
                <option value="FAILED">失败</option>
              </select>
              <button class="btn btn-sm" type="submit" :disabled="opLoading">查询</button>
              <button
                class="btn btn-sm btn-ghost"
                type="button"
                :disabled="opLoading"
                @click="resetOpFilters"
              >
                重置
              </button>
            </form>
            <button class="btn btn-sm btn-ghost" :disabled="opLoading" @click="reloadOp()">
              {{ opLoading ? '刷新中…' : '刷新' }}
            </button>
          </div>
        </header>

        <div v-if="opRows.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th class="w-140">时间</th>
                <th>操作者</th>
                <th>模块 / 动作</th>
                <th>目标</th>
                <th class="w-80">状态</th>
                <th class="w-96">耗时</th>
                <th class="w-140">IP</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in opRows" :key="String(row.id ?? index)">
                <td>{{ formatDateTime(row.createdAt) }}</td>
                <td>
                  <b>{{ operatorOf(row) }}</b>
                  <small v-if="row.userId !== undefined && row.userId !== null" class="cell-sub">
                    用户 ID {{ row.userId }}
                  </small>
                </td>
                <td>
                  <b>{{ row.module || '—' }}</b>
                  <small class="cell-sub">{{ actionLabel(row.action) }}</small>
                </td>
                <td>
                  <span :title="row.target || ''">{{ shorten(row.target, 42) }}</span>
                  <small v-if="row.errorMsg" class="cell-sub danger-text" :title="row.errorMsg">
                    {{ shorten(row.errorMsg, 36) }}
                  </small>
                </td>
                <td>
                  <span class="chip" :class="statusChip(row.status)">
                    {{ statusLabel(row.status) }}
                  </span>
                </td>
                <td>
                  {{
                    row.durationMs === undefined || row.durationMs === null
                      ? '—'
                      : `${row.durationMs} ms`
                  }}
                </td>
                <td>{{ row.ip || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else-if="opLoading" class="empty-state compact">
          <b>正在加载操作日志…</b>
        </div>
        <div v-else class="empty-state compact">
          <span class="e-ico"><Icon name="shield" /></span>
          <b>{{ opEmpty() }}</b>
          <p>
            {{
              opError
                ? '请查看上方提示后重试。'
                : '调整账号、模块、操作或状态后再试；筛选为精确匹配。'
            }}
          </p>
        </div>

        <footer v-if="!opError && opTotal > OP_PAGE_SIZE" class="card-foot">
          <span>第 {{ opPage }} / {{ opPageCount }} 页 · 共 {{ opTotal }} 条</span>
          <div class="pager">
            <button class="btn btn-sm" :disabled="opPage <= 1 || opLoading" @click="goOpPage(-1)">
              上一页
            </button>
            <button
              class="btn btn-sm"
              :disabled="opPage >= opPageCount || opLoading"
              @click="goOpPage(1)"
            >
              下一页
            </button>
          </div>
        </footer>
      </section>
    </template>

    <!-- ============ 登录日志 ============ -->
    <template v-else>
      <div v-if="loginError" class="callout" role="alert">
        <span>登录日志</span>
        <p>{{ loginError }}</p>
        <button class="btn btn-sm" :disabled="loginLoading" @click="reloadLogin()">
          {{ loginLoading ? '重试中…' : '重试' }}
        </button>
      </div>

      <div class="stat-grid kpi-grid">
        <Stat v-for="metric in loginMetrics" :key="metric.label" :metric="metric" role="admin" />
      </div>

      <section class="card table-card">
        <header class="card-head">
          <div>
            <h2>
              登录日志 <span class="chip chip-mute">{{ loginTotal }} 条</span>
            </h2>
          </div>
          <div class="filter-row">
            <form class="search-form" @submit.prevent="applyLoginFilters">
              <select v-model="loginFilters.status" class="input" aria-label="按登录状态筛选">
                <option value="">全部状态</option>
                <option value="SUCCESS">成功</option>
                <!-- 库内实际写入的是 FAILURE（不是 FAILED）：2026-11-04 真栈实测
                     status=FAILED 命中 0 条、status=FAILURE 命中 18 条。 -->
                <option value="FAILURE">失败</option>
              </select>
              <button class="btn btn-sm" type="submit" :disabled="loginLoading">查询</button>
              <button
                class="btn btn-sm btn-ghost"
                type="button"
                :disabled="loginLoading"
                @click="resetLoginFilters"
              >
                重置
              </button>
            </form>
            <button class="btn btn-sm btn-ghost" :disabled="loginLoading" @click="reloadLogin()">
              {{ loginLoading ? '刷新中…' : '刷新' }}
            </button>
          </div>
        </header>

        <div v-if="loginRows.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th class="w-140">时间</th>
                <th>账号</th>
                <th class="w-96">状态</th>
                <th>IP / 归属地</th>
                <th>设备 / 浏览器</th>
                <th>失败原因</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in loginRows" :key="String(row.id ?? index)">
                <td>{{ formatDateTime(row.createdAt) }}</td>
                <td>
                  <b>{{ row.username || '—' }}</b>
                  <small v-if="row.userId !== undefined && row.userId !== null" class="cell-sub">
                    用户 ID {{ row.userId }}
                  </small>
                </td>
                <td>
                  <span class="chip" :class="statusChip(row.loginStatus)">
                    {{ statusLabel(row.loginStatus) }}
                  </span>
                </td>
                <td>
                  {{ row.ipAddress || '—' }}
                  <small v-if="row.location" class="cell-sub">{{ row.location }}</small>
                </td>
                <td>
                  {{ [row.deviceName, row.browser].filter(Boolean).join(' · ') || '—' }}
                  <small v-if="row.operatingSystem" class="cell-sub">{{
                    row.operatingSystem
                  }}</small>
                </td>
                <td>
                  <span v-if="row.failureReason" class="danger-text">{{ row.failureReason }}</span>
                  <span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else-if="loginLoading" class="empty-state compact">
          <b>正在加载登录日志…</b>
        </div>
        <div v-else class="empty-state compact">
          <span class="e-ico"><Icon name="lock" /></span>
          <b>{{ loginEmpty() }}</b>
          <p>{{ loginError ? '请查看上方提示后重试。' : '调整登录状态后再试。' }}</p>
        </div>

        <footer v-if="!loginError && loginTotal > LOGIN_PAGE_SIZE" class="card-foot">
          <span>第 {{ loginPage }} / {{ loginPageCount }} 页 · 共 {{ loginTotal }} 条</span>
          <div class="pager">
            <button
              class="btn btn-sm"
              :disabled="loginPage <= 1 || loginLoading"
              @click="goLoginPage(-1)"
            >
              上一页
            </button>
            <button
              class="btn btn-sm"
              :disabled="loginPage >= loginPageCount || loginLoading"
              @click="goLoginPage(1)"
            >
              下一页
            </button>
          </div>
        </footer>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Icon from '../../components/Icon.vue'
import Stat from '../../components/Stat.vue'
import { useUi } from '../../composables/ui'
import { useListFeedback } from '@/composables/useListFeedback'
import { getOperationLogs, getOperationLogStatistics } from '@/api/operationLog'
import loginLogApi from '@/api/loginLog'
import { pickList, readTotal, unwrap } from '@/utils/response'
import { formatDateTime } from '@/utils/datetime'
import type { Metric } from '../../nav'

type TabId = 'operation' | 'login'

interface OpLogRow {
  id?: number | string
  userId?: number | string | null
  username?: string | null
  module?: string | null
  action?: string | null
  target?: string | null
  ip?: string | null
  method?: string | null
  durationMs?: number | null
  status?: string | null
  errorMsg?: string | null
  createdAt?: string | null
}

interface LoginLogRow {
  id?: number | string
  userId?: number | string | null
  username?: string | null
  loginStatus?: string | null
  ipAddress?: string | null
  location?: string | null
  deviceName?: string | null
  browser?: string | null
  operatingSystem?: string | null
  failureReason?: string | null
  createdAt?: string | null
}

interface OpStats {
  totalCount?: number
  successCount?: number
  failedCount?: number
}

interface LoginStats {
  totalCount?: number
  successCount?: number
  failedCount?: number
  /** 后端已是百分比（99.41），不要再乘 100 */
  successRate?: number
  todayCount?: number
  weekCount?: number
}

const TABS: Array<{ id: TabId; label: string }> = [
  { id: 'operation', label: '操作日志' },
  { id: 'login', label: '登录日志' },
]

const OP_PAGE_SIZE = 15
const LOGIN_PAGE_SIZE = 15

const { toast } = useUi()

const tab = ref<TabId>('operation')

const opRows = ref<OpLogRow[]>([])
const opTotal = ref(0)
const opPage = ref(1)
const opStats = ref<OpStats>({})
const opFilters = reactive({ username: '', module: '', action: '', status: '' })

const loginRows = ref<LoginLogRow[]>([])
const loginTotal = ref(0)
const loginPage = ref(1)
const loginStats = ref<LoginStats>({})
const loginFilters = reactive({ status: '' })

/**
 * 两个页签各自持一份反馈态：一个页签失败不能把另一个页签也变成「无法确认」。
 * 这是 §11.8.3 里收件箱三块「失败互不牵连」的同一条判据。
 */
const {
  loading: opLoading,
  listError: opError,
  run: runOp,
  emptyDescription: opEmpty,
} = useListFeedback({
  failMessage: '操作日志加载失败，请稍后重试。',
  toastMessage: '操作日志加载失败',
  emptyText: '暂无操作日志',
  errorEmptyText: '无法确认操作日志，请查看上方提示后重试',
  notify: (message) => toast(message, 'danger'),
})

const {
  loading: loginLoading,
  listError: loginError,
  run: runLogin,
  emptyDescription: loginEmpty,
} = useListFeedback({
  failMessage: '登录日志加载失败，请稍后重试。',
  toastMessage: '登录日志加载失败',
  emptyText: '暂无登录日志',
  errorEmptyText: '无法确认登录日志，请查看上方提示后重试',
  notify: (message) => toast(message, 'danger'),
})

const opPageCount = computed(() => Math.max(1, Math.ceil(opTotal.value / OP_PAGE_SIZE)))
const loginPageCount = computed(() => Math.max(1, Math.ceil(loginTotal.value / LOGIN_PAGE_SIZE)))

/** 失败时不给 0：0 会被读成「查过且确实没有」。 */
function countText(value: number | undefined | null): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return new Intl.NumberFormat('zh-CN').format(Number(value))
}

const opMetrics = computed<Metric[]>(() => {
  const stats = opStats.value
  const total = stats.totalCount
  const success = stats.successCount
  const failed = stats.failedCount
  const rate =
    typeof total === 'number' && total > 0 && typeof success === 'number'
      ? `${((success / total) * 100).toFixed(1)}%`
      : '—'
  return [
    {
      label: '操作总数',
      value: countText(total),
      unit: '条',
      note: '库内全部操作记录',
      icon: 'shield',
    },
    {
      label: '成功',
      value: countText(success),
      unit: '条',
      note: '全部尝试中成功',
      icon: 'check',
      tone: 'ok',
    },
    { label: '失败', value: countText(failed), unit: '条', note: '全部尝试中失败', icon: 'alert' },
    { label: '成功率', value: rate, note: '成功 / 总数', icon: 'chart' },
  ]
})

const loginMetrics = computed<Metric[]>(() => {
  const stats = loginStats.value
  const rate =
    stats.successRate === null || stats.successRate === undefined
      ? '—'
      : `${Number(stats.successRate).toFixed(1)}%`
  return [
    {
      label: '登录总数',
      value: countText(stats.totalCount),
      unit: '次',
      note: '全部登录尝试',
      icon: 'lock',
    },
    { label: '成功率', value: rate, note: '按全部尝试计算', icon: 'check', tone: 'ok' },
    {
      label: '今日登录',
      value: countText(stats.todayCount),
      unit: '次',
      note: '自然日口径',
      icon: 'clock',
    },
    {
      label: '近 7 天',
      value: countText(stats.weekCount),
      unit: '次',
      note: '滚动 7 天',
      icon: 'calendar',
    },
  ]
})

function operatorOf(row: OpLogRow): string {
  if (row.username) return String(row.username)
  if (row.userId !== undefined && row.userId !== null) return `用户 #${row.userId}`
  // 登录接口本身也会被记录，此时还没有会话，库内 username 为空——如实写「未登录」
  return '未登录 / 系统'
}

const STATUS_LABELS: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  // 后端登录日志写入的是 FAILURE；FAILED 保留兼容旧数据
  FAILURE: '失败',
  LOCKED: '已锁定',
  EXPIRED: '已过期',
}

const ACTION_LABELS: Record<string, string> = {
  CREATE: '新增',
  UPDATE: '更新',
  DELETE: '删除',
  LOGIN: '登录',
  LOGOUT: '登出',
  IMPORT: '导入',
  EXPORT: '导出',
  UPLOAD: '上传',
  DOWNLOAD: '下载',
}

function statusLabel(value?: string | null): string {
  const key = String(value || '').toUpperCase()
  return STATUS_LABELS[key] || (value ? String(value) : '—')
}

function statusChip(value?: string | null): string {
  const key = String(value || '').toUpperCase()
  if (key === 'SUCCESS') return 'chip-ok'
  if (key === 'FAILED' || key === 'FAILURE' || key === 'LOCKED') return 'chip-danger'
  return 'chip-mute'
}

function actionLabel(value?: string | null): string {
  const key = String(value || '').toUpperCase()
  const label = ACTION_LABELS[key]
  return label ? `${label}（${key}）` : value ? String(value) : '—'
}

function shorten(value?: string | null, max = 40): string {
  const text = String(value || '')
  if (!text) return '—'
  return text.length > max ? `${text.slice(0, max)}…` : text
}

async function loadOpStats() {
  try {
    const data = unwrap<OpStats>(await getOperationLogStatistics())
    opStats.value = data && typeof data === 'object' ? data : {}
  } catch {
    // 统计失败不冒充 0：KPI 统一显示 —，列表仍可独立使用
    opStats.value = {}
  }
}

async function loadOp(resetPage = false) {
  if (resetPage) opPage.value = 1
  await runOp(async () => {
    const payload = unwrap(
      await getOperationLogs({
        page: opPage.value,
        size: OP_PAGE_SIZE,
        username: opFilters.username.trim() || undefined,
        module: opFilters.module.trim() || undefined,
        action: opFilters.action.trim() || undefined,
        status: opFilters.status || undefined,
      }),
    )
    if (!payload || typeof payload !== 'object') throw new Error('操作日志响应格式无效')
    const list = pickList<OpLogRow>(payload)
    opRows.value = list
    opTotal.value = readTotal(payload, list.length)
  })
  void loadOpStats()
}

async function loadLogin(resetPage = false) {
  if (resetPage) loginPage.value = 1
  await runLogin(async () => {
    const payload = unwrap(
      await loginLogApi.getList({
        page: loginPage.value,
        size: LOGIN_PAGE_SIZE,
        status: loginFilters.status || undefined,
      }),
    )
    if (!payload || typeof payload !== 'object') throw new Error('登录日志响应格式无效')
    const list = pickList<LoginLogRow>(payload)
    loginRows.value = list
    loginTotal.value = readTotal(payload, list.length)
  })
  void loadLoginStats()
}

async function loadLoginStats() {
  try {
    const data = unwrap<LoginStats>(await loginLogApi.getStats())
    loginStats.value = data && typeof data === 'object' ? data : {}
  } catch {
    loginStats.value = {}
  }
}

function reloadOp() {
  void loadOp()
}

function reloadLogin() {
  void loadLogin()
}

function applyOpFilters() {
  void loadOp(true)
}

function resetOpFilters() {
  opFilters.username = ''
  opFilters.module = ''
  opFilters.action = ''
  opFilters.status = ''
  void loadOp(true)
}

function applyLoginFilters() {
  void loadLogin(true)
}

function resetLoginFilters() {
  loginFilters.status = ''
  void loadLogin(true)
}

function goOpPage(step: number) {
  opPage.value = Math.min(opPageCount.value, Math.max(1, opPage.value + step))
  void loadOp()
}

function goLoginPage(step: number) {
  loginPage.value = Math.min(loginPageCount.value, Math.max(1, loginPage.value + step))
  void loadLogin()
}

const loadedTabs = new Set<TabId>()

function switchTab(next: TabId) {
  if (tab.value === next) return
  tab.value = next
  if (loadedTabs.has(next)) return
  loadedTabs.add(next)
  if (next === 'login') void loadLogin()
  else void loadOp()
}

onMounted(() => {
  loadedTabs.add('operation')
  void loadOp()
})
</script>

<style scoped>
.audit-body {
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
.filter-input {
  width: 132px;
}
.danger-text {
  color: var(--danger);
}
</style>
