<template>
  <section class="users-body">
    <div v-if="listError" class="callout" role="alert">
      <span>ERROR / 用户列表</span>
      <p>{{ listError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="loadUsers()">
        {{ loading ? '重试中…' : '重试' }}
      </button>
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">ACCOUNTS / SCHOOL-WIDE</p>
          <h2>
            用户与账号 <span class="chip chip-mute">共 {{ total }} 人</span>
          </h2>
        </div>
        <div class="filter-row user-tools">
          <label class="role-field">
            <span>角色</span>
            <select
              v-model="roleFilter"
              class="input role-select"
              aria-label="按角色筛选"
              @change="applyFilters"
            >
              <option value="">全部角色</option>
              <option v-for="item in ROLE_OPTIONS" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </label>
          <form class="search-form" @submit.prevent="applyFilters">
            <input
              v-model="keyword"
              class="input search-input"
              type="search"
              placeholder="搜索姓名 / 账号 / 邮箱"
              aria-label="搜索姓名、账号或邮箱"
            />
            <button class="btn btn-sm" type="submit" :disabled="loading">查询</button>
          </form>
          <button class="btn btn-sm btn-ghost" :disabled="loading" @click="loadUsers(true)">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>

      <div v-if="rows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>姓名 / 账号</th>
              <th class="w-96">角色</th>
              <th>邮箱</th>
              <th class="w-140">手机号</th>
              <th class="w-80">状态</th>
              <th class="w-96 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in rows" :key="String(row.id ?? index)">
              <td>
                <b>{{ row.fullName || row.username || '—' }}</b>
                <small class="cell-sub">{{ row.username || '—' }}</small>
              </td>
              <td>
                <span class="chip" :class="roleChip(row.role)">{{ roleLabel(row.role) }}</span>
              </td>
              <td>{{ row.email || '—' }}</td>
              <td>{{ row.phone || '—' }}</td>
              <td>
                <span class="chip" :class="activeOf(row) ? 'chip-ok' : 'chip-mute'">
                  {{ activeOf(row) ? '启用' : '停用' }}
                </span>
              </td>
              <td>
                <div class="row-actions">
                  <button
                    class="text-btn"
                    :class="{ danger: activeOf(row) }"
                    :disabled="togglingId === row.id"
                    @click="askToggle(row)"
                  >
                    {{
                      togglingId === row.id ? '处理中…' : activeOf(row) ? '停用账号' : '启用账号'
                    }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else-if="loading" class="empty-state compact">
        <b>正在加载用户…</b>
      </div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="users" /></span>
        <b>{{ emptyDescription() }}</b>
        <p>{{ listError ? '请查看上方提示后重试。' : '调整角色或搜索词后再试。' }}</p>
      </div>

      <footer v-if="!listError && total > pageSize" class="card-foot">
        <span>第 {{ page }} / {{ pageCount }} 页 · 共 {{ total }} 人</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1 || loading" @click="goPage(-1)">
            上一页
          </button>
          <button class="btn btn-sm" :disabled="page >= pageCount || loading" @click="goPage(1)">
            下一页
          </button>
        </div>
      </footer>

      <p class="hint">
        列表与筛选走 GET /admin/users（角色、关键词由服务端过滤）；启用 / 停用走 PATCH
        /users/{id}/status，停用前二次确认。
      </p>
    </section>

    <Modal
      v-model:open="confirmOpen"
      title="停用账号"
      kicker="CONFIRM / ACCOUNT"
      width="460px"
      :close-on-backdrop="false"
    >
      <p class="modal-text">
        停用后 <b>{{ confirmUser?.fullName || confirmUser?.username || '该账号' }}</b>
        将无法登录，历史学习与教学数据保留。
      </p>
      <p class="modal-hint">可随时在本页重新启用；停用不影响已产生的成绩与选课记录。</p>
      <p v-if="actionError" class="form-error" role="alert">{{ actionError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="actionBusy" @click="closeConfirm">取消</button>
        <button class="btn btn-danger" :disabled="actionBusy" @click="confirmDisable">
          {{ actionBusy ? '处理中…' : '确定停用' }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Icon from '../../components/Icon.vue'
import Modal from '../../components/Modal.vue'
import { useUi } from '../../composables/ui'
import { useListFeedback } from '@/composables/useListFeedback'
import { getUsers } from '@/api/admin'
import { updateUserStatus } from '@/api/user'
import { pickList, readTotal, unwrap } from '@/utils/response'

interface UserRow {
  id?: number | string
  username?: string
  fullName?: string
  email?: string
  phone?: string
  role?: string
  isActive?: boolean
  [key: string]: unknown
}

const ROLE_OPTIONS = [
  { value: 'ADMIN', label: '管理员' },
  { value: 'TEACHER', label: '教师' },
  { value: 'STUDENT', label: '学生' },
]
const ROLE_LABELS: Record<string, string> = {
  ADMIN: '管理员',
  TEACHER: '教师',
  STUDENT: '学生',
}

const { toast } = useUi()

const rows = ref<UserRow[]>([])
const keyword = ref('')
const roleFilter = ref('')
const page = ref(1)
const pageSize = 10
const total = ref(0)

const togglingId = ref<UserRow['id'] | null>(null)
const confirmOpen = ref(false)
const actionBusy = ref(false)
const actionError = ref('')
const confirmUser = ref<UserRow | null>(null)

const { loading, listError, run, emptyDescription } = useListFeedback({
  failMessage: '用户列表加载失败，请稍后重试。',
  toastMessage: '用户列表加载失败',
  emptyText: '暂无用户',
  errorEmptyText: '无法确认用户数据，请查看上方提示后重试',
  notify: (message) => toast(message, 'danger'),
})

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function roleLabel(role?: string): string {
  const key = String(role || '').toUpperCase()
  return ROLE_LABELS[key] || key || '—'
}

function roleChip(role?: string): string {
  const key = String(role || '').toUpperCase()
  if (key === 'ADMIN') return 'chip-danger'
  if (key === 'TEACHER') return 'chip-info'
  if (key === 'STUDENT') return 'chip-ok'
  return 'chip-mute'
}

function activeOf(row: UserRow): boolean {
  return row.isActive !== false
}

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

async function loadUsers(resetPage = false) {
  if (resetPage) page.value = 1
  await run(async () => {
    const payload = unwrap(
      await getUsers({
        page: page.value,
        size: pageSize,
        role: roleFilter.value || undefined,
        keyword: keyword.value.trim() || undefined,
      }),
    )
    if (!payload || typeof payload !== 'object') throw new Error('用户列表响应格式无效')
    const list = pickList<UserRow>(payload)
    rows.value = list
    total.value = readTotal(payload, list.length)
  })
}

function applyFilters() {
  void loadUsers(true)
}

function goPage(step: number) {
  page.value = Math.min(pageCount.value, Math.max(1, page.value + step))
  void loadUsers()
}

function askToggle(row: UserRow) {
  if (row.id === undefined || row.id === null) return
  if (!activeOf(row)) {
    void setStatus(row, true)
    return
  }
  confirmUser.value = row
  actionError.value = ''
  confirmOpen.value = true
}

function closeConfirm() {
  if (actionBusy.value) return
  confirmOpen.value = false
  confirmUser.value = null
  actionError.value = ''
}

async function confirmDisable() {
  if (!confirmUser.value) return
  await setStatus(confirmUser.value, false)
}

async function setStatus(row: UserRow, next: boolean) {
  if (row.id === undefined || row.id === null) return
  togglingId.value = row.id
  actionBusy.value = true
  actionError.value = ''
  try {
    await updateUserStatus(row.id, next)
    toast(next ? '账号已启用' : '账号已停用', 'ok')
    confirmOpen.value = false
    confirmUser.value = null
    await loadUsers()
  } catch (error) {
    const message = errorText(error, next ? '启用失败，请稍后重试' : '停用失败，请稍后重试')
    toast(message, 'danger')
    if (confirmOpen.value) actionError.value = message
  } finally {
    togglingId.value = null
    actionBusy.value = false
  }
}

onMounted(() => {
  void loadUsers()
})
</script>

<style scoped>
.users-body {
  display: grid;
  gap: 14px;
}
.user-tools {
  width: auto;
  justify-content: flex-end;
}
.role-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--ink-3);
}
.role-select {
  min-width: 110px;
  height: 30px;
}
.search-form {
  display: flex;
  gap: 6px;
}
.search-input {
  width: 220px;
  height: 30px;
}
</style>
