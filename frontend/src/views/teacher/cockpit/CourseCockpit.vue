<!-- 课程壳页：TEACHER / ADMIN 双角色消费，角色按 route 首段推导 -->
<template>
  <div>
    <!-- 课程头卡 -->
    <section class="l1-card" style="margin-bottom: 14px">
      <!-- 加载失败持久提示（不自动消失） -->
      <el-alert
        v-if="loadError"
        :title="loadError"
        type="error"
        :closable="false"
        class="load-error-alert"
      />
      <div class="l1-body" style="display: flex; gap: 18px; align-items: center">
        <div
          :style="{
            width: '86px',
            height: '86px',
            borderRadius: '14px',
            background: `linear-gradient(135deg,${coverColors[coverIdx]})`,
            display: 'grid',
            placeItems: 'center',
            color: 'var(--surface)',
            flexShrink: 0,
          }"
        >
          <svg
            viewBox="0 0 24 24"
            width="34"
            height="34"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path
              d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M4 19.5A2.5 2.5 0 0 0 6.5 22H20V2H6.5A2.5 2.5 0 0 0 4 4.5v15z"
            />
          </svg>
        </div>
        <div style="flex: 1">
          <div class="flex gap-8">
            <span style="font-size: 18px; font-weight: 700; letter-spacing: -0.3px">{{
              courseName || '加载中…'
            }}</span>
            <span v-if="courseCode" class="tag outline mono">{{ courseCode }}</span>
            <span class="tag" :class="statusClass">{{ statusLabel }}</span>
            <router-link
              :to="`${rolePrefix}/courses`"
              class="btn btn-ghost btn-sm"
              style="margin-left: auto"
              >← 返回课程列表</router-link
            >
          </div>
          <div class="small muted mt-8">{{ courseMeta }}</div>
        </div>
      </div>
      <!-- 班级 Tab —— 一级隔离维度（课程未绑定行政班时不显示，避免伪造「全部班级 0 人」） -->
      <div v-if="classes.length" class="tabs-line">
        <div
          v-for="(cls, i) in classes"
          :key="cls.key"
          class="tab-i"
          :class="{ active: activeClass === cls.key }"
          @click="activeClass = cls.key"
        >
          <svg
            viewBox="0 0 24 24"
            width="13"
            height="13"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z" />
          </svg>
          {{ cls.name }}
          <span class="tab-badge">{{ cls.students }}</span>
          <span
            v-if="cls.pending"
            class="tab-badge"
            style="background: var(--accent-soft); color: var(--accent)"
            >{{ cls.pending }} 待批</span
          >
        </div>
      </div>
    </section>

    <!-- 模块子 Tab + 内容 -->
    <div class="mb-12 flex between">
      <div class="tabs-pill">
        <div
          v-for="tab in cockpitTabs"
          :key="tab.path"
          class="tab-i"
          :class="{ active: activeCockpit === tab.path }"
          @click="activeCockpit = tab.path"
        >
          {{ tab.label }}
          <span
            v-if="tab.path === 'tasks' && totalPending"
            class="tab-badge"
            :style="{
              background: totalPending ? 'var(--accent-soft)' : '',
              color: totalPending ? 'var(--accent)' : '',
            }"
            >{{ totalPending }}</span
          >
        </div>
      </div>
      <span class="tag blue">{{ currentClassLabel }}</span>
    </div>

    <div>
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import courseApi from '@/api/course'
import { getCourseClasses } from '@/api/teacher'
import { COURSE_TABS } from '@/layouts/menus'
import { unwrap } from '@/utils/response'

const route = useRoute()
const router = useRouter()
const courseId = computed(() => route.params.id)

// 壳同时挂在 /teacher/courses/:id 与 /admin/courses/:id 下，角色由路径首段推导：
// 不新增 prop 是为了避免调用方（路由表）漏传，两处声明漂移。
const role = computed(() => (route.path.split('/')[1] === 'admin' ? 'ADMIN' : 'TEACHER'))
const rolePrefix = computed(() => `/${role.value.toLowerCase()}`)

const courseName = ref('')
const courseCode = ref('')
const courseStatus = ref('')
const semester = ref('')
const credits = ref(null)
const enrolledStudents = ref(null)
const coverIdx = ref(0)
const coverColors = ['#8f2a21,#c13b30', '#1a4a80,#2563a8', '#1f5c39,#2e7d4f', '#6d4fa1,#8b6cc0']

// 三路加载各自的独立错误状态：互不复位，防止一路失败清除另一路的提示。
const detailError = ref('')
const statsError = ref('')
const classesError = ref('')
const loadError = computed(() => detailError.value || statsError.value || classesError.value)

const classes = ref([])
const activeClass = ref('')
const currentClass = computed(() => classes.value.find((c) => c.key === activeClass.value))
const totalPending = computed(() => classes.value.reduce((s, c) => s + (c.pending || 0), 0))

