<template>
  <section class="classes-tab">
    <header class="course-tab-bar">
      <div>
        <h2>
          教学班 <span class="chip chip-mute">{{ classes.length }} 个</span>
        </h2>
      </div>
      <div class="course-tab-actions">
        <button class="btn btn-ghost" :disabled="loading" @click="loadClasses">刷新</button>
      </div>
    </header>

    <div v-if="loadError" class="callout danger" role="alert">
      <span>教学班</span>
      <p>{{ loadError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="loadClasses">重试</button>
    </div>

    <div v-if="loading && !classes.length" class="empty-state compact"><b>正在加载教学班…</b></div>
    <div v-else-if="!classes.length" class="empty-state compact">
      <b>本课程暂无教学班</b>
    </div>

    <div v-else class="class-strip">
      <button
        v-for="item in classes"
        :key="item.key"
        class="class-pill"
        :class="{ 'is-active': String(item.key) === selectedKey }"
        @click="selectClass(item.key)"
      >
        <b>{{ item.name || `教学班 ${item.key}` }}</b>
        <span>{{ item.code || '—' }}</span>
        <small>{{ item.students }} 人</small>
      </button>
    </div>

    <section v-if="selectedClass" class="card table-card members-card">
      <header class="card-head">
        <div>
          <h2>
            {{ selectedClass.name || '教学班' }}
            <span class="chip chip-mute">{{ activeMembers.length }} 人</span>
          </h2>
        </div>
        <div class="member-tools">
          <input
            v-model="memberKeyword"
            class="input search-input"
            type="search"
            placeholder="姓名 / 学号"
            aria-label="搜索教学班成员"
          />
          <button class="btn btn-sm" :disabled="membersLoading" @click="loadMembers">
            {{ membersLoading ? '加载中…' : '刷新名单' }}
          </button>
        </div>
      </header>

      <div v-if="membersError" class="callout danger" role="alert">
        <span>名单</span>
        <p>{{ membersError }}</p>
        <button class="btn btn-sm" :disabled="membersLoading" @click="loadMembers">重试</button>
      </div>

      <div v-if="visibleMembers.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>学生</th>
              <th class="w-140">学号 / 用户名</th>
              <th class="w-96">状态</th>
              <th class="w-140">加入时间</th>
              <th class="w-120 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="member in visibleMembers" :key="String(member.enrollmentId)">
              <td>
                <b>{{ member.studentName || member.username || `学生 #${member.studentId}` }}</b>
              </td>
              <td>{{ member.studentNo || member.username || '—' }}</td>
              <td>
                <span class="chip" :class="statusClass(member)">{{ statusLabel(member) }}</span>
              </td>
              <td>{{ formatTime(member.enrolledAt) }}</td>
              <td>
                <button
                  v-if="canCancel(member)"
                  class="text-btn danger"
                  :disabled="acting === String(member.enrollmentId)"
                  @click="askCancel(member)"
                >
                  取消选课
                </button>
                <span v-else class="hint-inline">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="membersLoading" class="empty-state compact"><b>正在加载名单…</b></div>
      <div v-else class="empty-state compact">
        <b>{{ memberKeyword.trim() ? '没有匹配的学生' : '该教学班暂无学生' }}</b>
      </div>
    </section>

    <Modal v-model:open="cancelOpen" title="取消选课" width="520px" :close-on-backdrop="false">
      <p class="modal-text">
        取消 <b>{{ cancelTarget?.studentName || cancelTarget?.username || '该学生' }}</b> 在「{{
          selectedClass?.name || '本教学班'
        }}」的选课？记录会保留为「已取消」。
      </p>
      <label class="field">
        <span>取消原因 *</span>
        <textarea
          v-model="cancelReason"
          class="input textarea"
          maxlength="200"
          rows="3"
          data-testid="teacher-cancel-reason"
          placeholder="2–200 字，写入审批记录"
        />
      </label>
      <label class="check-row">
        <input v-model="cancelConfirmed" type="checkbox" data-testid="teacher-cancel-confirm" />
        <span>我已核对学生与教学班，确认取消选课</span>
      </label>
      <p v-if="cancelError" class="form-error" role="alert">{{ cancelError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="cancelling" @click="closeCancel">返回</button>
        <button
          class="btn btn-danger"
          :disabled="cancelling || !cancelConfirmed || cancelReason.trim().length < 2"
          @click="confirmCancel"
        >
          {{ cancelling ? '取消中…' : '确认取消选课' }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Modal from '../../../components/Modal.vue'
import { useUi } from '../../../composables/ui'
import { getCourseClasses } from '@/api/teacher'
import { getTeachingClassMembers } from '@/api/teachingClass'
import { cancelEnrollment } from '@/api/enrollment'
import { pickList, unwrap } from '@/utils/response'

interface ClassRow {
  key: string
  name?: string
  code?: string
  students?: number
  pending?: number
  schedule?: string
  status?: string
}

interface MemberRow {
  enrollmentId?: number | string
  studentId?: number | string
  studentName?: string
  username?: string
  studentNo?: string
  approvalStatus?: string
  isActive?: boolean
  enrolledAt?: string
}

const props = defineProps<{ courseId: number }>()
const { toast } = useUi()

const classes = ref<ClassRow[]>([])
const selectedKey = ref('')
const members = ref<MemberRow[]>([])
const memberKeyword = ref('')
const loading = ref(false)
const membersLoading = ref(false)
const loadError = ref('')
const membersError = ref('')
const acting = ref('')
const cancelOpen = ref(false)
const cancelTarget = ref<MemberRow | null>(null)
const cancelReason = ref('')
const cancelConfirmed = ref(false)
const cancelError = ref('')
const cancelling = ref(false)

const selectedClass = computed(
  () => classes.value.find((item) => String(item.key) === selectedKey.value) || null,
)

const activeMembers = computed(() => members.value.filter((member) => member.isActive !== false))

const visibleMembers = computed(() => {
  const key = memberKeyword.value.trim().toLowerCase()
  if (!key) return activeMembers.value
  return activeMembers.value.filter((member) =>
    [member.studentName, member.username, member.studentNo].some((value) =>
      String(value ?? '')
        .toLowerCase()
        .includes(key),
    ),
  )
})

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

function statusLabel(member: MemberRow): string {
  if (member.isActive === false) return '已取消'
  return (
    ({ PENDING: '待审批', APPROVED: '已通过' } as Record<string, string>)[
      String(member.approvalStatus ?? '')
    ] || '已通过'
  )
}

function statusClass(member: MemberRow): string {
  if (member.isActive === false) return 'chip-mute'
  return member.approvalStatus === 'PENDING' ? 'chip-warn' : 'chip-ok'
}

function canCancel(member: MemberRow): boolean {
  return (
    member.isActive !== false &&
    (member.approvalStatus === 'APPROVED' || member.approvalStatus === 'PENDING')
  )
}

function formatTime(value?: string): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

async function loadClasses() {
  loading.value = true
  loadError.value = ''
  try {
    const rows = await getCourseClasses(props.courseId)
    classes.value = rows as ClassRow[]
    const first = classes.value[0]
    if (!classes.value.some((item) => String(item.key) === selectedKey.value)) {
      selectedKey.value = first ? String(first.key) : ''
    }
    await loadMembers()
  } catch (error) {
    classes.value = []
    members.value = []
    selectedKey.value = ''
    loadError.value = errorText(error, '教学班加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function loadMembers() {
  if (!selectedKey.value) {
    members.value = []
    return
  }
  membersLoading.value = true
  membersError.value = ''
  try {
    const payload = unwrap(await getTeachingClassMembers(selectedKey.value))
    members.value = pickList<MemberRow>(payload)
  } catch (error) {
    members.value = []
    membersError.value = errorText(error, '名单加载失败，请稍后重试')
  } finally {
    membersLoading.value = false
  }
}

async function selectClass(key: string) {
  selectedKey.value = String(key)
  memberKeyword.value = ''
  await loadMembers()
}

function askCancel(member: MemberRow) {
  cancelTarget.value = member
  cancelReason.value = ''
  cancelConfirmed.value = false
  cancelError.value = ''
  cancelOpen.value = true
}

function closeCancel() {
  if (cancelling.value) return
  cancelOpen.value = false
  cancelTarget.value = null
  cancelError.value = ''
}

async function confirmCancel() {
  const member = cancelTarget.value
  if (!member || member.enrollmentId == null || !cancelConfirmed.value) return
  const reason = cancelReason.value.trim()
  if (reason.length < 2 || reason.length > 200) {
    cancelError.value = '取消原因需为 2–200 字'
    return
  }
  cancelling.value = true
  cancelError.value = ''
  acting.value = String(member.enrollmentId)
  try {
    await cancelEnrollment(member.enrollmentId, reason)
    toast('已取消选课', 'ok')
    cancelOpen.value = false
    cancelTarget.value = null
    await loadClasses()
  } catch (error) {
    cancelError.value = errorText(error, '取消选课失败，请稍后重试')
  } finally {
    cancelling.value = false
    acting.value = ''
  }
}

onMounted(loadClasses)
</script>

<style scoped>
.classes-tab {
  display: grid;
  gap: 14px;
}
.class-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.class-pill {
  display: grid;
  gap: 2px;
  padding: 10px 12px;
  min-width: 160px;
  text-align: left;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--ink);
  cursor: pointer;
}
.class-pill.is-active {
  border-color: var(--brand);
}
.class-pill span,
.class-pill small {
  color: var(--ink-3);
  font-size: 12px;
}
.member-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}
.search-input {
  width: 180px;
  height: 30px;
}
.textarea {
  min-height: 84px;
  resize: vertical;
}
.check-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 14px;
}
.check-row input {
  width: 16px;
  height: 16px;
}
</style>
