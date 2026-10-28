<template>
  <div v-if="!validTab" class="card empty-state">
    <b>未知的课程页签</b>
    <p>正在返回课程概览…</p>
  </div>
  <template v-else>
    <header class="course-head">
      <div class="course-head-main">
        <button class="course-back" @click="backToCourses">
          <V2Icon name="arrow" />
          {{ role === 'admin' ? '课程目录' : '我的课程' }}
        </button>
        <div class="course-title-line">
          <span class="course-code">{{ detail?.code || '—' }}</span>
          <h1>{{ detail?.name || courseLabel }}</h1>
          <span class="chip" :class="statusChip.cls">{{ statusChip.text }}</span>
        </div>
        <p class="course-meta">{{ metaLine }}</p>
      </div>
      <div class="course-head-side">
        <span class="class-badge" title="后端尚未实现教学班模型">
          {{
            role === 'teacher' ? '教学班：待接入' : role === 'student' ? '课程级内容' : '管理员只读'
          }}
        </span>
        <button class="btn btn-sm btn-ghost" @click="notReady('课程通知')">课程通知</button>
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

    <div class="course-scope">
      <span class="scope-label">{{ scope.label }}</span>
      <b>{{ scope.title }}</b>
      <span>{{ scope.detail }}</span>
    </div>

    <div class="course-body">
      <CourseOverviewTab
        v-if="currentTab === 'overview'"
        :course-id="courseId"
        :role="role"
        :course="courseRecord"
      />
      <GradingTab v-else-if="currentTab === 'grading'" :course-id="courseId" :role="role" />
      <TasksTab v-else-if="currentTab === 'tasks' && role === 'teacher'" :course-id="courseId" />
      <ChaptersTab
        v-else-if="currentTab === 'chapters' && role === 'teacher'"
        :course-id="courseId"
      />
      <ReaderTab
        v-else-if="currentTab === 'chapters' && role === 'student'"
        :course-id="courseId"
      />
      <AnalyticsTab v-else-if="currentTab === 'analytics'" :course-id="courseId" />
      <GraphTab v-else-if="currentTab === 'graph'" />
      <AiTab v-else-if="currentTab === 'ai'" :role="role" />
      <GradesTab v-else-if="currentTab === 'grades'" :role="role" />
      <V2TeacherBankView
        v-else-if="currentTab === 'bank' && role === 'teacher'"
        :course-id="courseId"
      />
      <V2TeacherExamsView
        v-else-if="currentTab === 'exams' && role === 'teacher'"
        :course-id="courseId"
      />
      <ProctorTab v-else-if="currentTab === 'proctor'" :course-id="courseId" />
      <ExamTab v-else-if="currentTab === 'exams'" :role="role" />
      <SettingsTab
        v-else-if="currentTab === 'settings' || currentTab === 'approval'"
        :role="role"
        :course="courseForSettings"
        :course-id="courseId"
      />
      <ModuleTab v-else :block="block" :role="role" :note="moduleNote" />
    </div>
  </template>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import V2Icon from '../../components/V2Icon.vue'
import CourseOverviewTab from './tabs/CourseOverviewTab.vue'
import GradingTab from './tabs/GradingTab.vue'
import ReaderTab from './tabs/ReaderTab.vue'
import AnalyticsTab from './tabs/AnalyticsTab.vue'
import GraphTab from './tabs/GraphTab.vue'
import AiTab from './tabs/AiTab.vue'
import GradesTab from './tabs/GradesTab.vue'
import ExamTab from './tabs/ExamTab.vue'
import SettingsTab from './tabs/SettingsTab.vue'
import ModuleTab from './tabs/ModuleTab.vue'
import V2TeacherBankView from '../teacher/V2TeacherBankView.vue'
import V2TeacherExamsView from '../teacher/V2TeacherExamsView.vue'
import ProctorTab from './tabs/ProctorTab.vue'
import ChaptersTab from './tabs/ChaptersTab.vue'
import TasksTab from './tabs/TasksTab.vue'
import { courseTabs, type V2Role } from '../../nav'
import { useV2Ui } from '../../composables/ui'
import { loadCourse, getCourses, type V2CourseDetail } from '../../data/api'
import { courseTabBlock, COURSE_TAB_NOTES } from '../../data/courseTabData'

const route = useRoute()
const router = useRouter()
const { role, courseContext, setCourseContext, toast } = useV2Ui()

const detail = ref<V2CourseDetail | null>(null)
const fallbackName = ref('')
const courseId = computed(() => Number(route.params.id) || 0)
const currentTab = computed(() => String(route.params.tab || 'overview'))
const tabs = computed(() => courseTabs(role.value as V2Role))
const validTab = computed(() => tabs.value.some((tab) => tab.id === currentTab.value))

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
const metaLine = computed(
  () =>
    [
      detail.value?.semester,
      detail.value?.category,
      detail.value?.credits != null ? `${detail.value.credits} 学分` : '',
    ]
      .filter(Boolean)
      .join(' · ') || '课程信息加载中',
)

const statusChip = computed(() => {
  const status = String(detail.value?.approvalStatus || detail.value?.status || '')
  if (status === 'APPROVED' || status === '已通过') return { text: '已通过', cls: 'chip-ok' }
  if (status === 'PENDING' || status === '待审批') return { text: '待审批', cls: 'chip-warn' }
  if (status === 'REJECTED' || status === '已驳回') return { text: '已驳回', cls: 'chip-danger' }
  return { text: status || '课程', cls: 'chip-info' }
})

const isCourseLevel = computed(() =>
  ['overview', 'chapters', 'graph', 'settings'].includes(currentTab.value),
)
const scope = computed(() => {
  if (role.value === 'student') {
    return {
      label: '课程级内容',
      title: '所有教学班共享',
      detail: '你的教学班信息待后端接入；章节、作业与考试按课程展示。',
    }
  }
  if (role.value === 'admin') {
    return { label: '管理员只读', title: '治理查看', detail: '不代教师批阅、不改成绩。' }
  }
  if (isCourseLevel.value) {
    return {
      label: '课程级内容',
      title: '所有教学班共享',
      detail: '章节、资源、题库、知识图谱与课程设置',
    }
  }
  return { label: '当前范围', title: '全部学生', detail: '教学班模型待接入；当前按课程聚合' }
})

const block = computed(() => courseTabBlock(role.value as V2Role, currentTab.value, courseId.value))
const moduleNote = computed(
  () =>
    COURSE_TAB_NOTES[currentTab.value] ||
    '示例数据：该课程页签的后端聚合端点尚未接入；旧版对应页面仍可使用。',
)

function goTab(tab: string) {
  router.push(`/v2/${role.value}/courses/${courseId.value}/${tab}`)
}

function backToCourses() {
  router.push(`/v2/${role.value}/courses`)
}

function notReady(label: string) {
  toast(`演示数据：${label}尚未接入`, 'warn')
}

async function load() {
  if (!courseId.value) return
  detail.value = await loadCourse(role.value as V2Role, courseId.value)
  if (!detail.value) {
    const rows = await getCourses(role.value as V2Role)
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
    router.replace(`/v2/${role.value}/courses/${courseId.value}/overview`)
  }
}

onMounted(load)
watch(() => route.params.id, load)
watch(
  () => route.params.tab,
  (tab) => {
    if (tab && !tabs.value.some((item) => item.id === tab)) {
      router.replace(`/v2/${role.value}/courses/${courseId.value}/overview`)
    }
  },
)
</script>
