<template>
  <div class="fade-in">
    <PageHeader title="学习首页" subtitle="课程进度、待办任务与未读通知概览" icon="dashboard" />
    <div class="stat-grid animate-in d1">
      <StatCard label="在修课程" icon="book"
        ><template #value
          ><CountUp :value="Number(summary.courseCount ?? courses.length)" /></template
      ></StatCard>
      <StatCard label="平均进度" icon="chart" tone="var(--info-soft)" tone-color="var(--info)"
        ><template #value><CountUp :value="Number(avgProgress) || 0" suffix="%" /></template
      ></StatCard>
      <StatCard
        label="待办任务"
        icon="clock"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
        :foot="`${todos.assignments.length} 作业 · ${todos.exams.length} 考试`"
        ><template #value><CountUp :value="Number(pendingCount)" /></template
      ></StatCard>
      <StatCard label="未读通知" icon="award" tone="var(--ok-soft)" tone-color="var(--ok)"
        ><template #value><CountUp :value="Number(summary.unreadCount ?? 0)" /></template
      ></StatCard>
    </div>

    <!-- 学情可视化（D7）子组件：时长趋势 / 薄弱度雷达 / 活跃热力图（自包含数据加载） -->
    <SdVizSection />

    <!-- 课程大厅子组件：课程卡片网格 + 加入课程弹窗 -->
    <SdCourseGrid :courses="courses" :loading="loading" @joined="load" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import CountUp from '@/components/CountUp.vue'
import { toast } from '@/composables/useToast'
import studentApi from '@/api/student'
import SdVizSection from './components/SdVizSection.vue'
import SdCourseGrid from './components/SdCourseGrid.vue'

/**
 * 学生驾驶舱首页（拆分子组件后页面壳）。
 * <p>职责收敛：统计卡数据组装 + 课程/待办数据加载；可视化（SdVizSection）
 * 与课程大厅交互（SdCourseGrid）分别下沉。</p>
 */
const courses = ref([])
const todos = ref({ assignments: [], exams: [], signIns: [] })
const summary = ref({})
const loading = ref(true)

const avgProgress = computed(() => {
  if (!courses.value.length) return 0
  return Math.round(courses.value.reduce((s, c) => s + c.progress, 0) / courses.value.length)
})
const pendingCount = computed(() => todos.value.assignments.length + todos.value.exams.length)

function nextTaskText(courseId) {
  const todo = todos.value.assignments.find((t) => Number(t.courseId) === Number(courseId))
  if (todo)
    return todo.dueDate
      ? `作业「${todo.title}」 ${String(todo.dueDate).slice(0, 10)} 截止`
      : `作业「${todo.title}」待提交`
  const exam = todos.value.exams.find((e) => Number(e.courseId) === Number(courseId))
  if (exam)
    return exam.startTime
      ? `考试「${exam.title}」 ${String(exam.startTime).slice(0, 10)} 开考`
      : `考试「${exam.title}」已发布`
  return '暂无待办'
}

async function load() {
  loading.value = true
  try {
    const res = await studentApi.getStudentDashboard()
    const d = res?.data?.data || res?.data || {}
    courses.value = (d.courses || []).map((c) => ({
      courseId: c.courseId,
      name: c.courseName || `课程 #${c.courseId}`,
      cover: c.cover,
      progress: Math.round(c.progress || 0),
    }))
    courses.value.forEach((c) => {
      c.nextTask = nextTaskText(c.courseId)
    })
    todos.value = d.todos || { assignments: [], exams: [], signIns: [] }
    summary.value = d.summary || {}
  } catch (e) {
    toast.show('课程数据加载失败：' + (e?.message || '网络错误'), 'error')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
/* 入场动画 */
.fade-in {
  animation: fadeIn 0.5s ease-in;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
