<template>
  <aside class="sidenav" aria-label="主导航">
    <div class="side-brand-row">
      <button class="side-brand brand" @click="go(ROLE_HOME[role])">
        <span class="brand-mark">枢</span>
        <span class="brand-text">
          <b>云枢教学平台</b>
          <small>YUNSHU · IA / RECORD WORKSPACE</small>
        </span>
      </button>
    </div>
    <button class="workspace" @click="onWorkspace">
      <span class="ws-mark">{{ workspace.mark }}</span>
      <span class="ws-text">
        <b>{{ workspace.title }}</b>
        <small>{{ workspace.sub }}</small>
      </span>
      <V2Icon name="chevron" />
    </button>

    <div class="nav-head">
      <span class="nav-eyebrow">{{ isCourse ? 'COURSE / 课程空间' : 'WORKSPACE / 页面' }}</span>
      <span class="nav-count">{{ String(navCount).padStart(2, '0') }}</span>
    </div>

    <div class="nav-scroll">
      <template v-if="isCourse">
        <button class="side-back" title="返回我的课程" @click="go(coursesPath)">
          <V2Icon name="arrow" />
          <span class="side-back-label">返回{{ role === 'admin' ? '课程目录' : '我的课程' }}</span>
        </button>
        <button
          class="course-rail-mark"
          :aria-label="`当前课程：${courseLabel}`"
          :title="courseLabel"
          @click="goTab('overview')"
        >
          {{ courseMark }}
        </button>
        <div class="course-nav-card">
          <p class="course-nav-kicker">COURSE SPACE / {{ courseSemester || '课程空间' }}</p>
          <div class="course-nav-title">
            {{ courseLabel }}
            <span v-if="courseContext.status" class="chip chip-ok">{{ courseContext.status }}</span>
          </div>
          <p class="course-nav-meta">
            {{
              [courseContext.code, courseContext.teacher].filter(Boolean).join(' · ') ||
              '课程信息加载中'
            }}
          </p>
          <label v-if="courses.length" class="course-switcher-wrap">
            <span>切换课程</span>
            <select
              class="course-switcher"
              :value="courseId"
              aria-label="切换课程"
              @change="switchCourse(($event.target as HTMLSelectElement).value)"
            >
              <option v-for="course in courses" :key="course.id" :value="course.id">
                {{ course.name }}{{ course.code ? ` · ${course.code}` : '' }}
              </option>
            </select>
          </label>
        </div>
        <div class="nav-group-label nav-group-label--course">
          <span>课程页签</span>
          <span>{{ String(tabs.length).padStart(2, '0') }}</span>
        </div>
        <div class="nav-group course-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            class="nav-item"
            :class="{ 'is-active': activeTab === tab.id }"
            :title="tab.label"
            :aria-current="activeTab === tab.id ? 'page' : undefined"
            @click="goTab(tab.id)"
          >
            <V2Icon :name="tab.icon || 'dashboard'" />
            <span class="nav-label">{{ tab.label }}</span>
          </button>
        </div>
      </template>

      <template v-else>
        <div v-for="group in groups" :key="group.label" class="nav-group">
          <div class="nav-group-label">
            <span>{{ group.label }}</span>
            <span>{{ String(group.items.length).padStart(2, '0') }}</span>
          </div>
          <button
            v-for="item in group.items"
            :key="item.id"
            class="nav-item"
            :class="{ 'is-active': isActive(item.path) }"
            :title="item.label"
            :aria-current="isActive(item.path) ? 'page' : undefined"
            @click="go(item.path)"
          >
            <V2Icon :name="item.icon" />
            <span class="nav-label">{{ item.label }}</span>
            <span v-if="item.id === 'messages' && unread > 0" class="nav-badge hot">{{
              unread
            }}</span>
          </button>
        </div>
      </template>
    </div>

    <div class="nav-foot">
      <button
        class="side-user"
        :aria-label="`打开个人中心：${avatar} ${displayName} ${roleLabel}`"
        @click="go(profilePath)"
      >
        <span class="user-avatar">{{ avatar }}</span>
        <span class="user-text">
          <b>{{ displayName }}</b>
          <small>{{ roleLabel }}</small>
        </span>
        <V2Icon name="settings" />
      </button>
      <div class="nav-foot-meta">
        <span>v2 / RECORD</span>
        <button class="text-btn nav-logout" @click="logout">退出</button>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import V2Icon from './V2Icon.vue'
import { useV2Ui } from '../composables/ui'
import { ROLE_HOME, courseTabs, navGroups, type V2Role } from '../nav'
import { clearCourses, getCourses, type V2CourseCard } from '../data/api'

const route = useRoute()
const router = useRouter()
const { auth, role, roleLabel, displayName, avatar, unread, courseContext, mobileNavOpen, toast } =
  useV2Ui()

const courses = ref<V2CourseCard[]>([])
const isCourse = computed(() => Boolean(route.meta.v2course))
const courseId = computed(() => Number(route.params.id) || courseContext.id || 0)
const activeTab = computed(() => String(route.params.tab || 'overview'))
const tabs = computed(() => courseTabs(role.value as V2Role))
const groups = computed(() => navGroups(role.value as V2Role))
const coursesPath = computed(() => `/${role.value}/courses`)
const profilePath = computed(() => `/${role.value}/profile`)
const navCount = computed(() =>
  isCourse.value
    ? tabs.value.length
    : groups.value.reduce((sum, group) => sum + group.items.length, 0),
)

const courseLabel = computed(
  () => courseContext.name || courses.value.find((c) => c.id === courseId.value)?.name || '课程',
)
const courseMark = computed(() => {
  const short = courses.value.find((c) => c.id === courseId.value)?.short
  return short || courseLabel.value.slice(0, 2)
})
const courseSemester = computed(() => courseContext.semester || '')

const workspace = computed(() => {
  if (isCourse.value) {
    return {
      mark: courseMark.value,
      title: courseLabel.value,
      sub: [courseContext.semester, courseContext.code].filter(Boolean).join(' · ') || '课程空间',
    }
  }
  if (role.value === 'admin') {
    return { mark: '云', title: '云枢大学', sub: '校级治理 · 2026 秋' }
  }
  return {
    mark: '计',
    title: '计算机学院',
    sub: role.value === 'teacher' ? '教师空间 · 2026 秋' : '学生空间 · 2026 秋',
  }
})

function isActive(path: string): boolean {
  if (route.path === path) return true
  if (path === `/${role.value}/messages`) return false
  return route.path.startsWith(`${path}/`)
}

async function loadCourses() {
  courses.value = await getCourses(role.value as V2Role)
}

watch(
  () => [role.value, isCourse.value],
  async ([, courseMode]) => {
    if (courseMode && auth.isLoggedIn) await loadCourses()
  },
  { immediate: true },
)

function go(path: string) {
  mobileNavOpen.value = false
  router.push(path)
}

function goTab(tab: string) {
  if (!courseId.value) return
  mobileNavOpen.value = false
  router.push(`/${role.value}/courses/${courseId.value}/${tab}`)
}

function switchCourse(value: string) {
  const id = Number(value)
  if (!id || id === courseId.value) return
  mobileNavOpen.value = false
  router.push(`/${role.value}/courses/${id}/${activeTab.value}`)
}

function onWorkspace() {
  if (isCourse.value) {
    toast('课程信息：切换课程请使用下方下拉框', 'info')
    return
  }
  toast(role.value === 'admin' ? '演示数据：院系作用域需后端支持' : '演示数据：工作区切换', 'info')
}

async function logout() {
  clearCourses()
  await auth.logout()
  router.push('/login')
}
</script>
