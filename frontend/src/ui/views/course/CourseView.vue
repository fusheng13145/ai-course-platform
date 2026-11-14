<template>
  <div v-if="!validTab" class="card empty-state">
    <b>未知的课程页签</b>
    <p>正在返回课程概览…</p>
  </div>
  <template v-else>
    <header class="course-head">
      <div class="course-head-main">
        <div class="course-title-line">
          <h1>{{ detail?.name || courseLabel }}</h1>
          <span class="chip" :class="statusChip.cls">{{ statusChip.text }}</span>
        </div>
      </div>
      <div class="course-mobile-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="{ 'is-active': currentTab === tab.id }"
          @click="goTab(tab.id)"
        >
          {{ tab.label }}
        </button>
      </div>
    </header>

    <div class="course-body">
      <CourseOverviewTab
        v-if="currentTab === 'overview'"
        :course-id="courseId"
        :role="role"
        :course="courseRecord"
      />
      <GradingTab v-else-if="currentTab === 'grading'" :course-id="courseId" :role="role" />
      <TasksTab
        v-else-if="currentTab === 'tasks' && (role === 'teacher' || role === 'student')"
        :course-id="courseId"
        :role="role"
      />
      <ChaptersTab
        v-else-if="currentTab === 'chapters' && role === 'teacher'"
        :course-id="courseId"
      />
      <ReaderTab
        v-else-if="currentTab === 'chapters' && role === 'student'"
        :course-id="courseId"
      />
      <AnalyticsTab v-else-if="currentTab === 'analytics'" :course-id="courseId" />
      <AdminReadOnlyTab v-else-if="adminReadOnly" :tab="currentTab" />
      <KnowledgeGraphTab
        v-else-if="currentTab === 'graph' && (role === 'teacher' || role === 'student')"
        :course-id="courseId"
        :mode="role === 'student' ? 'student' : 'teacher'"
      />
      <AiCenterTab
        v-else-if="currentTab === 'ai' && (role === 'teacher' || role === 'student')"
        :course-id="courseId"
        :mode="role === 'student' ? 'student' : 'teacher'"
      />
      <GradesTab v-else-if="currentTab === 'grades'" :role="role" :course-id="courseId" />
      <TeacherBankView
        v-else-if="currentTab === 'bank' && role === 'teacher'"
        :course-id="courseId"
      />
      <TeacherExamsView
        v-else-if="currentTab === 'exams' && role === 'teacher'"
        :course-id="courseId"
      />
      <ProctorTab v-else-if="currentTab === 'proctor'" :course-id="courseId" />
      <StudentExamPanel
        v-else-if="currentTab === 'exams' && role === 'student'"
        :course-id="courseId"
      />
      <SettingsTab
        v-else-if="currentTab === 'settings' || currentTab === 'approval'"
        :role="role"
        :course="courseForSettings"
        :course-id="courseId"
        @updated="applyCourseUpdate"
      />
      <DiscussionTab
        v-else-if="currentTab === 'discussion' && (role === 'teacher' || role === 'student')"
        :course-id="courseId"
        :role="role"
      />
      <CourseNoticesTab
        v-else-if="currentTab === 'notices' && (role === 'teacher' || role === 'student')"
        :course-id="courseId"
        :role="role"
      />
      <AiCorpusTab
        v-else-if="currentTab === 'corpus' && role === 'teacher'"
        :course-id="courseId"
      />
      <TabUnavailable v-else />
    </div>
  </template>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { track } from '@/utils/track'
import CourseOverviewTab from './tabs/CourseOverviewTab.vue'
import GradingTab from './tabs/GradingTab.vue'
import ReaderTab from './tabs/ReaderTab.vue'
import AnalyticsTab from './tabs/AnalyticsTab.vue'
import AdminReadOnlyTab from './tabs/AdminReadOnlyTab.vue'
import GradesTab from './tabs/GradesTab.vue'
import SettingsTab from './tabs/SettingsTab.vue'
import TabUnavailable from './tabs/TabUnavailable.vue'
import CourseNoticesTab from './tabs/CourseNoticesTab.vue'
import AiCorpusTab from './tabs/AiCorpusTab.vue'
import TeacherBankView from '../teacher/TeacherBankView.vue'
import TeacherExamsView from '../teacher/TeacherExamsView.vue'
import ProctorTab from './tabs/ProctorTab.vue'
import ChaptersTab from './tabs/ChaptersTab.vue'
import TasksTab from './tabs/TasksTab.vue'
import DiscussionTab from './tabs/DiscussionTab.vue'
import KnowledgeGraphTab from './tabs/KnowledgeGraphTab.vue'
import AiCenterTab from './tabs/AiCenterTab.vue'
import StudentExamPanel from '../exam/StudentExamPanel.vue'
import { courseTabs, type Role } from '../../nav'
import { useUi } from '../../composables/ui'
import { loadCourse, getCourses, type CourseDetail } from '../../data/api'

