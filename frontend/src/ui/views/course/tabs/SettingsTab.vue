<template>
  <div class="settings-grid">
    <section v-if="role !== 'student'" class="card">
      <header class="card-head">
        <div>
          <h2>开课信息</h2>
        </div>
        <div class="course-tab-actions">
          <button v-if="role === 'teacher'" class="btn btn-ghost" @click="openEdit">
            开课设置
          </button>
          <button v-if="!record?.completedAt" class="btn btn-ghost" @click="completeOpen = true">
            结课
          </button>
        </div>
      </header>
      <div class="setting-form">
        <label><span>课程名称</span><input :value="course?.name || ''" readonly /></label>
        <label><span>课程编号</span><input :value="course?.code || ''" readonly /></label>
        <label><span>学期</span><input :value="course?.semester || ''" readonly /></label>
        <label
          ><span>计划教学班</span><input :value="record?.plannedSections ?? 1" readonly
        /></label>
        <label
          ><span>报名方式</span><input :value="enrollmentLabel(record?.enrollmentType)" readonly
        /></label>
        <label><span>每班容量</span><input :value="record?.maxStudents ?? ''" readonly /></label>
      </div>
      <p v-if="loadError" class="form-error">{{ loadError }}</p>
    </section>

    <section v-if="role !== 'student'" class="card">
      <header class="card-head">
        <div>
          <h2>教学班</h2>
        </div>
        <div class="course-tab-actions">
          <button
            v-if="role === 'teacher' && hasDraft"
            class="btn btn-ghost"
            :disabled="publishing"
            @click="publishClasses"
          >
            {{ publishing ? '发布中…' : '发布教学班' }}
          </button>
        </div>
      </header>
      <div v-if="sections.length" class="section-list">
        <div v-for="row in sections" :key="String(row.id)" class="section-row">
          <div class="section-name">
            <b>{{ row.name || `教学班 #${row.id}` }}</b>
            <small>{{ row.code || '' }}</small>
          </div>
          <select
            v-if="role === 'teacher'"
            class="input section-teacher"
            :value="String(row.leadTeacherId ?? '')"
            aria-label="主讲教师"
            @change="assignLead(row, ($event.target as HTMLSelectElement).value)"
          >
            <option value="" disabled>选择主讲</option>
            <option v-for="item in teachers" :key="String(item.id)" :value="String(item.id)">
              {{ item.name }}
            </option>
          </select>
          <span v-else class="section-teacher">{{ leadLabel(row) }}</span>
          <span class="chip" :class="row.status === 'ACTIVE' ? 'chip-ok' : 'chip-mute'">
            {{ statusLabel(row.status) }}
          </span>
          <span class="section-cap">{{ row.capacity ?? '—' }} 人</span>
          <button v-if="role === 'teacher'" class="text-btn" @click="openRoster(row)">名单</button>
        </div>
      </div>
      <div v-else class="empty-state compact"><b>暂无教学班</b></div>
    </section>

    <Modal
      v-model:open="rosterOpen"
      :title="`${rosterTarget?.name || '教学班'} · 名单`"
      width="620px"
    >
      <div v-if="rosterLoading" class="empty-state compact"><b>正在加载名单来源…</b></div>
      <template v-else>
        <div class="roster-actions">
          <select v-model="rosterSelected" class="input" aria-label="选择行政班">
            <option value="" disabled>选择行政班</option>
            <option
              v-for="item in rosterCandidates"
              :key="String(item.id)"
              :value="String(item.id)"
            >
              {{ item.name }}{{ item.code ? ` · ${item.code}` : '' }}
            </option>
          </select>
          <button
            class="btn btn-primary"
            :disabled="!rosterSelected || rosterBusy"
            @click="doImport"
          >
            {{ rosterBusy ? '处理中…' : '导入' }}
          </button>
          <button
            class="btn btn-ghost"
            :disabled="!rosterSources.length || rosterBusy"
            @click="doResync"
          >
            重同步
          </button>
        </div>
        <p v-if="rosterResult" class="roster-result">{{ rosterResult }}</p>
        <p v-if="rosterError" class="form-error" role="alert">{{ rosterError }}</p>
        <div v-if="rosterSources.length" class="source-list">
          <span v-for="source in rosterSources" :key="String(source.id)" class="chip">
            {{ sourceLabel(source.adminClassId) }}
          </span>
        </div>
        <div v-else class="empty-state compact"><b>尚未导入行政班</b></div>
      </template>
      <template #footer>
        <button class="btn btn-ghost" @click="rosterOpen = false">关闭</button>
      </template>
    </Modal>

    <!-- 开课设置 -->
    <Modal v-model:open="editOpen" title="开课设置" width="560px">
      <div v-if="editLoading" class="empty-state compact"><b>正在加载开课资料…</b></div>
      <template v-else>
        <div class="form-grid">
          <label class="field">
            <span>计划教学班数</span>
            <input
              v-model.number="form.plannedSections"
              class="input"
              type="number"
              min="1"
              max="50"
            />
          </label>
          <label class="field">
            <span>报名方式</span>
            <select v-model="form.enrollmentType" class="input">
              <option v-for="option in enrollmentOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>每班容量</span>
            <input v-model.number="form.maxStudents" class="input" type="number" min="1" />
          </label>
          <div class="field span-2 practice-setting">
            <span>学生刷题</span>
            <label class="practice-toggle">
              <input v-model="form.questionPracticeEnabled" type="checkbox" />
              <span>允许本课程学生进入题库刷题</span>
            </label>
          </div>
        </div>
        <p v-if="formError" class="form-error">{{ formError }}</p>
      </template>
      <template #footer>
        <button class="btn btn-ghost" @click="editOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="saving || editLoading" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <Modal v-model:open="completeOpen" title="结课" width="420px">
      <p class="modal-text">结课后学生不能再加入，成绩与学习记录保留。</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="completing" @click="completeOpen = false">
          取消
        </button>
        <button class="btn btn-primary" :disabled="completing" @click="completeCourseAction">
          {{ completing ? '结课中…' : '确认结课' }}
        </button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Modal from '../../../components/Modal.vue'
