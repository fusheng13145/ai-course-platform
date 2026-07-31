<template>
  <div>
    <!-- 课程头卡 -->
    <section class="l1-card" style="margin-bottom:14px">
      <div class="l1-body" style="display:flex;gap:18px;align-items:center">
        <div :style="{ width:'86px',height:'86px',borderRadius:'14px',background:`linear-gradient(135deg,${coverColors[coverIdx]})`,display:'grid',placeItems:'center',color:'#fff',flexShrink:0 }">
          <svg viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M4 19.5A2.5 2.5 0 0 0 6.5 22H20V2H6.5A2.5 2.5 0 0 0 4 4.5v15z"/></svg>
        </div>
        <div style="flex:1">
          <div class="flex gap-8">
            <span style="font-size:18px;font-weight:700;letter-spacing:-.3px">{{ courseName || '计算机网络' }}</span>
            <span class="tag outline mono">{{ courseCode || 'CS3101' }}</span>
            <span class="tag green">进行中</span>
            <router-link to="/teacher/courses" class="btn btn-ghost btn-sm" style="margin-left:auto">← 返回课程列表</router-link>
          </div>
          <div class="small muted mt-8">2025-2026-2 · 4 学分 · 共 {{ classes.length }} 个班级 {{ totalStudents }} 名学生</div>
        </div>
      </div>
      <!-- 班级 Tab —— 一级隔离维度 -->
      <div class="tabs-line">
        <div v-for="(cls, i) in classes" :key="cls.key" class="tab-i" :class="{ active: activeClass === cls.key }" @click="activeClass = cls.key">
          <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"/></svg>
          {{ cls.name }}
          <span class="tab-badge">{{ cls.students }}</span>
          <span v-if="cls.pending" class="tab-badge" style="background:var(--accent-soft);color:var(--accent)">{{ cls.pending }} 待批</span>
        </div>
      </div>
    </section>

    <!-- 模块子 Tab + 内容 -->
    <div class="mb-12 flex between">
      <div class="tabs-pill">
        <div v-for="tab in cockpitTabs" :key="tab.path" class="tab-i" :class="{ active: activeCockpit === tab.path }" @click="activeCockpit = tab.path">
          {{ tab.label }}
          <span v-if="tab.badge != null" class="tab-badge" :style="{ background: tab.badge ? 'var(--accent-soft)' : '', color: tab.badge ? 'var(--accent)' : '' }">{{ tab.badge }}</span>
        </div>
      </div>
      <span class="tag blue">当前班级：{{ currentClass?.name || '—' }} · {{ currentClass?.schedule || '' }}</span>
    </div>

    <div>
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import courseApi from '@/api/course'
import { unwrap } from '@/utils/response'

const route = useRoute()
const router = useRouter()
const courseId = computed(() => route.params.id)

const courseName = ref('')
const courseCode = ref('')
const coverIdx = ref(0)
const coverColors = ['#8f2a21,#c13b30', '#1a4a80,#2563a8', '#1f5c39,#2e7d4f', '#6d4fa1,#8b6cc0']

const classes = ref([
  { key: 'cs2301', name: '计科2301', students: 46, pending: 8, schedule: '周一 3-4节 · 笃学楼B204' },
  { key: 'cs2302', name: '计科2302', students: 45, pending: 11, schedule: '周二 5-6节 · 笃学楼B206' },
  { key: 'cs2303', name: '计科2303', students: 47, pending: 5, schedule: '周四 1-2节 · 明德楼C301' },
])
const activeClass = ref(classes.value[0]?.key || '')
const currentClass = computed(() => classes.value.find(c => c.key === activeClass.value))
const totalStudents = computed(() => classes.value.reduce((s, c) => s + c.students, 0))

const cockpitTabs = [
  { path: 'chapters', label: '课程章节' },
  { path: 'overview', label: '课程资料' },
  { path: 'tasks', label: '作业管理', badge: 8 },
  { path: 'exams', label: '考试管理' },
  { path: 'submissions', label: '成员管理' },
  { path: 'grades', label: '成绩统计' },
]
const activeCockpit = ref('chapters')

// Navigate to selected cockpit tab
watch(activeCockpit, (tab) => {
  router.push(`/teacher/courses/${courseId.value}/${tab}`)
})

// Initialize from route
onMounted(async () => {
  const segments = route.path.split('/')
  const last = segments[segments.length - 1]
  // Check if last segment matches a tab key
  const match = cockpitTabs.find(t => t.path === last)
  if (match) activeCockpit.value = last
  else activeCockpit.value = 'chapters'
  // Try to load course info from API
  try {
    const res = await courseApi.getDetail(courseId.value)
    const data = unwrap(res)
    if (data) {
      courseName.value = data.name || data.courseName || courseName.value
      courseCode.value = data.code || data.courseCode || courseCode.value
    }
  } catch { /* use defaults */ }
})
</script>
