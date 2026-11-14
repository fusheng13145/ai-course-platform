<template>
  <section class="semesters-body">
    <div v-if="listError" class="callout" role="alert">
      <span>学期列表</span>
      <p>{{ listError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="loadSemesters()">
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
            学期列表 <span class="chip chip-mute">{{ semesters.length }} 个</span>
          </h2>
        </div>
        <div class="filter-row">
          <button class="btn btn-sm btn-ghost" :disabled="loading" @click="loadSemesters()">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
          <button class="btn btn-sm btn-primary" @click="openCreate">新建学期</button>
        </div>
      </header>

      <div v-if="semesters.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>学期名称</th>
              <th class="w-140">开始日期</th>
              <th class="w-140">结束日期</th>
              <th class="w-96">状态</th>
              <th class="w-220">归档时间</th>
              <th class="w-96 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in semesters" :key="String(row.id ?? index)">
              <td>
                <b>{{ row.name || '—' }}</b>
                <small class="cell-sub">ID {{ row.id ?? '—' }}</small>
              </td>
              <td>{{ formatDate(row.startDate) }}</td>
              <td>{{ formatDate(row.endDate) }}</td>
              <td>
                <span class="chip" :class="statusOf(row) === 'ARCHIVED' ? 'chip-warn' : 'chip-ok'">
                  {{ statusLabel(row.status) }}
                </span>
              </td>
              <td>{{ formatTime(row.archivedAt) }}</td>
              <td>
                <div class="row-actions">
                  <button
                    class="text-btn"
                    :disabled="statusOf(row) === 'ARCHIVED' || archivingId === row.id"
                    @click="askArchive(row)"
                  >
                    {{ archivingId === row.id ? '归档中…' : '归档' }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else-if="loading" class="empty-state compact">
        <b>正在加载学期…</b>
      </div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="calendar" /></span>
        <b>{{ emptyDescription() }}</b>
        <p>{{ listError ? '请查看上方提示后重试。' : '用右上角「新建学期」注册第一个学期。' }}</p>
      </div>

      <p class="hint">
        学期只支持新建与归档，不支持修改或删除；归档只改变学期状态，不冻结其下课程。
      </p>
    </section>

    <Modal v-model:open="createOpen" title="新建学期" width="560px" :close-on-backdrop="false">
      <div class="form-grid">
        <label class="field span-2">
          <span>学期名称 *</span>
          <input
            v-model="form.name"
            class="input"
            data-testid="semester-name"
            maxlength="64"
            placeholder="如：2026-2027-1"
          />
        </label>
        <label class="field">
          <span>开始日期 *</span>
          <input v-model="form.startDate" class="input" data-testid="semester-start" type="date" />
        </label>
        <label class="field">
          <span>结束日期 *</span>
          <input v-model="form.endDate" class="input" data-testid="semester-end" type="date" />
        </label>
      </div>
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      <p class="modal-hint">
        学期名唯一；结束日期不得早于开始日期。创建成功后课程归属不会被自动改动。
      </p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="submitting" @click="closeCreate">取消</button>
        <button class="btn btn-primary" :disabled="submitting" @click="submitCreate">
          {{ submitting ? '创建中…' : '创建学期' }}
        </button>
      </template>
    </Modal>

    <Modal v-model:open="archiveOpen" title="归档学期" width="460px" :close-on-backdrop="false">
      <p class="modal-text">
        确定归档学期 <b>{{ archiveTarget?.name || '该学期' }}</b> 吗？
      </p>
      <p class="modal-hint">归档只改变学期状态与列表分组，不会冻结、删除或改动任何课程。</p>
      <p v-if="archiveError" class="form-error" role="alert">{{ archiveError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="archiving" @click="closeArchive">取消</button>
        <button class="btn btn-danger" :disabled="archiving" @click="confirmArchive">
          {{ archiving ? '归档中…' : '确定归档' }}
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
import { archiveSemester, createSemester, getSemesters } from '@/api/admin'
import { pickList, unwrap } from '@/utils/response'
import type { Metric } from '../../nav'

interface SemesterRow {
  id?: number | string
  name?: string
  startDate?: string
  endDate?: string
  status?: string
  archivedAt?: string | null
  [key: string]: unknown
}

const { toast } = useUi()

const semesters = ref<SemesterRow[]>([])
const createOpen = ref(false)
const submitting = ref(false)
const formError = ref('')
const form = reactive({ name: '', startDate: '', endDate: '' })

const archiveOpen = ref(false)
const archiveTarget = ref<SemesterRow | null>(null)
const archiving = ref(false)
const archivingId = ref<SemesterRow['id'] | null>(null)
const archiveError = ref('')

const { loading, listError, run, emptyDescription } = useListFeedback({
  failMessage: '学期列表加载失败，请稍后重试。',
  toastMessage: '学期列表加载失败',
  emptyText: '尚未注册任何学期',
  errorEmptyText: '无法确认学期数据，请查看上方提示后重试',
  notify: (message) => toast(message, 'danger'),
})

const activeCount = computed(
  () => semesters.value.filter((row) => statusOf(row) === 'ACTIVE').length,
)
const archivedCount = computed(
  () => semesters.value.filter((row) => statusOf(row) === 'ARCHIVED').length,
)

const metrics = computed<Metric[]>(() => [
  {
    label: '学期总数',
    value: listError.value ? '—' : semesters.value.length,
    unit: '个',
    note: '学期注册表',
    icon: 'calendar',
  },
  {
    label: '在册',
    value: listError.value ? '—' : activeCount.value,
    unit: '个',
    note: '当前生效',
    icon: 'check',
    tone: 'ok',
  },
  {
    label: '已归档',
    value: listError.value ? '—' : archivedCount.value,
    unit: '个',
    note: '已结束存档',
    icon: 'lock',
    tone: 'warn',
  },
])

function statusOf(row: SemesterRow): string {
  return String(row.status || '').toUpperCase()
}

function statusLabel(status?: string): string {
  return status === 'ARCHIVED' ? '已归档' : status === 'ACTIVE' ? '在册' : status || '—'
}

function formatDate(value?: string | null): string {
  return value ? String(value).slice(0, 10) : '—'
}

function formatTime(value?: string | null): string {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

async function loadSemesters() {
  await run(async () => {
    const payload = unwrap(await getSemesters())
    if (!Array.isArray(payload)) throw new Error('学期列表响应格式无效')
    semesters.value = pickList<SemesterRow>(payload)
  })
}

function openCreate() {
  Object.assign(form, { name: '', startDate: '', endDate: '' })
  formError.value = ''
  createOpen.value = true
}

function closeCreate() {
  if (submitting.value) return
  createOpen.value = false
  formError.value = ''
}

async function submitCreate() {
  const name = form.name.trim()
  if (!name) {
    formError.value = '请填写学期名称'
    return
  }
  if (!form.startDate || !form.endDate) {
    formError.value = '请选择学期的开始与结束日期'
    return
  }
  if (form.endDate < form.startDate) {
    formError.value = '结束日期不得早于开始日期'
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    await createSemester({ name, startDate: form.startDate, endDate: form.endDate })
    createOpen.value = false
    toast('学期创建成功', 'ok')
    await loadSemesters()
  } catch (error) {
    formError.value = errorText(error, '创建学期失败，请检查输入后重试')
  } finally {
    submitting.value = false
  }
}

function askArchive(row: SemesterRow) {
  if (row.id === undefined || row.id === null || statusOf(row) === 'ARCHIVED') return
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
  archivingId.value = row.id
  archiveError.value = ''
  try {
    await archiveSemester(row.id)
    archiveOpen.value = false
    archiveTarget.value = null
    toast('学期已归档', 'ok')
    await loadSemesters()
  } catch (error) {
    archiveError.value = errorText(error, '归档失败，请稍后重试')
  } finally {
    archiving.value = false
    archivingId.value = null
  }
}

onMounted(() => {
  void loadSemesters()
})
</script>

<style scoped>
.semesters-body {
  display: grid;
  gap: 14px;
}
</style>
