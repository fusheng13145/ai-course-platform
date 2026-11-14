<template>
  <section class="roles-body">
    <div v-if="listError" class="callout" role="alert">
      <span>权限列表</span>
      <p>{{ listError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="loadAll()">
        {{ loading ? '重试中…' : '重试' }}
      </button>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <div class="roles-layout">
      <section class="card role-card">
        <header class="card-head">
          <div>
            <h2>角色</h2>
          </div>
        </header>
        <ul class="role-list">
          <li v-for="item in roleEntries" :key="item.value">
            <button
              class="role-item"
              :class="{ 'is-active': selectedRole === item.value }"
              :aria-pressed="selectedRole === item.value"
              @click="selectedRole = item.value"
            >
              <span class="role-name">
                <b>{{ item.label }}</b>
                <small>{{ item.value }}</small>
              </span>
              <span class="chip chip-mute">{{ listError ? '—' : `${item.count} 项` }}</span>
            </button>
          </li>
        </ul>
        <p class="hint">
          角色取自平台角色常量 ADMIN / TEACHER / STUDENT；每行权限对应 role_permissions
          表的一条真实配置。
        </p>
      </section>

      <section class="card table-card">
        <header class="card-head">
          <div>
            <h2>
              {{ selectedLabel }}权限
              <span class="chip chip-mute">{{ roleRows.length }} 项</span>
            </h2>
          </div>
          <button class="btn btn-sm" :disabled="loading" @click="openAssign">分配权限</button>
        </header>

        <div v-if="roleRows.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>权限名称</th>
                <th class="w-140">权限 Key</th>
                <th class="w-80">类型</th>
                <th>资源 / 接口路径</th>
                <th class="w-140">上级权限</th>
                <th class="w-80">状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in roleRows" :key="String(row.id ?? index)">
                <td>
                  <b>{{ row.permissionName || '—' }}</b>
                  <small class="cell-sub">{{ row.description || '—' }}</small>
                </td>
                <td class="mono">{{ row.permissionKey || '—' }}</td>
                <td>{{ permissionTypeLabel(row.permissionType) }}</td>
                <td class="mono">{{ row.resourcePath || '—' }}</td>
                <td>{{ parentName(row) }}</td>
                <td>
                  <span class="chip" :class="activeOf(row) ? 'chip-ok' : 'chip-mute'">
                    {{ activeOf(row) ? '已启用' : '已停用' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-else-if="loading" class="empty-state compact">
          <b>正在加载权限…</b>
        </div>
        <div v-else class="empty-state compact">
          <span class="e-ico"><Icon name="shield" /></span>
          <b>{{ emptyDescription() }}</b>
          <p>
            {{
              listError
                ? '请查看上方提示后重试。'
                : '该角色尚未配置权限，可用右上角「分配权限」写入第一条。'
            }}
          </p>
        </div>

        <p class="hint">本页只维护资源级权限，不提供菜单或按钮级权限项。</p>
      </section>
    </div>

    <Modal v-model:open="assignOpen" title="分配权限" width="640px" :close-on-backdrop="false">
      <p class="modal-text">
        为 <b>{{ selectedLabel }}</b> 写入一条 role_permissions 记录。角色与权限 Key 组合唯一。
      </p>
      <div class="form-grid">
        <label class="field">
          <span>权限名称 *</span>
          <input
            v-model="form.permissionName"
            class="input"
            data-testid="role-assign-name"
            placeholder="如：课程审批"
          />
        </label>
        <label class="field">
          <span>权限 Key *</span>
          <input
            v-model="form.permissionKey"
            class="input mono"
            data-testid="role-assign-key"
            placeholder="如：course:approve"
          />
        </label>
        <label class="field">
          <span>权限类型</span>
          <select v-model="form.permissionType" class="input">
            <option value="MENU">菜单（MENU）</option>
            <option value="BUTTON">按钮（BUTTON）</option>
          </select>
        </label>
        <label class="field">
          <span>排序序号</span>
          <input v-model.number="form.sortOrder" class="input" type="number" min="0" />
        </label>
        <label class="field">
          <span>资源 / 接口路径</span>
          <input
            v-model="form.resourcePath"
            class="input mono"
            placeholder="如：/courses/approve"
          />
        </label>
        <label class="field">
          <span>上级权限</span>
          <select v-model="form.parentId" class="input">
            <option value="">无（顶级权限）</option>
            <option v-for="row in roleRows" :key="String(row.id)" :value="String(row.id)">
              {{ row.permissionName || row.permissionKey }}
            </option>
          </select>
        </label>
        <label class="field span-2">
          <span>描述</span>
          <textarea
            v-model="form.description"
            class="input textarea"
            rows="2"
            placeholder="可选：说明该权限的边界"
          ></textarea>
        </label>
      </div>
      <label class="active-line">
        <input v-model="form.isActive" type="checkbox" />
        创建后立即启用
      </label>
      <p v-if="assignError" class="form-error" role="alert">{{ assignError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="assignBusy" @click="closeAssign">取消</button>
        <button class="btn btn-primary" :disabled="assignBusy" @click="submitAssign">
          {{ assignBusy ? '提交中…' : '确认分配' }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Icon from '../../components/Icon.vue'
import Modal from '../../components/Modal.vue'
import Stat from '../../components/Stat.vue'
import { useUi } from '../../composables/ui'
import { useListFeedback } from '@/composables/useListFeedback'
import { createPermission, getAllPermissions } from '@/api/permission'
import { pickList, unwrap } from '@/utils/response'
import type { Metric } from '../../nav'

interface PermissionRow {
  id?: number | string
  role?: string
  permissionKey?: string
  permissionName?: string
  permissionType?: string
  resourcePath?: string
  parentId?: number | string | null
  sortOrder?: number | null
  isActive?: boolean | number
  description?: string
  [key: string]: unknown
}

const ROLE_CATALOGUE = [
  { value: 'ADMIN', label: '管理员' },
  { value: 'TEACHER', label: '教师' },
  { value: 'STUDENT', label: '学生' },
]
const TYPE_LABELS: Record<string, string> = { MENU: '菜单', BUTTON: '按钮' }

const { toast } = useUi()

const allRows = ref<PermissionRow[]>([])
const selectedRole = ref('ADMIN')
const assignOpen = ref(false)
const assignBusy = ref(false)
const assignError = ref('')
const form = reactive({
  permissionName: '',
  permissionKey: '',
  permissionType: 'MENU',
  resourcePath: '',
  parentId: '',
  sortOrder: null as number | null,
  description: '',
  isActive: true,
})

const { loading, listError, run, emptyDescription } = useListFeedback({
  failMessage: '权限列表加载失败，请稍后重试。',
  toastMessage: '权限列表加载失败',
  emptyText: '该角色暂无权限配置',
  errorEmptyText: '无法确认权限数据，请查看上方提示后重试',
  notify: (message) => toast(message, 'danger'),
})

const roleEntries = computed(() =>
  ROLE_CATALOGUE.map((item) => ({
    ...item,
    count: allRows.value.filter((row) => roleOf(row) === item.value).length,
  })),
)

const selectedLabel = computed(
  () =>
    ROLE_CATALOGUE.find((item) => item.value === selectedRole.value)?.label || selectedRole.value,
)

const roleRows = computed(() => allRows.value.filter((row) => roleOf(row) === selectedRole.value))

const metrics = computed<Metric[]>(() => {
  const enabled = allRows.value.filter((row) => activeOf(row)).length
  return [
    {
      label: '角色数',
      value: listError.value ? '—' : roleEntries.value.length,
      unit: '个',
      note: '平台角色常量',
      icon: 'users',
    },
    {
      label: '权限项',
      value: listError.value ? '—' : allRows.value.length,
      unit: '项',
      note: 'role_permissions 全量',
      icon: 'shield',
    },
    {
      label: '已启用',
      value: listError.value ? '—' : enabled,
      unit: '项',
      note: '当前全量',
      icon: 'check',
      tone: 'ok',
    },
    {
      label: selectedLabel.value,
      value: listError.value ? '—' : roleRows.value.length,
      unit: '项',
      note: '当前角色',
      icon: 'lock',
      tone: 'info',
    },
  ]
})

function roleOf(row: PermissionRow): string {
  return String(row.role || '').toUpperCase()
}

function activeOf(row: PermissionRow): boolean {
  return row.isActive !== false && row.isActive !== 0
}

function permissionTypeLabel(type?: string): string {
  const key = String(type || '').toUpperCase()
  return TYPE_LABELS[key] || key || '—'
}

function parentName(row: PermissionRow): string {
  if (
    row.parentId === null ||
    row.parentId === undefined ||
    row.parentId === '' ||
    row.parentId === 0
  ) {
    return '顶级'
  }
  const parent = roleRows.value.find((item) => String(item.id) === String(row.parentId))
  return parent?.permissionName || parent?.permissionKey || `#${row.parentId}`
}

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

async function loadAll() {
  await run(async () => {
    const payload = unwrap(await getAllPermissions())
    if (!Array.isArray(payload)) throw new Error('权限列表响应格式无效')
    allRows.value = pickList<PermissionRow>(payload)
  })
}

function openAssign() {
  assignError.value = ''
  Object.assign(form, {
    permissionName: '',
    permissionKey: '',
    permissionType: 'MENU',
    resourcePath: '',
    parentId: '',
    sortOrder: null,
    description: '',
    isActive: true,
  })
  assignOpen.value = true
}

function closeAssign() {
  if (assignBusy.value) return
  assignOpen.value = false
  assignError.value = ''
}

async function submitAssign() {
  const name = form.permissionName.trim()
  const key = form.permissionKey.trim()
  if (!name) {
    assignError.value = '请填写权限名称'
    return
  }
  if (!key) {
    assignError.value = '请填写权限 Key'
    return
  }
  if (roleRows.value.some((row) => String(row.permissionKey || '') === key)) {
    assignError.value = '该角色已拥有相同权限 Key，请勿重复分配'
    return
  }
  assignBusy.value = true
  assignError.value = ''
  try {
    await createPermission({
      role: selectedRole.value,
      permissionName: name,
      permissionKey: key,
      permissionType: form.permissionType || 'MENU',
      resourcePath: form.resourcePath.trim() || undefined,
      parentId: form.parentId ? Number(form.parentId) : undefined,
      sortOrder: form.sortOrder === null ? undefined : Number(form.sortOrder),
      description: form.description.trim() || undefined,
      isActive: form.isActive,
    })
    assignOpen.value = false
    toast('权限已分配', 'ok')
    await loadAll()
  } catch (error) {
    assignError.value = errorText(error, '分配失败，请稍后重试')
  } finally {
    assignBusy.value = false
  }
}

onMounted(() => {
  void loadAll()
})
</script>

<style scoped>
.roles-body {
  display: grid;
  gap: 14px;
}
.roles-layout {
  display: grid;
  grid-template-columns: minmax(220px, 0.7fr) minmax(0, 2fr);
  gap: 14px;
  align-items: start;
}
.role-list {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.role-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-ctl);
  background: var(--surface-2);
  text-align: left;
  transition:
    border-color 0.2s,
    background 0.2s;
}
.role-item:hover {
  border-color: var(--line-strong);
}
.role-item.is-active {
  border-color: var(--accent);
  background: var(--accent-soft);
}
.role-name {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}
.role-name b {
  font-size: 12px;
}
.role-name small {
  font-family: var(--font-mono);
  font-size: 8px;
  color: var(--ink-3);
}
.active-line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  font-size: 11px;
  color: var(--ink-2);
}
@media (max-width: 900px) {
  .roles-layout {
    grid-template-columns: 1fr;
  }
}
</style>
