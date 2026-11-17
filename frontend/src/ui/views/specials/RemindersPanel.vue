<template>
  <section class="reminders-body">
    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>
            催交提醒 <span class="chip chip-mute">{{ total }} 条</span>
          </h2>
        </div>
        <div class="filter-row class-tools">
          <input
            v-model="keyword"
            class="input search-input"
            type="search"
            placeholder="搜索标题 / 内容 / 用户 ID"
            aria-label="搜索提醒"
          />
          <select v-model="status" class="input filter-select" aria-label="按状态筛选">
            <option value="">全部状态</option>
            <option value="PENDING">待发送</option>
            <option value="SENT">已发送</option>
          </select>
          <button class="btn btn-sm" :disabled="loading" @click="load">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
          <button class="btn btn-sm" :disabled="sending" @click="sendDueReminders">
            {{ sending ? '发送中…' : '发送到期' }}
          </button>
          <button class="btn btn-sm btn-primary" data-testid="reminder-new" @click="openCreate">
            新建提醒
          </button>
        </div>
      </header>

      <div v-if="listError" class="callout danger" role="alert">
        <span>提醒</span>
        <p>{{ listError }}</p>
        <button class="btn btn-sm" :disabled="loading" @click="load">重试</button>
      </div>
      <div v-else-if="loading && !rows.length" class="empty-state compact">
        <b>正在加载提醒…</b>
      </div>
      <div v-else-if="!rows.length" class="empty-state compact">
        <b>暂无催交提醒</b>
        <p>新建提醒后，到期时由「发送到期」或定时任务扇出到站内通知。</p>
      </div>
      <div v-else-if="!filtered.length" class="empty-state compact">
        <b>没有匹配的提醒</b>
        <p>换个关键词或状态再试。</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-96">状态</th>
              <th>标题 / 内容</th>
              <th class="w-140">接收人</th>
              <th class="w-180">类型 / 关联</th>
              <th class="w-180">计划发送</th>
              <th class="w-180">截止时间</th>
              <th class="w-140 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in paged" :key="String(row.id)">
              <td>
                <span class="chip" :class="statusClass(row.status)">
                  {{ statusLabel(row.status) }}
                </span>
              </td>
              <td>
                <b>{{ row.title || '—' }}</b>
                <small class="cell-sub">{{ brief(row.message) }}</small>
              </td>
              <td>#{{ row.userId ?? '—' }}</td>
              <td>
                {{ typeLabel(row.reminderType) }}
                <small v-if="row.courseId != null || row.taskId != null" class="cell-sub">
                  <span v-if="row.courseId != null">课程 #{{ row.courseId }}</span>
                  <span v-if="row.taskId != null"> · 任务 #{{ row.taskId }}</span>
                </small>
              </td>
              <td>{{ briefTime(row.scheduledAt) }}</td>
              <td>{{ briefTime(row.dueDate) }}</td>
              <td>
                <div class="row-actions">
                  <button
                    v-if="isPending(row)"
                    class="text-btn"
                    :disabled="acting === String(row.id)"
                    @click="openEdit(row)"
                  >
                    编辑
                  </button>
                  <button
                    v-if="isPending(row)"
                    class="text-btn danger"
                    :disabled="acting === String(row.id)"
                    @click="cancel(row)"
                  >
                    取消
                  </button>
                  <span v-if="!isPending(row)" class="cell-sub">—</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer v-if="showPager" class="card-foot">
        <span>第 {{ page }} 页 · 共 {{ total }} 条</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1" @click="onPageChange(page - 1)">
            上一页
          </button>
          <button
            class="btn btn-sm"
            :disabled="page >= Math.ceil(total / pageSize)"
            @click="onPageChange(page + 1)"
          >
            下一页
          </button>
        </div>
      </footer>
    </section>

    <section v-if="formOpen" class="card reminder-form-card">
      <header class="card-head">
        <div>
          <h2>{{ editingId ? '编辑提醒' : '新建催交提醒' }}</h2>
        </div>
        <div class="filter-row class-tools">
          <button class="btn btn-sm" @click="closeForm">关闭</button>
        </div>
      </header>

      <form class="form-grid reminder-grid" @submit.prevent="submit">
        <label class="field">
          <span>接收用户 ID *</span>
          <input
            v-model.number="form.targetUserId"
            class="input"
            type="number"
            min="1"
            data-testid="reminder-user"
            placeholder="例如 1001"
          />
        </label>

        <label v-if="!editingId" class="field">
          <span>提醒类型 *</span>
          <select v-model="form.reminderType" class="input" data-testid="reminder-type">
            <option value="BEFORE_DEADLINE">截止前提醒</option>
            <option value="DAILY_STUDY">每日学习提醒</option>
            <option value="NEW_CONTENT">新内容提醒</option>
          </select>
        </label>

        <label v-if="!editingId" class="field">
          <span>截止时间 *</span>
          <input
            v-model="form.dueDate"
            class="input"
            type="datetime-local"
            data-testid="reminder-due"
          />
        </label>

        <label v-if="!editingId" class="field">
          <span>提前触发天数 *</span>
          <input
            v-model.number="form.triggerDay"
            class="input"
            type="number"
            min="1"
            data-testid="reminder-trigger"
          />
        </label>

        <label v-else class="field">
          <span>计划发送时间 *</span>
          <input
            v-model="form.remindAt"
            class="input"
            type="datetime-local"
            data-testid="reminder-remind-at"
          />
        </label>

        <label v-if="!editingId" class="field">
          <span>关联课程 ID（可选）</span>
          <input v-model.number="form.courseId" class="input" type="number" min="1" />
        </label>

        <label v-if="!editingId" class="field">
          <span>关联任务 ID（可选）</span>
          <input v-model.number="form.taskId" class="input" type="number" min="1" />
        </label>

        <label class="field span-2">
          <span>标题 *</span>
          <input
            v-model="form.title"
            class="input"
            maxlength="100"
            data-testid="reminder-title"
            placeholder="例如：作业截止提醒"
          />
        </label>

        <label class="field span-2">
          <span>内容</span>
          <textarea
            v-model="form.message"
            class="input textarea"
            rows="3"
            maxlength="2000"
            data-testid="reminder-message"
            placeholder="例如：您的作业将于明天截止，请尽快提交。"
          ></textarea>
        </label>

        <p v-if="formError" class="form-error span-2" role="alert">{{ formError }}</p>

        <div class="form-actions span-2">
          <button class="btn btn-primary" type="submit" :disabled="submitting">
            {{ submitting ? '保存中…' : editingId ? '保存修改' : '创建提醒' }}
          </button>
          <span class="hint-inline">
            接收人按用户 ID 指定；创建后可在列表改期或取消，发送后不可再编辑。
          </span>
        </div>
      </form>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import Stat from '../../components/Stat.vue'