const statusLabel = computed(() => {
  if (!courseStatus.value) return '未同步'
  return courseStatus.value === 'ACTIVE' ? '进行中' : '已停用'
})
const statusClass = computed(() => (courseStatus.value === 'ACTIVE' ? 'green' : ''))

/** 头部副标题：逐项只在拿到真实数据后渲染，缺字段就不显示，不再用硬编码串占位。
 *  加载失败的读数改显 '--'，避免把「0 名学生 / 未绑定行政班」当成真实结果误导教师。 */
const courseMeta = computed(() => {
  const parts = []
  if (detailError.value) {
    parts.push('--')
  } else {
    if (semester.value) parts.push(semester.value)
    if (credits.value != null) parts.push(`${credits.value} 学分`)
  }
  // 学生数优先取后端统计；统计不可用但班级维度可用则按班级求和（DR-46，不得报 0）；
  // 两路都拿不到真实读数时才显 '--'，避免把「未知」伪装成 0。
  const fallbackStudents =
    classesError.value || classes.value.length === 0
      ? null
      : classes.value.reduce((s, c) => s + (c.students || 0), 0)
  const students = enrolledStudents.value ?? fallbackStudents
  parts.push(students == null ? '-- 名学生' : `${students} 名学生`)
  if (classesError.value) {
    parts.push('--')
  } else {
    parts.push(classes.value.length ? `共 ${classes.value.length} 个班级` : '未绑定行政班')
  }
  return parts.join(' · ')
})

const currentClassLabel = computed(() => {
  if (classesError.value) return '--'
  if (!currentClass.value) return '按课程维度（未绑定行政班）'
  return `当前班级：${currentClass.value.name}${currentClass.value.schedule ? ` · ${currentClass.value.schedule}` : ''}`
})

// 页签清单以 menus.ts 的 COURSE_TABS 为单一来源：侧边栏三级页签与本壳页签同源，
// 文案与入口不会再各写一份而漂移（DR-47 里 submissions 曾被误标成「成员管理」）。
// role 只可能是 TEACHER / ADMIN，两者都有配置，故不做 `|| TEACHER` 兜底：
// 兜底只会把「新增角色漏配页签」压成静默降级——管理员拿到 14 个教师页签，其中 6 个在管理端没有子路由。
const cockpitTabs = computed(() =>
  COURSE_TABS[role.value].map((t) => ({ path: t.suffix, label: t.text })),
)
const activeCockpit = ref('chapters')

// Navigate to selected cockpit tab
watch(activeCockpit, (tab) => {
  router.push(`${rolePrefix.value}/courses/${courseId.value}/${tab}`)
})

// 侧边栏/外部链接切换页签时同步高亮
watch(
  () => route.path,
  (p) => {
    const last = p.split('/').pop()
    if (cockpitTabs.value.some((t) => t.path === last)) activeCockpit.value = last
  },
)

// Initialize from route
onMounted(async () => {
  const segments = route.path.split('/')
  const last = segments[segments.length - 1]
  // Check if last segment matches a tab key
  const match = cockpitTabs.value.find((t) => t.path === last)
  if (match) activeCockpit.value = last
  else activeCockpit.value = 'chapters'
  // 课程主体信息 + 报名统计并行拉取：/courses/{id} 给学期与学分，/courses/{id}/stats 给真实学生数
  detailError.value = ''
  statsError.value = ''
  classesError.value = ''
  const [detailRes, statsRes] = await Promise.allSettled([
    courseApi.getCourseById(courseId.value),
    courseApi.getStats(courseId.value),
  ])
  if (detailRes.status === 'fulfilled') {
    const detail = unwrap(detailRes.value)
    if (detail) {
      courseName.value = String(detail.name || detail.courseName || '')
      courseCode.value = String(detail.code || detail.courseCode || '')
      courseStatus.value = String(detail.status || '')
      semester.value = String(detail.semester || '')
      const parsedCredits = Number(detail.credits)
      credits.value = Number.isFinite(parsedCredits) ? parsedCredits : null
    }
  } else {
    detailError.value = '课程基本信息加载失败，请检查网络后重试'
  }
  if (statsRes.status === 'fulfilled') {
    const stats = unwrap(statsRes.value)
    const parsedStudents = Number(stats?.totalStudents)
    enrolledStudents.value = Number.isFinite(parsedStudents) ? parsedStudents : null
  } else {
    statsError.value = '课程统计加载失败，请检查网络后重试'
  }
  // Load real class dimensions from backend
  try {
    const res = await getCourseClasses(courseId.value)
    const data = unwrap(res)
    if (Array.isArray(data) && data.length > 0) {
      classes.value = data
      activeClass.value = data[0]?.key || ''
    }
  } catch {
    /* 班级维度缺失：保持空数组，但页面给出持久失败提示，不静默当作「未绑定行政班」 */
    classesError.value = '课程班级维度加载失败，请检查网络后重试'
  }
})
</script>

<style scoped>
.load-error-alert {
  margin-bottom: 12px;
}
</style>