const route = useRoute()
const router = useRouter()
const { role, courseContext, setCourseContext } = useUi()

const detail = ref<CourseDetail | null>(null)
const fallbackName = ref('')
const courseId = computed(() => Number(route.params.id) || 0)
const currentTab = computed(() => String(route.params.tab || 'overview'))
const tabs = computed(() => courseTabs(role.value as Role))
const validTab = computed(() => tabs.value.some((tab) => tab.id === currentTab.value))

// 管理员在这些页签没有任何接口数据面；渲染示例读数会让学生/教师读数被误当成平台事实。
const ADMIN_READONLY_TABS = new Set(['content', 'bank', 'exams', 'grades', 'graph', 'aiUsage'])
const adminReadOnly = computed(
  () => role.value === 'admin' && ADMIN_READONLY_TABS.has(currentTab.value),
)

const courseLabel = computed(
  () =>
    String(detail.value?.name || '') ||
    fallbackName.value ||
    courseContext.name ||
    `课程 #${courseId.value}`,
)

const courseRecord = computed(() => detail.value)
interface CourseLike {
  name?: string
  code?: string
  semester?: string
  credits?: number | null
  category?: string
  approvalStatus?: string
}
const courseForSettings = computed<CourseLike | null>(() => {
  if (!detail.value) return null
  return {
    name: String(detail.value.name ?? ''),
    code: String(detail.value.code ?? ''),
    semester: String(detail.value.semester ?? ''),
    credits: detail.value.credits == null ? null : Number(detail.value.credits),
    category: String(detail.value.category ?? ''),
    approvalStatus: String(detail.value.approvalStatus ?? ''),
  }
})
const statusChip = computed(() => {
  const status = String(detail.value?.approvalStatus || detail.value?.status || '')
  if (status === 'APPROVED' || status === '已通过') return { text: '已通过', cls: 'chip-ok' }
  if (status === 'PENDING' || status === '待审批') return { text: '待审批', cls: 'chip-warn' }
  if (status === 'REJECTED' || status === '已驳回') return { text: '已驳回', cls: 'chip-danger' }
  return { text: status || '课程', cls: 'chip-info' }
})

function goTab(tab: string) {
  router.push(`/${role.value}/courses/${courseId.value}/${tab}`)
}

async function load() {
  if (!courseId.value) return
  detail.value = await loadCourse(role.value as Role, courseId.value)
  if (!detail.value) {
    const rows = await getCourses(role.value as Role)
    fallbackName.value = rows.find((row) => row.id === courseId.value)?.name || ''
  }
  setCourseContext({
    id: courseId.value,
    name: String(detail.value?.name || fallbackName.value || ''),
    code: String(detail.value?.code || ''),
    semester: String(detail.value?.semester || ''),
    credits: detail.value?.credits == null ? null : Number(detail.value.credits),
    teacher: String(detail.value?.teacher || ''),
    status: statusChip.value.text,
  })
  if (!validTab.value) {
    router.replace(`/${role.value}/courses/${courseId.value}/overview`)
  }
}

// 业务埋点（2026-11-07 P2–P3 批次）：course_view 是学习漏斗的第一个事件；
// 端点未配置时 track 为 no-op，不影响页面行为。
onMounted(() => {
  track({ event: 'course_view', props: { courseId: courseId.value } })
  void load()
})

/** 设置页保存后直接用 PUT 响应回填：后端课程缓存未随更新失效，重新读会拿到旧值 */
function applyCourseUpdate(course: Record<string, unknown>) {
  if (!detail.value) return
  detail.value = { ...detail.value, ...course } as typeof detail.value
  setCourseContext({
    id: courseId.value,
    name: String(detail.value?.name || fallbackName.value || ''),
    code: String(detail.value?.code || ''),
    semester: String(detail.value?.semester || ''),
    credits: detail.value?.credits == null ? null : Number(detail.value.credits),
    teacher: String(detail.value?.teacher || ''),
    status: statusChip.value.text,
  })
}

watch(() => route.params.id, load)
watch(
  () => route.params.tab,
  (tab) => {
    if (tab && !tabs.value.some((item) => item.id === tab)) {
      router.replace(`/${role.value}/courses/${courseId.value}/overview`)
    }
  },
)
</script>