import { useUi } from '../../composables/ui'
import { useClientPagination } from '@/composables/useClientPagination'
import { cancelReminder, createReminder, getTodos, sendDue, updateReminder } from '@/api/reminder'
import { pickList, unwrap } from '@/utils/response'
import type { Metric } from '../../nav'

interface ReminderRow {
  id?: number | string
  reminderType?: string
  status?: string
  userId?: number | string
  taskId?: number | string | null
  courseId?: number | string | null
  title?: string
  message?: string
  dueDate?: string
  triggerDay?: number | null
  scheduledAt?: string
  sentAt?: string | null
}

const { toast } = useUi()

const rows = ref<ReminderRow[]>([])
const loading = ref(false)
const listError = ref('')
const keyword = ref('')
const status = ref('')
const acting = ref('')
const sending = ref(false)

const formOpen = ref(false)
const editingId = ref<number | string | null>(null)
const submitting = ref(false)
const formError = ref('')

const form = reactive({
  targetUserId: null as number | null,
  reminderType: 'BEFORE_DEADLINE',
  dueDate: '',
  triggerDay: 3,
  remindAt: '',
  courseId: null as number | null,
  taskId: null as number | null,
  title: '',
  message: '',
})

const filtered = computed(() => {
  const word = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    if (status.value && row.status !== status.value) return false
    if (!word) return true
    return [row.title, row.message, row.userId, row.courseId, row.taskId]
      .map((value) => String(value ?? '').toLowerCase())
      .some((value) => value.includes(word))
  })
})

const { page, pageSize, total, paged, showPager, onPageChange, reset } =
  useClientPagination<ReminderRow>(() => filtered.value, { pageSize: 15 })

watch([keyword, status], () => reset())

const metrics = computed<Metric[]>(() => {
  const pending = rows.value.filter(isPending).length
  const sent = rows.value.filter((row) => row.status === 'SENT').length
  const users = new Set(rows.value.map((row) => row.userId).filter((id) => id != null)).size
  const courses = new Set(rows.value.map((row) => row.courseId).filter((id) => id != null)).size
  return [
    { label: '待发送', value: pending, unit: '条', note: '尚未触发', icon: 'bell', tone: 'warn' },
    { label: '已发送', value: sent, unit: '条', note: '已扇出通知', icon: 'check', tone: 'ok' },
    { label: '覆盖用户', value: users, unit: '人', note: '接收人去重', icon: 'users' },
    { label: '关联课程', value: courses, unit: '门', note: '按课程去重', icon: 'book' },
  ]
})

function isPending(row: ReminderRow): boolean {
  return row.status === 'PENDING'
}

function statusLabel(value: unknown): string {
  return (
    ({ PENDING: '待发送', SENT: '已发送', CANCELLED: '已取消' } as Record<string, string>)[
      String(value ?? '')
    ] || String(value ?? '—')
  )
}

