<template>
  <section class="module">
    <PageHead :title="def.title">
      <template #actions>
        <select
          v-if="role === 'student'"
          v-model="selectedCourseId"
          class="input join-course-select"
          aria-label="选择要加入的课程"
        >
          <option value="" disabled>
            {{ joinable.length ? '选择要加入的课程' : '暂无可加入的课程' }}
          </option>
          <option v-for="course in joinable" :key="course.id" :value="course.id">
            {{ course.name }}
          </option>
        </select>
        <button v-if="role === 'admin'" class="btn btn-ghost" @click="openLibrary">课程库</button>
        <button v-if="role === 'admin'" class="btn btn-ghost" @click="managerOpen = true">
          课程管理
        </button>
        <button class="btn btn-primary" :disabled="joining" @click="onPrimary">
          {{ primaryLabel }}
        </button>
      </template>
    </PageHead>

    <div class="courses-toolbar">
      <div class="tabs" role="tablist" aria-label="课程筛选">
        <button :class="{ 'is-active': filter === 'all' }" @click="filter = 'all'">
          全部 {{ courses.length }}
        </button>
        <button :class="{ 'is-active': filter === 'active' }" @click="filter = 'active'">
          进行中 {{ activeCount }}
        </button>
        <button :class="{ 'is-active': filter === 'done' }" @click="filter = 'done'">
          已完成 {{ doneCount }}
        </button>
      </div>
      <div class="table-tools"></div>
    </div>

    <div v-if="filtered.length" class="course-grid">
      <CourseFolderCard
        v-for="course in filtered"
        :key="course.id"
        :course="course"
        :role="role"
        :dropping="dropping"
        @open="open"
        @drop="askDrop"
      />
    </div>

    <div v-else class="card empty-state">
      <span class="e-ico"><Icon name="book" /></span>
      <b>{{ emptyTitle }}</b>
    </div>

    <Modal v-model:open="managerOpen" title="课程管理" width="920px" :close-on-backdrop="false">
      <div v-if="courses.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-120">编号</th>
              <th>课程名称</th>
              <th class="w-120">教师</th>
              <th class="w-140">学期</th>
              <th class="w-80">状态</th>
              <th class="w-180 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="course in courses" :key="course.id">
              <td class="mono">{{ course.code || '—' }}</td>
              <td>{{ course.name }}</td>
              <td>{{ course.teacher || '—' }}</td>
              <td>{{ course.semester || '—' }}</td>
              <td>
                <span class="chip" :class="course.status === 'INACTIVE' ? 'chip-mute' : 'chip-ok'">
                  {{ courseStatusLabel(course.status) }}
                </span>
              </td>
              <td>
                <div class="row-actions">
                  <button
                    class="text-btn"
                    :disabled="courseActionId === course.id"
                    @click="toggleCourse(course)"
                  >
                    {{ course.status === 'INACTIVE' ? '启用' : '停用' }}
                  </button>
                  <button
                    class="text-btn danger"
                    :disabled="courseActionId === course.id"
                    @click="askDeleteCourse(course)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state compact"><b>暂无课程</b></div>
      <template #footer>
        <button class="btn btn-ghost" @click="managerOpen = false">关闭</button>
      </template>
    </Modal>

    <Modal
      v-model:open="deleteCourseOpen"
      title="删除课程"
      width="460px"
      :close-on-backdrop="false"
    >
      <p class="modal-text">
        确定删除「{{ deleteCourseTarget?.name || '该课程' }}」？该课程的教学班与选课记录将一并移除。
      </p>
      <p v-if="deleteCourseError" class="form-error" role="alert">{{ deleteCourseError }}</p>
      <template #footer>
        <button
          class="btn btn-ghost"
          :disabled="deleteCourseBusy"
          @click="deleteCourseOpen = false"
        >
          取消
        </button>
        <button class="btn btn-danger" :disabled="deleteCourseBusy" @click="confirmDeleteCourse">
          {{ deleteCourseBusy ? '删除中…' : '确定删除' }}
        </button>
      </template>
    </Modal>

    <Modal v-model:open="libraryOpen" title="课程库" width="860px" :close-on-backdrop="false">
      <div class="table-tools">
        <button class="btn btn-sm btn-ghost" @click="openDefinitionCreate">新增课程定义</button>
      </div>
      <div v-if="libraryLoading" class="empty-state compact"><b>正在读取课程库…</b></div>
      <div v-else-if="libraryError" class="callout">
        <span>课程库</span>
        <p>{{ libraryError }}</p>
        <button class="btn btn-ghost btn-sm" @click="loadLibrary">重试</button>
      </div>
      <div v-else-if="definitions.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-120">编号</th>
              <th>课程名称</th>
              <th class="w-64">学分</th>
              <th class="w-64">性质</th>
              <th class="w-80">状态</th>
              <th class="w-64 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in definitions" :key="String(row.id)">
              <td class="mono">{{ row.code }}</td>
              <td>{{ row.name }}</td>
              <td>{{ row.credits ?? '—' }}</td>
              <td>{{ row.nature || '—' }}</td>
              <td>
                <span class="chip" :class="row.status === 'INACTIVE' ? 'chip-mute' : 'chip-ok'">
                  {{ row.status === 'INACTIVE' ? '已停用' : '可用' }}
                </span>
              </td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="openDefinitionEdit(row)">编辑</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state compact"><b>课程库为空</b></div>
      <template #footer>
        <button class="btn btn-ghost" @click="libraryOpen = false">关闭</button>
      </template>
    </Modal>

    <Modal
      v-model:open="definitionOpen"
      :title="definitionEditingId === null ? '新增课程定义' : '编辑课程定义'"
      width="640px"
      :close-on-backdrop="false"
    >
      <div class="form-grid">
        <label class="field">
          <span>课程编号 *</span>
          <input
            v-model="definitionForm.code"
            class="input mono"
            data-testid="definition-code"
            maxlength="50"
            placeholder="如 CN202"
          />
        </label>
        <label class="field">
          <span>课程名称 *</span>
          <input
            v-model="definitionForm.name"
            class="input"
            data-testid="definition-name"
            maxlength="255"
          />
        </label>
        <label class="field">
          <span>学分</span>
          <input
            v-model.number="definitionForm.credits"
            class="input"
            type="number"
            min="0"
            step="0.5"
          />
        </label>
        <label class="field">
          <span>学时</span>
          <input v-model.number="definitionForm.hours" class="input" type="number" min="0" />
        </label>
        <label class="field">
          <span>课程性质</span>
          <select v-model="definitionForm.nature" class="input">
            <option value="必修">必修</option>
            <option value="选修">选修</option>
            <option value="公选">公选</option>
          </select>
        </label>
        <label class="field">
          <span>学院</span>
          <select v-model="definitionForm.collegeId" class="input">
            <option value="">不限学院</option>
            <option
              v-for="college in colleges"
              :key="String(college.id)"
              :value="String(college.id)"
            >
              {{ college.name }}
            </option>
          </select>
        </label>
        <label v-if="definitionEditingId !== null" class="field">
          <span>状态</span>
          <select v-model="definitionForm.status" class="input">
            <option value="ACTIVE">可用</option>
            <option value="INACTIVE">停用</option>
          </select>
        </label>
      </div>
      <p v-if="definitionError" class="form-error" role="alert">{{ definitionError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="definitionBusy" @click="definitionOpen = false">
          取消
        </button>
        <button class="btn btn-primary" :disabled="definitionBusy" @click="submitDefinition">
          {{ definitionBusy ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <!-- 选班：同一开课任务下有多个启用教学班时，先选班再加入 -->
    <Modal v-model:open="sectionOpen" title="选择教学班" width="420px">
      <div class="section-list">
        <label v-for="item in sections" :key="String(item.id)" class="section-option">
          <input v-model="selectedSectionId" type="radio" :value="String(item.id)" />
          <span>{{ item.name || `教学班 ${item.id}` }}</span>
          <small>{{ item.capacity ?? '—' }} 人</small>
        </label>
      </div>
      <template #footer>
        <button class="btn btn-ghost" :disabled="joining" @click="sectionOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="joining" @click="confirmJoinSection">
          {{ joining ? '加入中…' : '加入' }}
        </button>
      </template>
    </Modal>

    <!-- 退选二次确认 -->
    <Modal v-model:open="dropOpen" title="退选课程" width="460px">
      <p class="modal-text">
        确定退选「{{ dropTarget?.name || '该课程' }}」？退选后将从「我的课程」移除，学习记录保留。
      </p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="dropping" @click="dropOpen = false">取消</button>
        <button class="btn btn-danger" :disabled="dropping" @click="runDrop">
          {{ dropping ? '退选中…' : '确定退选' }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHead from '../components/PageHead.vue'
import Icon from '../components/Icon.vue'
import Modal from '../components/Modal.vue'
import CourseFolderCard from '../components/CourseFolderCard.vue'
import studentApi from '@/api/student'
import {
  createCourseDefinition,
  deleteCourse,
  getCourseDefinitions,
  toggleCourseStatus,
  updateCourseDefinition,
} from '@/api/course'
import { getColleges } from '@/api/admin'
import { pageDef, type Role } from '../nav'
import { useUi } from '../composables/ui'
import { getCourses, getCourseCatalog, type CourseCard } from '../data/api'
import { pickList, unwrap } from '@/utils/response'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const { role, toast } = useUi()
const courses = ref<CourseCard[]>([])
const filter = ref<'all' | 'active' | 'done'>('all')
// 学生「加入课程」：从课程目录里挑一门未选课程，走真实选课写口（非演示动作）。
const selectedCourseId = ref<number | ''>('')
const joinable = ref<CourseCard[]>([])
const joining = ref(false)
// 选班：一门开课任务可能有多个启用教学班，加入前必须先确定教学班。
const sectionOpen = ref(false)
const sections = ref<{ id?: number | string; name?: string; capacity?: number | null }[]>([])
const selectedSectionId = ref('')
const pendingCourseId = ref<number | null>(null)
// 退选：后端 DELETE /student/courses/{courseId}/enroll 已存在，二次确认后走真实写口。
const dropOpen = ref(false)
const dropTarget = ref<CourseCard | null>(null)
const dropping = ref(false)

interface DefinitionRow {
  id?: number | string
  code?: string
  name?: string
  credits?: number | string | null
  hours?: number | null
  nature?: string
  collegeId?: number | string | null
  status?: string
}

interface CollegeRow {
  id?: number | string
  name?: string
}

const libraryOpen = ref(false)
const libraryLoading = ref(false)
const libraryError = ref('')
const definitions = ref<DefinitionRow[]>([])
const colleges = ref<CollegeRow[]>([])
const definitionOpen = ref(false)
const definitionEditingId = ref<number | string | null>(null)
const definitionBusy = ref(false)
const definitionError = ref('')
const definitionForm = reactive({
  code: '',
  name: '',
  credits: 3,
  hours: 48,
  nature: '选修',
  collegeId: '',
  status: 'ACTIVE',
})

const managerOpen = ref(false)
const courseActionId = ref<number | null>(null)
const deleteCourseOpen = ref(false)
const deleteCourseTarget = ref<CourseCard | null>(null)
const deleteCourseBusy = ref(false)
const deleteCourseError = ref('')

const def = computed(() => pageDef(role.value as Role, 'courses'))
const primaryLabel = computed(() => (role.value === 'student' ? '加入课程' : '新建开课任务'))
const doneCount = computed(() => courses.value.filter((course) => course.progress >= 100).length)
const activeCount = computed(() => courses.value.length - doneCount.value)
const filtered = computed(() => {
  if (filter.value === 'done') return courses.value.filter((course) => course.progress >= 100)
  if (filter.value === 'active') return courses.value.filter((course) => course.progress < 100)
  return courses.value
})
const emptyTitle = computed(() => (filter.value === 'done' ? '暂无已完成课程' : '暂无课程'))

/**
 * 课程卡在新标签页打开：列表页状态（筛选、滚动位置）不被覆盖，
 * 也便于同时对照多门课。href 走 router.resolve，保留 base 与查询串。
 * 返回列表 = 关掉课程标签页（原列表页仍在，不会叠出第二份）。
 */
function open(id: number) {
  const href = router.resolve(`/${role.value}/courses/${id}/overview`).href
  // 新标签页是独立 JS 上下文、读不到本页的 sessionStorage：先把当前登录态
  // （非敏感 user）镜像到 localStorage，否则新页会被守卫判成未登录弹回 /login。
  useAuthStore().mirrorUserForNewTab()
  window.open(href, '_blank', 'noopener')
}

/** 可加入课程 = 课程目录剔除已在修课程（按 id 去重，剔除 id<=0 的脏行）。 */
async function refreshJoinable() {
  const joined = new Set(courses.value.map((course) => course.id))
  const catalog = await getCourseCatalog()
  joinable.value = catalog.filter((course) => course.id > 0 && !joined.has(course.id))
}

async function joinSelectedCourse() {
  const courseId = Number(selectedCourseId.value)
  if (!courseId) {
    toast(joinable.value.length ? '请先选择要加入的课程' : '暂无可加入的课程', 'warn')
    return
  }
  try {
    const payload = unwrap<unknown>(await studentApi.getAvailableTeachingClasses(courseId))
    sections.value = pickList<{ id?: number | string; name?: string; capacity?: number | null }>(
      payload,
    )
    if (!sections.value.length) {
      toast('该课程暂无启用教学班', 'warn')
      return
    }
    if (sections.value.length === 1) {
      await enrollWithSection(courseId, sections.value[0].id)
      return
    }
    pendingCourseId.value = courseId
    selectedSectionId.value = String(sections.value[0].id)
    sectionOpen.value = true
  } catch {
    toast('教学班读取失败，请稍后重试', 'warn')
  }
}

async function enrollWithSection(courseId: number, teachingClassId: unknown) {
  joining.value = true
  try {
    await studentApi.enrollCourse(courseId, teachingClassId as number | string)
    toast('已加入课程', 'ok')
    selectedCourseId.value = ''
    sectionOpen.value = false
    pendingCourseId.value = null
    courses.value = await getCourses('student', true)
    await refreshJoinable()
  } catch {
    toast('加入课程失败：课程可能未通过审核、已满员或已加入', 'warn')
  } finally {
    joining.value = false
  }
}

async function confirmJoinSection() {
  if (!selectedSectionId.value || pendingCourseId.value == null) return
  await enrollWithSection(pendingCourseId.value, selectedSectionId.value)
}

/** 打开退选确认弹层（二次确认后才发写请求）。 */
function askDrop(course: CourseCard) {
  dropTarget.value = course
  dropOpen.value = true
}

async function runDrop() {
  const course = dropTarget.value
  if (!course) return
  dropping.value = true
  try {
    // 后端契约：StudentController.unenrollCourse —— DELETE /student/courses/{courseId}/enroll，
    // 路径参数、无请求体；成功后 GET /student/courses 不再返回该课。
    await studentApi.unenrollCourse(course.id)
    toast('已退选课程', 'ok')
    dropOpen.value = false
    dropTarget.value = null
    courses.value = await getCourses('student', true)
    await refreshJoinable()
  } catch {
    toast('退选失败，请稍后重试', 'warn')
  } finally {
    dropping.value = false
  }
}

async function onPrimary() {
  if (role.value === 'teacher') {
    router.push('/teacher/courses/create')
    return
  }
  if (role.value === 'admin') {
    router.push('/admin/courses/create')
    return
  }
  if (role.value === 'student') {
    await joinSelectedCourse()
  }
}

function definitionErrorText(error: unknown): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || '保存失败，请稍后重试'
}

async function loadLibrary() {
  libraryLoading.value = true
  libraryError.value = ''
  try {
    const [definitionPayload, collegePayload] = await Promise.all([
      getCourseDefinitions(),
      getColleges(),
    ])
    const definitionRows = pickList<DefinitionRow>(unwrap(definitionPayload))
    const collegeRows = pickList<CollegeRow>(unwrap(collegePayload))
    definitions.value = definitionRows
    colleges.value = collegeRows
  } catch {
    libraryError.value = '课程库读取失败，请稍后重试'
  } finally {
    libraryLoading.value = false
  }
}

function openLibrary() {
  libraryOpen.value = true
  void loadLibrary()
}

function openDefinitionCreate() {
  definitionEditingId.value = null
  definitionError.value = ''
  Object.assign(definitionForm, {
    code: '',
    name: '',
    credits: 3,
    hours: 48,
    nature: '选修',
    collegeId: colleges.value[0]?.id ? String(colleges.value[0].id) : '',
    status: 'ACTIVE',
  })
  definitionOpen.value = true
}

function openDefinitionEdit(row: DefinitionRow) {
  definitionEditingId.value = row.id ?? null
  definitionError.value = ''
  Object.assign(definitionForm, {
    code: String(row.code || ''),
    name: String(row.name || ''),
    credits: Number(row.credits ?? 0),
    hours: Number(row.hours ?? 0),
    nature: String(row.nature || '选修'),
    collegeId: row.collegeId == null ? '' : String(row.collegeId),
    status: String(row.status || 'ACTIVE'),
  })
  definitionOpen.value = true
}

async function submitDefinition() {
  if (definitionBusy.value) return
  const code = definitionForm.code.trim().toUpperCase()
  const name = definitionForm.name.trim()
  if (!code) {
    definitionError.value = '请填写课程编号'
    return
  }
  if (!name) {
    definitionError.value = '请填写课程名称'
    return
  }
  if (!Number.isFinite(Number(definitionForm.credits)) || Number(definitionForm.credits) < 0) {
    definitionError.value = '学分必须是非负数字'
    return
  }
  definitionBusy.value = true
  definitionError.value = ''
  const payload = {
    code,
    name,
    credits: Number(definitionForm.credits),
    hours: Number(definitionForm.hours) || 0,
    nature: definitionForm.nature,
    collegeId: definitionForm.collegeId ? Number(definitionForm.collegeId) : undefined,
    status: definitionForm.status,
  }
  try {
    const editing = definitionEditingId.value
    if (editing === null || editing === undefined) {
      await createCourseDefinition(payload)
      toast('课程定义已创建', 'ok')
    } else {
      await updateCourseDefinition(editing, payload)
      toast('课程定义已更新', 'ok')
    }
    definitionOpen.value = false
    await loadLibrary()
  } catch (error) {
    definitionError.value = definitionErrorText(error)
  } finally {
    definitionBusy.value = false
  }
}

function courseStatusLabel(status: string): string {
  const key = String(status || '').toUpperCase()
  if (key === 'INACTIVE') return '已停用'
  if (key === 'ACTIVE') return '启用'
  return status || '—'
}

async function refreshCourses() {
  courses.value = await getCourses('admin')
}

async function toggleCourse(course: CourseCard) {
  if (courseActionId.value === course.id) return
  courseActionId.value = course.id
  try {
    await toggleCourseStatus(course.id)
    toast(String(course.status).toUpperCase() === 'INACTIVE' ? '课程已启用' : '课程已停用', 'ok')
    await refreshCourses()
  } catch {
    toast('课程状态切换失败，请稍后重试', 'warn')
  } finally {
    courseActionId.value = null
  }
}

function askDeleteCourse(course: CourseCard) {
  deleteCourseTarget.value = course
  deleteCourseError.value = ''
  deleteCourseOpen.value = true
}

async function confirmDeleteCourse() {
  const course = deleteCourseTarget.value
  if (!course || deleteCourseBusy.value) return
  deleteCourseBusy.value = true
  deleteCourseError.value = ''
  try {
    await deleteCourse(course.id)
    toast('课程已删除', 'ok')
    deleteCourseOpen.value = false
    deleteCourseTarget.value = null
    await refreshCourses()
  } catch (error) {
    deleteCourseError.value = definitionErrorText(error)
  } finally {
    deleteCourseBusy.value = false
  }
}

onMounted(async () => {
  courses.value = await getCourses(role.value as Role)
  if (role.value === 'student') await refreshJoinable()
})
</script>

<style scoped>
.section-list {
  display: grid;
  gap: 6px;
}
.section-option {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 0 8px;
  border: 1px solid var(--line);
  border-radius: var(--r-field, 8px);
  cursor: pointer;
}
.section-option small {
  margin-left: auto;
  color: var(--ink-3);
}
</style>