import { useUi } from '../../../composables/ui'
import courseApi, { completeCourse, getCourseById } from '@/api/course'
import commonApi from '@/api/common'
import {
  getRosterCandidates,
  getRosterSources,
  getTeachingClasses,
  importRoster,
  publishCourseClasses,
  assignLeadTeacher,
  resyncRoster,
} from '@/api/teachingClass'
import { pickList, unwrap } from '@/utils/response'
import logger from '@/utils/logger'
import type { Role } from '../../../nav'

interface CourseLike {
  name?: string
  code?: string
  semester?: string
  credits?: number | null
  category?: string
  approvalStatus?: string
}

interface SectionRow {
  id?: number | string
  name?: string
  code?: string
  leadTeacherId?: number | string | null
  capacity?: number | null
  status?: string
}

interface TeacherRow {
  id?: number | string
  name?: string
}

interface RosterCandidate {
  id?: number | string
  name?: string
  code?: string
}

interface RosterSource {
  id?: number | string
  adminClassId?: number | string | null
}

interface RosterResult {
  added?: number
  removed?: number
  skipped?: number
  conflicted?: number
}

const ENROLLMENT_OPTIONS = [
  { value: 'FREE', label: '开放报名' },
  { value: 'APPROVAL', label: '需审核' },
  { value: 'RESTRICTED', label: '限制报名' },
]

const props = defineProps<{ role: Role; course: CourseLike | null; courseId: number }>()
const emit = defineEmits<{ (event: 'updated', course: Record<string, unknown>): void }>()
const { toast } = useUi()

const record = ref<Record<string, unknown> | null>(null)
const loadError = ref('')
const editOpen = ref(false)
const editLoading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const completeOpen = ref(false)
const completing = ref(false)
const formError = ref('')
const sections = ref<SectionRow[]>([])
const teachers = ref<TeacherRow[]>([])
const hasDraft = computed(() =>
  sections.value.some((row) => String(row.status || '').toUpperCase() === 'DRAFT'),
)
const form = reactive({
  enrollmentType: 'FREE',
  maxStudents: 100,
  plannedSections: 1,
  questionPracticeEnabled: false,
})

const rosterOpen = ref(false)
const rosterLoading = ref(false)
const rosterBusy = ref(false)
const rosterError = ref('')
const rosterTarget = ref<SectionRow | null>(null)
const rosterCandidates = ref<RosterCandidate[]>([])
const rosterSources = ref<RosterSource[]>([])
const rosterSelected = ref('')
const rosterResult = ref('')

