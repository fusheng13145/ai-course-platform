<template>
  <section class="classes-body">
    <div v-if="listError" class="callout" role="alert">
      <span>班级列表</span>
      <p>{{ listError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="loadAll()">
        {{ loading ? '重试中…' : '重试' }}
      </button>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>
            行政班列表 <span class="chip chip-mute">{{ filtered.length }} 个</span>
          </h2>
        </div>
        <div class="filter-row class-tools">
          <form class="search-form" @submit.prevent="applySearch">
            <input
              v-model="keyword"
              class="input search-input"
              type="search"
              placeholder="搜索班级名称 / 编号"
              aria-label="搜索行政班"
            />
            <button class="btn btn-sm" type="submit" :disabled="loading">查询</button>
          </form>
          <button class="btn btn-sm btn-ghost" :disabled="loading" @click="loadAll()">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
          <button class="btn btn-sm btn-primary" @click="openCreate">新建行政班</button>
        </div>
      </header>

      <div v-if="paged.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>行政班</th>
              <th class="w-140">专业</th>
              <th class="w-80">年级</th>
              <th class="w-140">班主任</th>
              <th class="w-96">人数 / 上限</th>
              <th class="w-80">状态</th>
              <th class="w-140 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in paged" :key="String(row.id ?? index)">
              <td>
                <b>{{ row.name || '—' }}</b>
                <small class="cell-sub">{{ row.code || '—' }}</small>
              </td>
              <td>{{ majorName(row.majorId) }}</td>
              <td>{{ row.gradeYear || '—' }}</td>
              <td>{{ advisorName(row.advisorTeacherId) }}</td>
              <td>{{ memberCount(row.id) }} / {{ row.maxStudents ?? '—' }}</td>
              <td>
                <span class="chip" :class="statusOf(row) === 'ACTIVE' ? 'chip-ok' : 'chip-mute'">
                  {{ statusOf(row) === 'ACTIVE' ? '启用' : '已归档' }}
                </span>
              </td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="openMembers(row)">成员</button>
                  <button class="text-btn" @click="openEdit(row)">编辑</button>
                  <button
                    v-if="statusOf(row) === 'ACTIVE'"
                    class="text-btn danger"
                    :disabled="memberCount(row.id) > 0"
                    @click="askArchive(row)"
                  >
                    归档
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else-if="loading" class="empty-state compact">
        <b>正在加载行政班…</b>
      </div>
      <div v-else class="empty-state compact">
        <b>{{ keyword.trim() ? '没有匹配的行政班' : emptyDescription() }}</b>
      </div>

      <footer v-if="showPager" class="card-foot">
        <span>第 {{ page }} / {{ pageCount }} 页 · 共 {{ total }} 个</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1" @click="onPageChange(page - 1)">
            上一页
          </button>
          <button class="btn btn-sm" :disabled="page >= pageCount" @click="onPageChange(page + 1)">
            下一页
          </button>
        </div>
      </footer>
    </section>

    <Modal
      v-model:open="editorOpen"
      :title="editingId === null ? '新建行政班' : '编辑行政班'"
      width="640px"
      :close-on-backdrop="false"
    >
      <div class="form-grid">
        <label class="field">
          <span>班级名称 *</span>
          <input v-model="form.name" class="input" data-testid="class-name" maxlength="100" />
        </label>
        <label class="field">
          <span>班级编号 *</span>
          <input v-model="form.code" class="input mono" data-testid="class-code" maxlength="50" />
        </label>
        <label class="field">
          <span>学院 *</span>
          <select
            v-model="form.collegeId"
            class="input"
            data-testid="class-college"
            :disabled="editingId !== null"
            @change="onCollegeChange"
          >
            <option value="" disabled>选择学院</option>
            <option v-for="item in colleges" :key="String(item.id)" :value="String(item.id)">
              {{ item.name }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>专业 *</span>
          <select
            v-model="form.majorId"
            class="input"
            data-testid="class-major"
            :disabled="editingId !== null"
          >
            <option value="" disabled>选择专业</option>
            <option v-for="item in formMajors" :key="String(item.id)" :value="String(item.id)">
              {{ item.name }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>年级</span>
          <input v-model="form.gradeYear" class="input" data-testid="class-grade" maxlength="10" />
        </label>
        <label class="field">
          <span>班主任</span>
          <select v-model="form.advisorTeacherId" class="input" data-testid="class-advisor">
            <option value="">未指定</option>
            <option v-for="item in teachers" :key="String(item.id)" :value="String(item.id)">
              {{ item.name }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>人数上限</span>
          <input
            v-model.number="form.maxStudents"
            class="input"
            data-testid="class-max"
            type="number"
            min="1"
          />
        </label>
      </div>
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="saving" @click="closeEditor">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submitEditor">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <Modal
      v-model:open="membersOpen"
      :title="`${memberTarget?.name || '行政班'} · 成员`"
      width="720px"
    >
      <div class="member-tools">
        <input
          v-model="memberKeyword"
          class="input search-input"
          type="search"
          placeholder="搜索学生"
          aria-label="搜索行政班成员"
        />
        <span class="chip chip-mute">{{ visibleMembers.length }} 人</span>
      </div>
      <div v-if="visibleMembers.length" class="table-wrap member-table">
        <table>
          <thead>
            <tr>
              <th>学生</th>
              <th class="w-140">学号 / 用户名</th>
              <th class="w-80 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="member in visibleMembers" :key="String(member.id)">
              <td>
                <b>{{ member.fullName || member.username || `学生 #${member.id}` }}</b>
              </td>
              <td>{{ member.studentNo || member.username || '—' }}</td>
              <td>
                <button
                  class="text-btn danger"
                  :disabled="removingId === String(member.id)"
                  @click="askRemove(member)"
                >
                  移出
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state compact">
        <b>{{ memberKeyword.trim() ? '没有匹配的学生' : '该行政班暂无学生' }}</b>
      </div>
    </Modal>

    <Modal v-model:open="archiveOpen" title="归档行政班" width="460px" :close-on-backdrop="false">
      <p class="modal-text">
        确定归档 <b>{{ archiveTarget?.name || '该行政班' }}</b> 吗？
      </p>
      <p v-if="archiveError" class="form-error" role="alert">{{ archiveError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="archiving" @click="closeArchive">取消</button>
        <button class="btn btn-danger" :disabled="archiving" @click="confirmArchive">
          {{ archiving ? '归档中…' : '确认归档' }}
        </button>
      </template>
    </Modal>

    <Modal v-model:open="removeOpen" title="移出学生" width="460px" :close-on-backdrop="false">
      <p class="modal-text">
        确定将 <b>{{ removeTarget?.fullName || removeTarget?.username || '该学生' }}</b>
        移出行政班吗？
      </p>
      <p v-if="removeError" class="form-error" role="alert">{{ removeError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="removing" @click="closeRemove">取消</button>
        <button class="btn btn-danger" :disabled="removing" @click="confirmRemove">
          {{ removing ? '移出中…' : '确认移出' }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Modal from '../../components/Modal.vue'
import Stat from '../../components/Stat.vue'
import { useUi } from '../../composables/ui'
import { useListFeedback } from '@/composables/useListFeedback'
import { useClientPagination } from '@/composables/useClientPagination'
import {
  archiveOrgAdminClass,
  createOrgAdminClass,
  getAllUsers,
  getColleges,
  getMajors,
  getOrgAdminClasses,
  updateOrgAdminClass,
  updateUser,
} from '@/api/admin'
import { pickList, unwrap } from '@/utils/response'
import type { Metric } from '../../nav'

interface AdminClassRow {
  id?: number | string
  name?: string
  code?: string
  gradeYear?: string
  collegeId?: number | string | null
  majorId?: number | string | null
  advisorTeacherId?: number | string | null
  maxStudents?: number | null
  status?: string
  [key: string]: unknown
}

interface OrgOption {
  id?: number | string
  name?: string
  collegeId?: number | string | null
}

interface DirectoryUser {
  id?: number | string
  username?: string
  fullName?: string
  studentNo?: string
  role?: string
  adminClassId?: number | string | null
}

interface MemberRow extends DirectoryUser {
  id?: number | string
}

const { toast } = useUi()

const rows = ref<AdminClassRow[]>([])
const users = ref<DirectoryUser[]>([])
const colleges = ref<OrgOption[]>([])
const majors = ref<OrgOption[]>([])
const keyword = ref('')
const appliedKeyword = ref('')

const filtered = computed(() => {
  const key = appliedKeyword.value.trim().toLowerCase()
  if (!key) return rows.value
  return rows.value.filter((row) =>
    [row.name, row.code, row.gradeYear].some((value) =>
      String(value ?? '')
        .toLowerCase()
        .includes(key),
    ),
  )
})

const { page, pageSize, total, paged, showPager, onPageChange, reset } =
  useClientPagination<AdminClassRow>(() => filtered.value, { pageSize: 20 })
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const editorOpen = ref(false)
const editingId = ref<string | number | null>(null)
const saving = ref(false)
const formError = ref('')
const form = reactive({
  name: '',
  code: '',
  collegeId: '',
  majorId: '',
  gradeYear: '',
  advisorTeacherId: '',
  maxStudents: 50,
  status: 'ACTIVE',
})

const membersOpen = ref(false)
const memberTarget = ref<AdminClassRow | null>(null)
const memberKeyword = ref('')
const removingId = ref('')
const removeOpen = ref(false)
const removeTarget = ref<MemberRow | null>(null)
const removing = ref(false)
const removeError = ref('')

const archiveOpen = ref(false)
const archiveTarget = ref<AdminClassRow | null>(null)
const archiving = ref(false)
const archiveError = ref('')

const { loading, listError, run, emptyDescription } = useListFeedback({
  failMessage: '行政班列表加载失败，请稍后重试。',
  toastMessage: '行政班列表加载失败',
  emptyText: '暂无行政班',
  errorEmptyText: '无法确认行政班数据，请查看上方提示后重试',
  notify: (message) => toast(message, 'danger'),
})

const teachers = computed(() =>
  users.value
    .filter((user) => String(user.role || '').toUpperCase() === 'TEACHER')
    .map((user) => ({
      id: user.id,
      name: String(user.fullName || user.username || `教师 #${user.id}`),
    })),
)

const formMajors = computed(() =>
  majors.value.filter((item) => String(item.collegeId ?? '') === String(form.collegeId)),
)

const memberMembers = computed(() =>
  users.value.filter(
    (user) =>
      String(user.role || '').toUpperCase() === 'STUDENT' &&
      String(user.adminClassId ?? '') === String(memberTarget.value?.id ?? ''),
  ),
)

const visibleMembers = computed(() => {
  const key = memberKeyword.value.trim().toLowerCase()
  if (!key) return memberMembers.value
  return memberMembers.value.filter((user) =>
    [user.fullName, user.username, user.studentNo].some((value) =>
      String(value ?? '')
        .toLowerCase()
        .includes(key),
    ),
  )
})

const memberTotal = computed(() => rows.value.reduce((sum, row) => sum + memberCount(row.id), 0))
const majorTotal = computed(
  () => new Set(rows.value.map((row) => String(row.majorId ?? '')).filter(Boolean)).size,
)

const metrics = computed<Metric[]>(() => [
  {
    label: '行政班总数',
    value: listError.value ? '—' : rows.value.length,
    unit: '个',
    note: '全部',
    icon: 'building',
  },
  {
    label: '在册学生',
    value: listError.value ? '—' : memberTotal.value,
    unit: '人',
    note: '按行政班归属',
    icon: 'users',
    tone: 'ok',
  },
  {
    label: '专业数',
    value: listError.value ? '—' : majorTotal.value,
    unit: '个',
    note: '按专业去重',
    icon: 'layers',
    tone: 'info',
  },
  {
    label: '平均班额',
    value: listError.value
      ? '—'
      : rows.value.length
        ? Math.round(memberTotal.value / rows.value.length)
        : 0,
    unit: '人',
    note: '学生数 / 班级数',
    icon: 'chart',
  },
])

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

function statusOf(row: AdminClassRow): string {
  return String(row.status || 'ACTIVE').toUpperCase()
}

function majorName(id: unknown): string {
  const item = majors.value.find((major) => String(major.id) === String(id ?? ''))
  return item?.name ? String(item.name) : '—'
}

function advisorName(id: unknown): string {
  if (id === null || id === undefined || id === '') return '未指定'
  const user = users.value.find((item) => String(item.id) === String(id))
  return user ? String(user.fullName || user.username || `教师 #${id}`) : `教师 #${id}`
}

function memberCount(id: unknown): number {
  if (id === null || id === undefined) return 0
  return users.value.filter(
    (user) =>
      String(user.role || '').toUpperCase() === 'STUDENT' &&
      String(user.adminClassId ?? '') === String(id),
  ).length
}

async function loadAll() {
  await run(async () => {
    const [classes, directory] = await Promise.all([getOrgAdminClasses(), getAllUsers({})])
    rows.value = pickList<AdminClassRow>(unwrap(classes)).filter(Boolean)
    users.value = (directory || []) as DirectoryUser[]
  })
}

async function loadOrgOptions() {
  try {
    const [collegePayload, majorPayload] = await Promise.all([getColleges(), getMajors({})])
    colleges.value = pickList<OrgOption>(unwrap(collegePayload))
    majors.value = pickList<OrgOption>(unwrap(majorPayload))
  } catch {
    colleges.value = []
    majors.value = []
  }
}

function applySearch() {
  appliedKeyword.value = keyword.value
  reset()
}

function openCreate() {
  editingId.value = null
  const firstCollege = colleges.value[0]
  const firstMajor = majors.value.find(
    (item) => String(item.collegeId ?? '') === String(firstCollege?.id ?? ''),
  )
  Object.assign(form, {
    name: '',
    code: '',
    collegeId: firstCollege?.id == null ? '' : String(firstCollege.id),
    majorId: firstMajor?.id == null ? '' : String(firstMajor.id),
    gradeYear: String(new Date().getFullYear()),
    advisorTeacherId: '',
    maxStudents: 50,
    status: 'ACTIVE',
  })
  formError.value = ''
  editorOpen.value = true
}

function openEdit(row: AdminClassRow) {
  editingId.value = row.id ?? null
  Object.assign(form, {
    name: row.name || '',
    code: row.code || '',
    collegeId: row.collegeId == null ? '' : String(row.collegeId),
    majorId: row.majorId == null ? '' : String(row.majorId),
    gradeYear: row.gradeYear || '',
    advisorTeacherId: row.advisorTeacherId == null ? '' : String(row.advisorTeacherId),
    maxStudents: row.maxStudents ?? 50,
    status: statusOf(row),
  })
  formError.value = ''
  editorOpen.value = true
}

function onCollegeChange() {
  if (!formMajors.value.some((item) => String(item.id) === String(form.majorId))) {
    form.majorId = formMajors.value[0]?.id == null ? '' : String(formMajors.value[0].id)
  }
}

function closeEditor() {
  if (saving.value) return
  editorOpen.value = false
  formError.value = ''
}

async function submitEditor() {
  const name = form.name.trim()
  const code = form.code.trim()
  if (!name) {
    formError.value = '请填写班级名称'
    return
  }
  if (!code) {
    formError.value = '请填写班级编号'
    return
  }
  if (!form.collegeId) {
    formError.value = '请选择学院'
    return
  }
  if (!form.majorId) {
    formError.value = '请选择专业'
    return
  }
  const maxStudents = Number(form.maxStudents)
  if (!Number.isInteger(maxStudents) || maxStudents < 1) {
    formError.value = '人数上限必须是正整数'
    return
  }
  const payload: Record<string, unknown> = {
    collegeId: Number(form.collegeId),
    majorId: Number(form.majorId),
    name,
    code,
    gradeYear: form.gradeYear.trim(),
    advisorTeacherId: form.advisorTeacherId ? Number(form.advisorTeacherId) : null,
    maxStudents,
    status: form.status,
  }
  saving.value = true
  formError.value = ''
  try {
    if (editingId.value === null) {
      await createOrgAdminClass(payload)
      toast('行政班已创建', 'ok')
    } else {
      await updateOrgAdminClass(editingId.value, payload)
      toast('行政班已更新', 'ok')
    }
    editorOpen.value = false
    await loadAll()
  } catch (error) {
    formError.value = errorText(error, '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function openMembers(row: AdminClassRow) {
  memberTarget.value = row
  memberKeyword.value = ''
  membersOpen.value = true
}

function askRemove(member: MemberRow) {
  removeTarget.value = member
  removeError.value = ''
  removeOpen.value = true
}

function closeRemove() {
  if (removing.value) return
  removeOpen.value = false
  removeTarget.value = null
  removeError.value = ''
}

async function confirmRemove() {
  const member = removeTarget.value
  if (!member || member.id === undefined || member.id === null) return
  removing.value = true
  removingId.value = String(member.id)
  removeError.value = ''
  try {
    await updateUser(member.id, { adminClassId: 0 })
    removeOpen.value = false
    removeTarget.value = null
    await loadAll()
    toast('已移出行政班', 'ok')
  } catch (error) {
    removeError.value = errorText(error, '移出失败，请稍后重试')
  } finally {
    removing.value = false
    removingId.value = ''
  }
}

function askArchive(row: AdminClassRow) {
  if (row.id === undefined || row.id === null || memberCount(row.id) > 0) return
  archiveTarget.value = row
  archiveError.value = ''
  archiveOpen.value = true
}

function closeArchive() {
  if (archiving.value) return
  archiveOpen.value = false
  archiveTarget.value = null
  archiveError.value = ''
}

async function confirmArchive() {
  const row = archiveTarget.value
  if (!row || row.id === undefined || row.id === null) return
  archiving.value = true
  archiveError.value = ''
  try {
    await archiveOrgAdminClass(row.id)
    archiveOpen.value = false
    archiveTarget.value = null
    toast('行政班已归档', 'ok')
    await loadAll()
  } catch (error) {
    archiveError.value = errorText(error, '归档失败，请稍后重试')
  } finally {
    archiving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadOrgOptions(), loadAll()])
})
</script>

<style scoped>
.classes-body {
  display: grid;
  gap: 14px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.search-form {
  display: flex;
  gap: 6px;
}
.search-input {
  width: 220px;
  height: 30px;
}
.member-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.member-table {
  max-height: 360px;
  overflow: auto;
}
</style>
