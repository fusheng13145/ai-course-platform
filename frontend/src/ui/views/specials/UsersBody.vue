<template>
  <section class="users-body">
    <div v-if="listError" class="callout" role="alert">
      <span>用户列表</span>
      <p>{{ listError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="loadUsers()">
        {{ loading ? '重试中…' : '重试' }}
      </button>
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
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
          <button class="btn btn-sm btn-primary" @click="openCreate">新建用户</button>
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
                  <button class="text-btn" data-testid="user-edit" @click="openEdit(row)">
                    编辑
                  </button>
                  <button class="text-btn" data-testid="user-reset" @click="openReset(row)">
                    重置密码
                  </button>
                  <button
                    class="text-btn"
                    data-testid="user-toggle"
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
      </div>

      <footer v-if="!listError && total > pageSize" class="card-foot">
        <span>第 {{ page }} / {{ pageCount }} 页 · 共 {{ total }} 人</span>
        <Pagination :page="page" :page-count="pageCount" :loading="loading" @turn="goPage" />
      </footer>
    </section>

    <Modal
      v-model:open="editorOpen"
      :title="editingId === null ? '新建用户' : '编辑用户'"
      width="640px"
      :close-on-backdrop="false"
    >
      <div class="form-grid">
        <label class="field">
          <span>用户名 *</span>
          <input
            v-model="form.username"
            class="input mono"
            data-testid="user-username"
            maxlength="20"
            placeholder="字母、数字或下划线，3–20 位"
          />
        </label>
        <label class="field">
          <span>姓名</span>
          <input v-model="form.fullName" class="input" data-testid="user-name" maxlength="50" />
        </label>
        <label class="field">
          <span>角色 *</span>
          <select v-model="form.role" class="input" data-testid="user-role">
            <option v-for="item in ROLE_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
        </label>
        <label v-if="editingId === null" class="field">
          <span>初始密码 *</span>
          <input
            v-model="form.password"
            class="input"
            data-testid="user-password"
            type="password"
            autocomplete="new-password"
            maxlength="32"
            placeholder="8–32 位，含大小写字母、数字、特殊字符"
          />
        </label>
        <label class="field">
          <span>邮箱</span>
          <input
            v-model="form.email"
            class="input"
            data-testid="user-email"
            type="email"
            maxlength="100"
          />
        </label>
        <label class="field">
          <span>手机号</span>
          <input
            v-model="form.phone"
            class="input"
            data-testid="user-phone"
            maxlength="11"
            placeholder="11 位手机号"
          />
        </label>
        <label v-if="form.role === 'STUDENT'" class="field">
          <span>行政班</span>
          <select v-model="form.adminClassId" class="input" data-testid="user-admin-class">
            <option value="">未分班</option>
            <option v-for="item in classrooms" :key="String(item.id)" :value="String(item.id)">
              {{ item.name }}{{ item.code ? ` · ${item.code}` : '' }}
            </option>
          </select>
        </label>
      </div>
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="formBusy" @click="closeEditor">取消</button>
        <button class="btn btn-primary" :disabled="formBusy" @click="submitEditor">
          {{ formBusy ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <Modal v-model:open="resetOpen" title="重置密码" width="480px" :close-on-backdrop="false">
      <p class="modal-text">
        为 <b>{{ resetUser?.fullName || resetUser?.username || '该账号' }}</b> 设置新密码。
      </p>
      <label class="field">
        <span>新密码 *</span>
        <input
          v-model="resetPassword"
          class="input"
          data-testid="user-new-password"
          type="password"
          autocomplete="new-password"
          maxlength="32"
          placeholder="8–32 位，含大小写字母、数字、特殊字符"
        />
      </label>
      <p v-if="resetError" class="form-error" role="alert">{{ resetError }}</p>
      <p class="modal-hint">保存后该账号的现有登录会话会失效。</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="resetBusy" @click="closeReset">取消</button>
        <button class="btn btn-primary" :disabled="resetBusy" @click="submitReset">
          {{ resetBusy ? '重置中…' : '重置密码' }}
        </button>
      </template>
    </Modal>

    <Modal v-model:open="confirmOpen" title="停用账号" width="460px" :close-on-backdrop="false">
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
import { computed, onMounted, reactive, ref } from 'vue'
import Icon from '../../components/Icon.vue'
import Modal from '../../components/Modal.vue'
import Pagination from '../../components/Pagination.vue'
import { useUi } from '../../composables/ui'
import { useListFeedback } from '@/composables/useListFeedback'
import {
  createUser,
  getOrgAdminClasses,
  getUsers,
  resetUserPassword,
  updateUser,
} from '@/api/admin'
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
  adminClassId?: number | string | null
  [key: string]: unknown
}

interface ClassroomOption {
  id?: number | string
  name?: string
  code?: string
  status?: string
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

const editorOpen = ref(false)
const editingId = ref<UserRow['id'] | null>(null)
const formBusy = ref(false)
const formError = ref('')
const form = reactive({
  username: '',
  fullName: '',
  role: 'STUDENT',
  email: '',
  phone: '',
  password: '',
  adminClassId: '',
})
const classrooms = ref<ClassroomOption[]>([])

const resetOpen = ref(false)
const resetUser = ref<UserRow | null>(null)
const resetPassword = ref('')
const resetBusy = ref(false)
const resetError = ref('')

function passwordIssue(value: string): string | null {
  if (value.length < 8 || value.length > 32) return '密码长度需为 8–32 位'
  if (!/[a-z]/.test(value)) return '密码需包含小写字母'
  if (!/[A-Z]/.test(value)) return '密码需包含大写字母'
  if (!/\d/.test(value)) return '密码需包含数字'
  const specialChars = '~!@#$%^&*()_+-={}[]:;"\'<>,.?/\\|`'
  if (![...value].some((char) => specialChars.includes(char))) return '密码需包含特殊字符'
  return null
}

function validateForm(): string | null {
  const username = form.username.trim()
  if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) return '用户名需为 3–20 位字母、数字或下划线'
  if (form.fullName.length > 50) return '姓名不能超过 50 个字符'
  if (!ROLE_OPTIONS.some((item) => item.value === form.role)) return '请选择角色'
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) return '邮箱格式不正确'
  if (form.phone && !/^1[3-9]\d{9}$/.test(form.phone)) return '手机号格式不正确'
  if (editingId.value === null) {
    const issue = passwordIssue(form.password)
    if (issue) return issue
  }
  return null
}

function openCreate() {
  editingId.value = null
  formError.value = ''
  form.username = ''
  form.fullName = ''
  form.role = 'STUDENT'
  form.email = ''
  form.phone = ''
  form.password = ''
  form.adminClassId = ''
  editorOpen.value = true
}

function openEdit(row: UserRow) {
  editingId.value = row.id ?? null
  formError.value = ''
  form.username = String(row.username || '')
  form.fullName = String(row.fullName || '')
  form.role = String(row.role || 'STUDENT').toUpperCase()
  form.email = String(row.email || '')
  form.phone = String(row.phone || '')
  form.password = ''
  form.adminClassId = row.adminClassId == null ? '' : String(row.adminClassId)
  editorOpen.value = true
}

function closeEditor() {
  if (formBusy.value) return
  editorOpen.value = false
  formError.value = ''
}

async function submitEditor() {
  if (formBusy.value) return
  const issue = validateForm()
  if (issue) {
    formError.value = issue
    return
  }
  formBusy.value = true
  formError.value = ''
  const editing = editingId.value
  const payload: Record<string, unknown> = {
    username: form.username.trim(),
    fullName: form.fullName.trim(),
    role: form.role,
    email: form.email.trim(),
    phone: form.phone.trim(),
  }
  if (form.role === 'STUDENT') {
    if (form.adminClassId) payload.adminClassId = Number(form.adminClassId)
    else if (editing !== null && editing !== undefined) payload.adminClassId = 0
  } else {
    payload.adminClassId = 0
  }
  try {
    if (editing === null || editing === undefined) {
      await createUser({ ...payload, password: form.password, isActive: true })
      toast('用户已创建', 'ok')
    } else {
      await updateUser(editing, payload)
      toast('用户已更新', 'ok')
    }
    editorOpen.value = false
    await loadUsers()
  } catch (error) {
    formError.value = errorText(error, '保存失败，请稍后重试')
  } finally {
    formBusy.value = false
  }
}

function openReset(row: UserRow) {
  resetUser.value = row
  resetPassword.value = ''
  resetError.value = ''
  resetOpen.value = true
}

function closeReset() {
  if (resetBusy.value) return
  resetOpen.value = false
  resetUser.value = null
  resetError.value = ''
}

async function submitReset() {
  if (resetBusy.value || !resetUser.value?.id) return
  const issue = passwordIssue(resetPassword.value)
  if (issue) {
    resetError.value = issue
    return
  }
  resetBusy.value = true
  resetError.value = ''
  try {
    await resetUserPassword(resetUser.value.id, { newPassword: resetPassword.value })
    toast('密码已重置', 'ok')
    resetOpen.value = false
    resetUser.value = null
  } catch (error) {
    resetError.value = errorText(error, '重置失败，请稍后重试')
  } finally {
    resetBusy.value = false
  }
}

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

async function loadClassrooms() {
  try {
    const payload = unwrap(await getOrgAdminClasses())
    classrooms.value = pickList<ClassroomOption>(payload).filter(
      (item) => String(item.status || 'ACTIVE').toUpperCase() !== 'ARCHIVED',
    )
  } catch {
    classrooms.value = []
  }
}

onMounted(() => {
  void loadUsers()
  void loadClassrooms()
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
  font-size: 12px;
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