function enrollmentLabel(value: unknown): string {
  const key = String(value ?? '')
  if (!key) return '—'
  if (key === 'OPEN') return '开放报名'
  return ENROLLMENT_OPTIONS.find((option) => option.value === key)?.label || key
}

function statusLabel(value: unknown): string {
  const key = String(value ?? '')
  if (key === 'ACTIVE') return '启用'
  if (key === 'DRAFT') return '草稿'
  if (key === 'CLOSED') return '结课'
  return key || '—'
}

function leadLabel(row: SectionRow): string {
  const teacher = teachers.value.find((item) => String(item.id) === String(row.leadTeacherId ?? ''))
  return teacher?.name ? String(teacher.name) : '未指定主讲'
}

const enrollmentOptions = computed(() => {
  const current = String(record.value?.enrollmentType ?? form.enrollmentType ?? '')
  const known = ENROLLMENT_OPTIONS.some((option) => option.value === current)
  return current && !known
    ? [{ value: current, label: current }, ...ENROLLMENT_OPTIONS]
    : ENROLLMENT_OPTIONS
})

async function fetchRecord() {
  loadError.value = ''
  try {
    record.value = unwrap(await getCourseById(props.courseId)) as Record<string, unknown>
  } catch (error) {
    logger.error('加载课程资料失败', error)
    record.value = null
    loadError.value = '课程资料加载失败'
  }
  return record.value
}

async function loadSections() {
  try {
    sections.value = pickList<SectionRow>(unwrap(await getTeachingClasses(props.courseId)))
    if (props.role === 'teacher' || props.role === 'admin') {
      try {
        teachers.value = pickList<TeacherRow>(unwrap(await commonApi.getTeachers()))
      } catch {
        teachers.value = []
      }
    }
  } catch (error) {
    logger.error('加载教学班失败', error)
  }
}

async function openEdit() {
  editOpen.value = true
  editLoading.value = true
  formError.value = ''
  const body = (record.value || (await fetchRecord())) as Record<string, unknown> | null
  if (body) {
    form.enrollmentType = String(body?.enrollmentType ?? 'OPEN')
    form.maxStudents = body?.maxStudents == null ? 100 : Number(body.maxStudents)
    form.plannedSections = body?.plannedSections == null ? 1 : Number(body.plannedSections)
    form.questionPracticeEnabled =
      body?.questionPracticeEnabled === true ||
      body?.questionPracticeEnabled === 1 ||
      body?.questionPracticeEnabled === '1'
  }
  editLoading.value = false
}

async function save() {
  if (form.plannedSections < 1 || form.plannedSections > 50) {
    formError.value = '计划教学班数需在 1–50 之间'
    return
  }
  saving.value = true
  try {
    const body = unwrap(await courseApi.updateCourse(props.courseId, { ...form })) as Record<
      string,
      unknown
    >
    record.value = body
    toast('开课设置已更新', 'ok')
    editOpen.value = false
    emit('updated', body)
  } catch (error) {
    formError.value = '保存失败，请稍后重试'
    logger.error('更新开课设置失败', error)
  } finally {
    saving.value = false
  }
}

async function completeCourseAction() {
  completing.value = true
  try {
    const body = unwrap(await completeCourse(props.courseId)) as Record<string, unknown>
    record.value = body
    completeOpen.value = false
    toast('已结课', 'ok')
    emit('updated', body)
    await loadSections()
  } catch (error) {
    logger.error('结课失败', error)
    toast('结课失败', 'warn')
  } finally {
    completing.value = false
  }
}

async function publishClasses() {
  if (String(props.course?.approvalStatus || '').toUpperCase() !== 'APPROVED') {
    toast('课程未通过审批，不能发布教学班', 'warn')
    return
  }
  publishing.value = true
  try {
    const payload = unwrap(await publishCourseClasses(props.courseId))
    sections.value = pickList<SectionRow>(payload)
    toast('教学班已发布', 'ok')
  } catch (error) {
    logger.error('发布教学班失败', error)
    toast('发布教学班失败', 'warn')
  } finally {
    publishing.value = false
  }
}

