<template>
  <aside class="sidenav" aria-label="主导航">
    <!-- 折叠手柄：骑跨在侧栏右缘上 -->
    <button
      class="side-collapse"
      :aria-label="sidebarCollapsed ? '展开导航' : '收起导航'"
      :aria-expanded="sidebarCollapsed ? 'false' : 'true'"
      @click="sidebarCollapsed = !sidebarCollapsed"
    >
      <Icon name="chevron" />
    </button>

    <div class="nav-scroll">
      <template v-if="isCourse">
        <button
          class="course-rail-mark"
          :aria-label="`当前课程：${courseLabel}`"
          :title="courseLabel"
          @click="goTab('overview')"
        >
          {{ courseMark }}
        </button>
        <div class="course-nav-pin">
          <div class="course-nav-card">
            <div class="course-nav-title">
              {{ courseLabel }}
              <span v-if="courseContext.status" class="chip chip-ok">{{
                courseContext.status
              }}</span>
            </div>
            <p v-if="courseMeta" class="course-nav-meta">{{ courseMeta }}</p>
            <label v-if="courses.length" class="course-switcher-wrap">
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
            <Icon :name="tab.icon || 'dashboard'" />
            <span class="nav-label">{{ tab.label }}</span>
          </button>
        </div>
      </template>

      <template v-else>
        <div v-for="group in groups" :key="group.label" class="nav-group">
          <div class="nav-group-label">
            <span>{{ group.label }}</span>
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
            <Icon :name="item.icon" />
            <span class="nav-label">{{ item.label }}</span>
            <span v-if="item.id === 'messages' && unread > 0" class="nav-badge hot">{{
              unread
            }}</span>
          </button>
        </div>
      </template>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './Icon.vue'
import { useUi } from '../composables/ui'
import { courseTabs, navGroups, type Role } from '../nav'
import { getCourses, type CourseCard } from '../data/api'

const route = useRoute()
const router = useRouter()
const { auth, role, unread, courseContext, mobileNavOpen, sidebarCollapsed } = useUi()

const courses = ref<CourseCard[]>([])
const isCourse = computed(() => Boolean(route.meta.courseSpace))
const courseId = computed(() => Number(route.params.id) || courseContext.id || 0)
const activeTab = computed(() => String(route.params.tab || 'overview'))
const tabs = computed(() => courseTabs(role.value as Role))
// 「个人中心」入口已上移至顶栏右上角的账号菜单，侧栏不再重复（Palette 搜索仍可直达该页）
const groups = computed(() =>
  navGroups(role.value as Role)
    .map((group) => ({ ...group, items: group.items.filter((item) => item.id !== 'profile') }))
    .filter((group) => group.items.length > 0),
)

const courseLabel = computed(
  () => courseContext.name || courses.value.find((c) => c.id === courseId.value)?.name || '课程',
)
const courseMark = computed(() => {
  const short = courses.value.find((c) => c.id === courseId.value)?.short
  return short || courseLabel.value.slice(0, 2)
})
const courseMeta = computed(() =>
  [courseContext.code, courseContext.teacher].filter(Boolean).join(' · '),
)

function isActive(path: string): boolean {
  if (route.path === path) return true
  if (path === `/${role.value}/messages`) return false
  return route.path.startsWith(`${path}/`)
}

async function loadCourses() {
  courses.value = await getCourses(role.value as Role)
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
</script>