function statusClass(value: unknown): string {
  return value === 'SENT' ? 'chip-ok' : value === 'CANCELLED' ? 'chip-mute' : 'chip-warn'
}

function typeLabel(value: unknown): string {
  return (
    (
      {
        BEFORE_DEADLINE: '截止前提醒',
        DAILY_STUDY: '每日学习',
        NEW_CONTENT: '新内容提醒',
      } as Record<string, string>
    )[String(value ?? '')] || String(value ?? '—')
  )
}

function brief(text: unknown): string {
  const raw = String(text ?? '')
  return raw.length > 60 ? raw.slice(0, 60) + '…' : raw
}

function briefTime(text: unknown): string {
  return (
    String(text ?? '')
      .replace('T', ' ')
      .slice(0, 16) || '—'
  )
}

function toIso(value: string): string {
  return value.length === 16 ? `${value}:00` : value
}

function toInput(value: unknown): string {
  return String(value ?? '')
    .replace(' ', 'T')
    .slice(0, 16)
}

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

function resetForm() {
  Object.assign(form, {
    targetUserId: null,
    reminderType: 'BEFORE_DEADLINE',
    dueDate: '',
    triggerDay: 3,
    remindAt: '',
    courseId: null,
    taskId: null,
    title: '',
    message: '',
  })
  formError.value = ''
}

function openCreate() {
  editingId.value = null
  resetForm()
  formOpen.value = true
}

function openEdit(row: ReminderRow) {
  editingId.value = row.id ?? null
  Object.assign(form, {
    targetUserId: row.userId == null ? null : Number(row.userId),
    remindAt: toInput(row.scheduledAt),
    title: row.title || '',
    message: row.message || '',
  })
  formError.value = ''
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  editingId.value = null
  resetForm()
}

async function load() {
  loading.value = true
  listError.value = ''
  try {
    const payload = unwrap<unknown>(await getTodos())
    if (!Array.isArray(payload)) throw new Error('响应格式无效')
    rows.value = pickList<ReminderRow>(payload)
  } catch (error) {
    listError.value = errorText(error, '提醒列表加载失败，请稍后重试')
    toast(listError.value, 'danger')
  } finally {
    loading.value = false
  }
}

async function sendDueReminders() {
  sending.value = true
  try {
    const payload = unwrap<unknown>(await sendDue())
    const count = Array.isArray(payload) ? payload.length : 0
    toast(count ? `已发送 ${count} 条到期提醒` : '没有到期的待发送提醒', count ? 'ok' : 'info')
    await load()
  } catch (error) {
    toast(errorText(error, '发送到期提醒失败'), 'danger')
  } finally {
    sending.value = false
  }
}

async function submit() {
  const title = form.title.trim()
  const targetUserId = Number(form.targetUserId)
  if (!title) {
    formError.value = '标题必填'
    return
  }
  if (!Number.isInteger(targetUserId) || targetUserId <= 0) {
    formError.value = '接收用户 ID 必须是正整数'
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    if (editingId.value != null) {
      if (!form.remindAt) {
        formError.value = '计划发送时间必填'
        return
      }
      await updateReminder(editingId.value, {
        title,
        content: form.message.trim(),
        remindAt: toIso(form.remindAt),
        targetUserId,
      })
      toast('提醒已更新', 'ok')
    } else {
      if (!form.dueDate) {
        formError.value = '截止时间必填'
        return
      }
      if (!Number.isInteger(Number(form.triggerDay)) || Number(form.triggerDay) < 1) {
        formError.value = '提前触发天数至少为 1'
        return
      }
      const body: Record<string, unknown> = {
        title,
        message: form.message.trim(),
        reminderType: form.reminderType,
        dueDate: toIso(form.dueDate),
        triggerDay: Number(form.triggerDay),
        userId: targetUserId,
      }
      if (form.courseId) body.courseId = Number(form.courseId)
      if (form.taskId) body.taskId = Number(form.taskId)
      await createReminder(body)
      toast('提醒已创建', 'ok')
    }
    closeForm()
    await load()
  } catch (error) {
    formError.value = errorText(error, '保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function cancel(row: ReminderRow) {
  if (row.id == null) return
  acting.value = String(row.id)
  try {
    await cancelReminder(row.id)
    toast('提醒已取消', 'ok')
    await load()
  } catch (error) {
    toast(errorText(error, '取消提醒失败'), 'danger')
  } finally {
    acting.value = ''
  }
}

onMounted(() => void load())
</script>

<style scoped>
.reminders-body {
  display: grid;
  gap: 14px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.search-input {
  width: 220px;
  height: 30px;
}
.filter-select {
  width: 120px;
  height: 30px;
}
.reminder-form-card {
  margin-top: 2px;
}
.reminder-grid {
  padding: 12px 16px 16px;
}
.form-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