async function assignLead(row: SectionRow, teacherId: string) {
  if (!row.id || !teacherId) return
  try {
    const updated = unwrap(await assignLeadTeacher(row.id, teacherId)) as SectionRow
    row.leadTeacherId = updated?.leadTeacherId ?? teacherId
    toast('主讲已更新', 'ok')
  } catch (error) {
    logger.error('指定主讲失败', error)
    toast('指定主讲失败', 'warn')
  }
}

function resultText(result: RosterResult | null): string {
  if (!result) return ''
  return `新增 ${result.added ?? 0} · 跳过 ${result.skipped ?? 0} · 冲突 ${result.conflicted ?? 0} · 移除 ${result.removed ?? 0}`
}

function sourceLabel(adminClassId: unknown): string {
  const item = rosterCandidates.value.find(
    (candidate) => String(candidate.id) === String(adminClassId ?? ''),
  )
  if (item?.name) return String(item.name)
  return `行政班 #${String(adminClassId ?? '—')}`
}

async function openRoster(row: SectionRow) {
  if (row.id === undefined || row.id === null) return
  rosterTarget.value = row
  rosterOpen.value = true
  rosterLoading.value = true
  rosterError.value = ''
  rosterResult.value = ''
  try {
    const [candidatePayload, sourcePayload] = await Promise.all([
      getRosterCandidates(row.id),
      getRosterSources(row.id),
    ])
    rosterCandidates.value = pickList<RosterCandidate>(unwrap(candidatePayload))
    rosterSources.value = pickList<RosterSource>(unwrap(sourcePayload))
    rosterSelected.value =
      rosterCandidates.value[0]?.id == null ? '' : String(rosterCandidates.value[0].id)
  } catch (error) {
    logger.error('加载名单来源失败', error)
    rosterCandidates.value = []
    rosterSources.value = []
    rosterSelected.value = ''
    rosterError.value = '名单来源加载失败'
  } finally {
    rosterLoading.value = false
  }
}

async function refreshRosterSources() {
  const row = rosterTarget.value
  if (!row || row.id === undefined || row.id === null) return
  const payload = await getRosterSources(row.id)
  rosterSources.value = pickList<RosterSource>(unwrap(payload))
}

async function doImport() {
  const row = rosterTarget.value
  if (!row || row.id === undefined || row.id === null || !rosterSelected.value) return
  rosterBusy.value = true
  rosterError.value = ''
  try {
    const result = unwrap(await importRoster(row.id, rosterSelected.value)) as RosterResult
    rosterResult.value = resultText(result)
    await refreshRosterSources()
    toast('名单已导入', 'ok')
  } catch (error) {
    logger.error('导入名单失败', error)
    rosterError.value = '导入失败'
  } finally {
    rosterBusy.value = false
  }
}

async function doResync() {
  const row = rosterTarget.value
  if (!row || row.id === undefined || row.id === null) return
  rosterBusy.value = true
  rosterError.value = ''
  try {
    const result = unwrap(await resyncRoster(row.id)) as RosterResult
    rosterResult.value = resultText(result)
    await refreshRosterSources()
    toast('名单已重同步', 'ok')
  } catch (error) {
    logger.error('重同步名单失败', error)
    rosterError.value = '重同步失败'
  } finally {
    rosterBusy.value = false
  }
}

onMounted(async () => {
  if (props.role === 'student') return
  await fetchRecord()
  await loadSections()
})
</script>

<style scoped>
.settings-grid .course-tab-actions {
  align-items: center;
}

.practice-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.practice-toggle input {
  width: 16px;
  height: 16px;
}

.section-list {
  display: grid;
  gap: 1px;
  background: var(--line);
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  overflow: hidden;
}

.section-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  padding: 0 12px;
  background: var(--surface, #fff);
}

.section-name {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.section-name b {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.section-name small {
  font-size: 12px;
  color: var(--ink-3);
}

.section-teacher {
  width: 140px;
}

.section-cap {
  width: 64px;
  text-align: right;
  font-size: 12px;
  color: var(--ink-3);
}

.roster-actions {
  display: flex;
  gap: 8px;
}

.roster-actions .input {
  flex: 1;
}

.roster-result {
  margin: 10px 0 0;
  font-size: 14px;
  color: var(--ink-2);
}

.source-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}
</style>
