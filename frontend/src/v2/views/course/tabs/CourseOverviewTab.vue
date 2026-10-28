<template>
  <div>
    <div class="stat-grid kpi-grid">
      <V2Stat v-for="metric in metrics" :key="metric.label" :metric="metric" :role="role" />
    </div>
    <div class="course-overview-grid">
      <section v-if="role === 'student'" class="card course-continue">
        <p class="card-kicker">CONTINUE / 继续学习</p>
        <h2>{{ nextChapter?.title || '第 5 章 · 网络层' }}</h2>
        <p>{{ nextChapterHint }}</p>
        <div class="course-progress-row">
          <span>章节完成度</span><b>{{ progress }}%</b>
        </div>
        <div class="course-bar course-bar--lg"><i :style="{ '--p': `${progress}%` }"></i></div>
        <button class="btn btn-primary" @click="goTab('chapters')">
          继续学习 <V2Icon name="arrow" />
        </button>
      </section>

      <section class="card">
        <header class="card-head">
          <div>
            <p class="card-kicker">CLASSES / 教学班</p>
            <h2>教学班概览</h2>
          </div>
          <span class="chip chip-warn">后端待接入</span>
        </header>
        <div class="mini-class-list">
          <div v-for="cls in demoClasses" :key="cls.name" class="mini-class">
            <span
              ><b>{{ cls.name }}</b
              ><small>{{ cls.students }} 人 · {{ cls.source }}</small></span
            >
            <span
              ><b>{{ cls.progress }}%</b><small>进度</small></span
            >
          </div>
        </div>
        <p class="hint">教学班模型尚未在后端落地，这里沿用原型样本。</p>
      </section>

      <section class="card">
        <header class="card-head">
          <div>
            <p class="card-kicker">
              {{ role === 'student' ? 'NEXT / 本周要完成' : 'TO REVIEW / 待批阅' }}
            </p>
            <h2>{{ role === 'student' ? '待办与考试' : '待批阅' }}</h2>
          </div>
          <button class="text-btn" @click="goTab(role === 'student' ? 'tasks' : 'grading')">
            全部 <V2Icon name="arrow" />
          </button>
        </header>
        <ul class="simple-list">
          <li v-for="item in queue" :key="item.title">
            <span
              ><b>{{ item.title }}</b
              ><small>{{ item.meta }}</small></span
            >
            <span class="chip" :class="`chip-${item.tone}`">{{ item.chip }}</span>
          </li>
        </ul>
      </section>

      <section class="card">
        <header class="card-head">
          <div>
            <p class="card-kicker">COURSE / INFO</p>
            <h2>课程信息</h2>
          </div>
        </header>
        <div class="mini-defs">
          <div>
            <span>学期</span><b>{{ course?.semester || '—' }}</b>
          </div>
          <div>
            <span>课程编号</span><b>{{ course?.code || '—' }}</b>
          </div>
          <div>
            <span>学分</span><b>{{ course?.credits ?? '—' }}</b>
          </div>
          <div>
            <span>审批状态</span
            ><b>{{ approvalLabel(course?.approvalStatus || course?.status) }}</b>
          </div>
        </div>
        <button
          class="btn btn-ghost btn-block"
          @click="goTab(role === 'teacher' ? 'settings' : 'overview')"
        >
          {{ role === 'teacher' ? '课程设置' : '查看课程通知' }}
        </button>
      </section>

      <section class="card">
        <header class="card-head">
          <div>
            <p class="card-kicker">QUICK / 快捷入口</p>
            <h2>课程内容</h2>
          </div>
        </header>
        <div class="quick-links">
          <button @click="goTab('chapters')">章节与资源</button>
          <button @click="goTab('tasks')">作业</button>
          <button @click="goTab('exams')">考试</button>
          <button @click="goTab('analytics')">学情</button>
          <button @click="goTab('graph')">知识图谱</button>
          <button @click="goTab('ai')">AI 助手</button>
        </div>
      </section>
    </div>
    <p class="hint">{{ note }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import chapterApi from '@/api/chapter'
import { pickList, unwrap } from '@/utils/response'
import V2Stat from '../../../components/V2Stat.vue'
import V2Icon from '../../../components/V2Icon.vue'
import type { V2Metric, V2Role } from '../../../nav'
import { IA } from '../../../data/demoSource'
import { getCourses, type V2CourseCard } from '../../../data/api'
import { approvalLabel } from '../../../utils'

interface CourseLike {
  name?: string
  code?: string
  semester?: string
  credits?: number | null
  category?: string
  status?: string
  approvalStatus?: string
  teacher?: string
}

const props = defineProps<{
  courseId: number
  role: V2Role
  course: CourseLike | null
}>()

const router = useRouter()
const summary = ref<V2CourseCard | null>(null)
const chapterCount = ref(0)
const firstChapter = ref('')
const note = ref('示例数据：教学班与部分信息来自原型样本。')

const demoCourse = computed(() => {
  const courses = IA.courses as unknown as Array<{
    id: number
    progress: number
    classes: Array<{ name: string; students: number; source: string; progress: number }>
    pending: number
  }>
  return courses.find((item) => item.id === props.courseId) || courses[0]
})

const demoClasses = computed(() => demoCourse.value?.classes || [])
const progress = computed(() => Number(summary.value?.progress ?? demoCourse.value?.progress ?? 0))

const metrics = computed<V2Metric[]>(() => {
  if (props.role === 'teacher') {
    return [
      {
        label: '学生',
        value: summary.value?.students ?? demoClasses.value.reduce((n, c) => n + c.students, 0),
        unit: '人',
        note: '选课学生',
        icon: 'users',
      },
      {
        label: '章节',
        value: chapterCount.value || '—',
        unit: '个',
        note: '课程章节',
        icon: 'book',
      },
      {
        label: '待批阅',
        value: summary.value?.pending ?? demoCourse.value?.pending ?? 0,
        unit: '份',
        note: '需教师确认',
        icon: 'check',
        tone: 'warn',
      },
      {
        label: '平均进度',
        value: progress.value,
        unit: '%',
        note: '按选课进度平均',
        icon: 'chart',
        tone: 'ok',
      },
    ]
  }
  if (props.role === 'admin') {
    return [
      {
        label: '教学班',
        value: demoClasses.value.length,
        unit: '个',
        note: '待后端接入',
        icon: 'users',
      },
      {
        label: '学生',
        value: demoClasses.value.reduce((n, c) => n + c.students, 0),
        unit: '人',
        note: '原型样本',
        icon: 'users',
      },
      {
        label: '审批',
        value: approvalLabel(props.course?.approvalStatus),
        icon: 'check',
        tone: 'ok',
      },
      { label: '学分', value: Number(props.course?.credits ?? 3), unit: '分', icon: 'award' },
    ]
  }
  return [
    {
      label: '课程进度',
      value: progress.value,
      unit: '%',
      note: '来自选课记录',
      icon: 'chart',
      tone: 'ok',
    },
    {
      label: '学分',
      value: Number(props.course?.credits ?? 3),
      unit: '分',
      note: '课程学分',
      icon: 'award',
    },
    {
      label: '教学班',
      value: demoClasses.value.length,
      unit: '个',
      note: '待后端接入',
      icon: 'users',
    },
    { label: '待办', value: 3, unit: '项', note: '示例队列', icon: 'check', tone: 'warn' },
  ]
})

const queue = computed(() => {
  if (props.role === 'student') {
    return [
      { title: '网络层拥塞控制', meta: '计算机网络 · 今天 23:59', chip: '作业', tone: 'warn' },
      { title: '计算机网络 · 期中测验', meta: '10-31 09:00', chip: '考试', tone: 'danger' },
      { title: 'TCP 拥塞控制讨论', meta: '讨论区 · 2 条未读', chip: '讨论', tone: 'info' },
    ]
  }
  return [
    {
      title: '林知远 · 网络层拥塞控制',
      meta: '计科 2301 · AI 建议 86',
      chip: '待批',
      tone: 'warn',
    },
    {
      title: '王雨桐 · 网络层拥塞控制',
      meta: '计科 2301 · AI 建议 92',
      chip: '待批',
      tone: 'warn',
    },
    {
      title: '陈子默 · 网络层拥塞控制',
      meta: '计科 2301 · AI 建议 78',
      chip: '待批',
      tone: 'warn',
    },
  ]
})

const nextChapter = computed(() => (firstChapter.value ? { title: firstChapter.value } : undefined))
const nextChapterHint = computed(() =>
  props.role === 'student'
    ? '上次停在 5.3 拥塞控制 08:42，系统已保存播放位置与笔记。'
    : '课程级内容由所有教学班共享。',
)

function goTab(tab: string) {
  router.push(`/v2/${props.role}/courses/${props.courseId}/${tab}`)
}

onMounted(async () => {
  if (props.role !== 'teacher') return
  const summaries = await getCourses('teacher')
  summary.value = summaries.find((item) => item.id === props.courseId) || null
  try {
    const payload = unwrap(await chapterApi.getChapters(props.courseId))
    const list = pickList(payload) as Array<Record<string, unknown>>
    chapterCount.value = list.length
    firstChapter.value = String(list[0]?.title || '')
  } catch {
    chapterCount.value = 0
  }
  if (summary.value) {
    note.value = '接口数据：课程概览来自课程汇总与章节接口；教学班仍为示例。'
  }
})
</script>
