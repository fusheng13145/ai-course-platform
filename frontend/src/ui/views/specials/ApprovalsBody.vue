<template>
  <section class="approvals-body">
    <div v-if="listError" class="callout" role="alert">
      <span>ERROR / 审批队列</span>
      <p>{{ listError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="loadCourses()">
        {{ loading ? '重试中…' : '重试' }}
      </button>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">APPROVALS / COURSES</p>
          <h2>
            待审批课程 <span class="chip chip-warn">{{ pending.length }} 门</span>
          </h2>
        </div>
        <div class="filter-row">
          <button class="btn btn-sm btn-ghost" :disabled="loading" @click="loadCourses()">
            {{ loading ? '刷新中…' : '刷新队列' }}
          </button>
        </div>
      </header>

      <div v-if="pending.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>课程名称 / 编号</th>
              <th class="w-140">分类</th>
              <th class="w-140">学期</th>
              <th class="w-140">教师</th>
              <th class="w-80">学分</th>
              <th class="w-220">提交时间</th>
              <th class="w-140 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in pending" :key="String(row.id ?? index)">
              <td>
                <b>{{ row.name || '未命名课程' }}</b>
                <small class="cell-sub">{{ row.code || '—' }}</small>
              </td>
              <td>{{ row.category || '未分类' }}</td>
              <td>{{ row.semester || '—' }}</td>
              <td>{{ teacherLabel(row) }}</td>
              <td>{{ row.credits ?? '—' }}</td>
              <td>{{ formatTime(row.createdAt) }}</td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="openApprove(row)">通过</button>
                  <button class="text-btn danger" @click="openReject(row)">驳回</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else-if="loading" class="empty-state compact">
        <b>正在加载审批队列…</b>
      </div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="check" /></span>
        <b>{{ emptyDescription() }}</b>
        <p>
          {{ listError ? '请查看上方提示后重试。' : '教师新提交的课程会以 PENDING 状态进入这里。' }}
        </p>
      </div>

      <p class="hint">
        待审批队列由 GET /courses 全量读取后按 approvalStatus=PENDING 过滤（列表端点只支持 status
        参数）。 通过走 POST /courses/{id}/approve，驳回走 POST
        /courses/{id}/reject，成功后重新拉取队列。
      </p>
    </section>

    <Modal
      v-model:open="approveOpen"
      title="通过课程审批"
      kicker="CONFIRM / APPROVE"
      width="460px"
      :close-on-backdrop="false"
    >
      <p class="modal-text">
        确定通过 <b>{{ approveTarget?.name || '该课程' }}</b> 的审批吗？
      </p>
      <p class="modal-hint">通过后课程状态置为 ACTIVE，教师可开始发布教学班与教学活动。</p>
      <p v-if="approveError" class="form-error" role="alert">{{ approveError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="approveBusy" @click="closeApprove">取消</button>
        <button class="btn btn-primary" :disabled="approveBusy" @click="confirmApprove">
          {{ approveBusy ? '审批中…' : '确定通过' }}
        </button>
      </template>
    </Modal>

    <Modal
      v-model:open="rejectOpen"
      title="驳回课程审批"
      kicker="CONFIRM / REJECT"
      width="520px"
      :close-on-backdrop="false"
    >
      <p class="modal-text">
        驳回 <b>{{ rejectTarget?.name || '该课程' }}</b> 的审批；理由将写入课程审核备注。
      </p>
      <label class="field">
        <span>驳回理由（可选）</span>
        <textarea
          v-model="rejectReason"
          class="input textarea"
          data-testid="reject-reason"
          rows="3"
          maxlength="500"
          placeholder="如：课程简介与教学大纲不完整"
        ></textarea>
      </label>
      <p v-if="rejectError" class="form-error" role="alert">{{ rejectError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="rejectBusy" @click="closeReject">取消</button>
        <button class="btn btn-danger" :disabled="rejectBusy" @click="confirmReject">
          {{ rejectBusy ? '驳回中…' : '确定驳回' }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Icon from '../../components/Icon.vue'
import Modal from '../../components/Modal.vue'
import Stat from '../../components/Stat.vue'
import { useUi } from '../../composables/ui'
import { useListFeedback } from '@/composables/useListFeedback'
import { approveCourse, getAllCourses, rejectCourse } from '@/api/course'
import type { Metric } from '../../nav'

interface CourseRow {
  id?: number | string
  name?: string
  code?: string
  category?: string
  semester?: string
  teacherId?: number | string | null
  credits?: number | string | null
  approvalStatus?: string
  status?: string
  createdAt?: string
  approvalComment?: string | null
  [key: string]: unknown
}

const { toast } = useUi()

const courses = ref<CourseRow[]>([])
const approveOpen = ref(false)
const approveTarget = ref<CourseRow | null>(null)
const approveBusy = ref(false)
const approveError = ref('')

const rejectOpen = ref(false)
const rejectTarget = ref<CourseRow | null>(null)
const rejectReason = ref('')
const rejectBusy = ref(false)
const rejectError = ref('')

const { loading, listError, run, emptyDescription } = useListFeedback({
  failMessage: '课程审批队列加载失败，请稍后重试。',
  toastMessage: '课程审批队列加载失败',
  emptyText: '当前没有待审批课程',
  errorEmptyText: '无法确认审批队列，请查看上方提示后重试',
  notify: (message) => toast(message, 'danger'),
})

const pending = computed(() =>
  courses.value.filter((row) => normalize(row.approvalStatus) === 'PENDING'),
)

const metrics = computed<Metric[]>(() => [
  {
    label: '待审批',
    value: listError.value ? '—' : pending.value.length,
    unit: '门',
    note: 'approvalStatus=PENDING',
    icon: 'clock',
    tone: 'warn',
  },
  {
    label: '课程总数',
    value: listError.value ? '—' : courses.value.length,
    unit: '门',
    note: '服务端全量',
    icon: 'book',
  },
  {
    label: '已通过',
    value: listError.value ? '—' : countBy('APPROVED'),
    unit: '门',
    note: '全量课程',
    icon: 'check',
    tone: 'ok',
  },
  {
    label: '已驳回',
    value: listError.value ? '—' : countBy('REJECTED'),
    unit: '门',
    note: '全量课程',
    icon: 'alert',
    tone: 'danger',
  },
])

function normalize(value?: string): string {
  return String(value || '').toUpperCase()
}

function countBy(status: string): number {
  return courses.value.filter((row) => normalize(row.approvalStatus) === status).length
}

function teacherLabel(row: CourseRow): string {
  return row.teacherId ? `教师 #${row.teacherId}` : '—'
}

function formatTime(value?: string | null): string {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

async function loadCourses() {
  await run(async () => {
    const list = await getAllCourses()
    if (!Array.isArray(list)) throw new Error('课程列表响应格式无效')
    courses.value = list as CourseRow[]
  })
}

function openApprove(row: CourseRow) {
  approveTarget.value = row
  approveError.value = ''
  approveOpen.value = true
}

function closeApprove() {
  if (approveBusy.value) return
  approveOpen.value = false
  approveTarget.value = null
  approveError.value = ''
}

async function confirmApprove() {
  const row = approveTarget.value
  if (!row || row.id === undefined || row.id === null) return
  approveBusy.value = true
  approveError.value = ''
  try {
    await approveCourse(row.id)
    approveOpen.value = false
    approveTarget.value = null
    toast('课程审批已通过', 'ok')
    await loadCourses()
  } catch (error) {
    approveError.value = errorText(error, '审批失败，请稍后重试')
  } finally {
    approveBusy.value = false
  }
}

function openReject(row: CourseRow) {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectError.value = ''
  rejectOpen.value = true
}

function closeReject() {
  if (rejectBusy.value) return
  rejectOpen.value = false
  rejectTarget.value = null
  rejectReason.value = ''
  rejectError.value = ''
}

async function confirmReject() {
  const row = rejectTarget.value
  if (!row || row.id === undefined || row.id === null) return
  rejectBusy.value = true
  rejectError.value = ''
  try {
    const reason = rejectReason.value.trim()
    await rejectCourse(row.id, reason || undefined)
    rejectOpen.value = false
    rejectTarget.value = null
    rejectReason.value = ''
    toast('课程已驳回', 'ok')
    await loadCourses()
  } catch (error) {
    rejectError.value = errorText(error, '驳回失败，请稍后重试')
  } finally {
    rejectBusy.value = false
  }
}

onMounted(() => {
  void loadCourses()
})
</script>

<style scoped>
.approvals-body {
  display: grid;
  gap: 14px;
}
</style>
