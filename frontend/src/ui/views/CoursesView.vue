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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHead from '../components/PageHead.vue'
import Icon from '../components/Icon.vue'
import Modal from '../components/Modal.vue'
import CourseFolderCard from '../components/CourseFolderCard.vue'
import studentApi from '@/api/student'
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
