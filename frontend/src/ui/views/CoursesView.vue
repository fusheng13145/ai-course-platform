<template>
  <section class="module">
    <PageHead :kicker="kicker" :title="def.title" :sub="def.desc" :source="source">
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
        <button
          v-if="role !== 'admin'"
          class="btn btn-primary"
          :disabled="joining"
          @click="onPrimary"
        >
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
      <div class="table-tools">
        <span class="chip chip-mute">{{ role === 'admin' ? '治理视图' : '课程空间' }}</span>
      </div>
    </div>

    <div v-if="filtered.length" class="course-grid">
      <article v-for="course in filtered" :key="course.id" class="course-tile">
        <button class="course-tile-cover" :data-tone="course.tone" @click="open(course.id)">
          <span>{{ course.short }}</span>
          <small>{{ [course.code, course.teacher].filter(Boolean).join(' · ') || '课程' }}</small>
          <span class="chip course-tile-progress">{{ course.progress }}%</span>
        </button>
        <div class="course-tile-body">
          <div class="course-tile-head">
            <h3>{{ course.name }}</h3>
          </div>
          <p>
            {{
              [course.semester, course.students ? `${course.students} 名学生` : '']
                .filter(Boolean)
                .join(' · ') || '课程信息'
            }}
          </p>
          <div class="course-bar"><i :style="{ '--p': `${course.progress}%` }"></i></div>
          <div class="course-tile-foot">
            <span>{{ course.progress >= 70 ? '进展良好' : '需要关注' }}</span>
            <div class="row-actions">
              <button
                v-if="role === 'student'"
                class="btn btn-sm btn-ghost"
                :disabled="dropping"
                @click="askDrop(course)"
              >
                退选
              </button>
              <button class="btn btn-sm" @click="open(course.id)">
                {{ role === 'admin' ? '查看治理' : '进入课程' }}
              </button>
            </div>
          </div>
        </div>
      </article>
    </div>

    <div v-else class="card empty-state">
      <span class="e-ico"><Icon name="book" /></span>
      <b>{{ emptyTitle }}</b>
      <p>{{ emptyHint }}</p>
      <button class="btn btn-sm" @click="filter = 'all'">查看全部课程</button>
    </div>

    <!-- 退选二次确认 -->
    <Modal v-model:open="dropOpen" title="退选课程" kicker="CONFIRM / ACTION" width="460px">
      <p class="modal-text">
        确定退选「{{
          dropTarget?.name || '该课程'
        }}」？退选后课程将从「我的课程」移除，既有学习记录保留。
      </p>
      <p class="modal-hint">如需重新学习，可在课程目录中再次加入。</p>
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
import studentApi from '@/api/student'
import { pageDef, type Role } from '../nav'
import { useUi } from '../composables/ui'
import { getCourses, getCourseCatalog, type CourseCard } from '../data/api'

const router = useRouter()
const { role, toast } = useUi()
const courses = ref<CourseCard[]>([])
const filter = ref<'all' | 'active' | 'done'>('all')
const source = ref<'api'>('api')
// 学生「加入课程」：从课程目录里挑一门未选课程，走真实选课写口（非演示动作）。
const selectedCourseId = ref<number | ''>('')
const joinable = ref<CourseCard[]>([])
const joining = ref(false)
// 退选：后端 DELETE /student/courses/{courseId}/enroll 已存在，二次确认后走真实写口。
const dropOpen = ref(false)
const dropTarget = ref<CourseCard | null>(null)
const dropping = ref(false)

const def = computed(() => pageDef(role.value as Role, 'courses'))
const kicker = computed(() => `${String(role.value).toUpperCase()} / COURSES · 2026 秋`)
const primaryLabel = computed(() => (role.value === 'student' ? '加入课程' : '新建课程'))
const doneCount = computed(() => courses.value.filter((course) => course.progress >= 100).length)
const activeCount = computed(() => courses.value.length - doneCount.value)
const filtered = computed(() => {
  if (filter.value === 'done') return courses.value.filter((course) => course.progress >= 100)
  if (filter.value === 'active') return courses.value.filter((course) => course.progress < 100)
  return courses.value
})
const emptyTitle = computed(() => (filter.value === 'done' ? '暂无已完成课程' : '暂无课程'))
const emptyHint = computed(() =>
  filter.value === 'done'
    ? '完成章节任务后，课程会自动归档到这里。'
    : '课程数据加载后会自动出现在这里。',
)

function open(id: number) {
  router.push(`/${role.value}/courses/${id}/overview`)
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
  joining.value = true
  try {
    // 后端契约：StudentController.enrollCourse —— POST /student/courses/{courseId}/enroll，
    // courseId 走路径参数、无请求体；服务层落库 isActive=true，故 GET /student/courses 立刻可见。
    await studentApi.enrollCourse(courseId)
    toast('已加入课程', 'ok')
    selectedCourseId.value = ''
    courses.value = await getCourses('student', true)
    await refreshJoinable()
  } catch {
    toast('加入课程失败：课程可能未通过审核、已满员或已加入', 'warn')
  } finally {
    joining.value = false
  }
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
  if (role.value === 'student') {
    await joinSelectedCourse()
  }
}

onMounted(async () => {
  courses.value = await getCourses(role.value as Role)
  // 列表为空 ≠ 示例数据：这一页的读数一律来自接口，空列表由下方空态承接，
  // 标成「示例数据」等于把「没有课程」说成「课程是假的」。
  source.value = 'api'
  if (role.value === 'student') await refreshJoinable()
})
</script>
