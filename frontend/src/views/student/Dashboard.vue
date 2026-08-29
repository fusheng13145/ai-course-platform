<template>
  <div>
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

    <!-- 学情可视化（D7）：学习时长趋势 / 课程薄弱度雷达 / 学习活跃热力图 -->
    <section v-if="vizReady" class="viz-grid animate-in d2">
      <div class="viz-card viz-wide">
        <div class="viz-head">
          <span class="viz-title">近 14 天学习时长</span>
          <span class="viz-sub">单位：分钟 · learning_records 聚合</span>
        </div>
        <VChart :option="durationOption" autoresize style="height: 220px" />
      </div>
      <div class="viz-card">
        <div class="viz-head">
          <span class="viz-title">课程薄弱度</span>
          <span class="viz-sub">未掌握错题占比（%）</span>
        </div>
        <VChart v-if="radarDimensions.length" :option="radarOption" autoresize style="height: 220px" />
        <el-empty v-else description="暂无错题数据" :image-size="60" />
      </div>
      <div class="viz-card viz-wide">
        <div class="viz-head">
          <span class="viz-title">学习活跃热力图</span>
          <span class="viz-sub">课程 × 近 7 天 · 学习时长（分钟）</span>
        </div>
        <VChart v-if="heatRows.length" :option="heatOption" autoresize style="height: 180px" />
        <el-empty v-else description="近 7 天暂无学习记录" :image-size="60" />
      </div>
    </section>

    <section class="l1-card animate-in d2">
      <div class="l1-head">
        <span class="l1-ico"
          ><svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /></svg
        ></span>
        <div>
          <div class="l1-title">课程大厅</div>
          <div class="l1-sub">我加入的课程 · 点击卡片进入学习</div>
        </div>
        <div class="l1-extra">
          <button class="btn btn-primary" @click="showJoin = true">
            <svg
              viewBox="0 0 24 24"
              width="14"
              height="14"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 5v14M5 12h14" />
            </svg>
            加入课程
          </button>
        </div>
      </div>
      <div class="l1-body">
        <div v-if="loading" class="empty-state"><div class="e-title">加载中…</div></div>
        <div v-else-if="courses.length === 0" class="empty-state">
          <div class="e-title">还没有课程</div>
          <div class="e-desc">点击右上角「加入课程」输入课程 ID 加入</div>
        </div>
        <div v-else class="course-grid">
          <div
            v-for="c in courses"
            :key="c.courseId"
            class="course-card"
            @click="$router.push(`/student/courses/${c.courseId}/overview`)"
          >
            <div class="course-cover" :style="{ background: coverOf(c.cover) }">
              <span class="cc-code mono">#{{ c.courseId }}</span>
              <span class="cc-name">{{ c.name }}</span>
            </div>
            <div class="course-meta">
              <div class="cm-row">
                <span class="avatar sm" :style="avatarStyle(c.name)">{{ c.name.slice(-2) }}</span>
                {{ c.name }}
              </div>
              <div>
                <div class="flex between mb-8">
                  <span class="cf-label">学习进度</span
                  ><span class="cf-num">{{ c.progress }}%</span>
                </div>
                <span class="mini-progress"><i :style="{ width: c.progress + '%' }"></i></span>
              </div>
              <div class="course-foot">
                <span class="cf-label flex gap-4"
                  ><svg
                    viewBox="0 0 24 24"
                    width="12"
                    height="12"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2" />
                  </svg>
                  {{ c.nextTask }}</span
                >
                <span class="op-link">进入学习</span>
              </div>
            </div>
          </div>
          <div class="course-card join-card" @click="showJoin = true">
            <div class="join-card-inner">
              <div class="join-card-icon">
                <svg
                  viewBox="0 0 24 24"
                  width="22"
                  height="22"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.9"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M12 5v14M5 12h14" />
                </svg>
              </div>
              <div class="join-card-title">加入新课程</div>
              <div class="small">凭选课码加入</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 加入课程弹窗 -->
    <Modal v-if="showJoin" narrow @close="showJoin = false">
      <template #title>加入新课程</template>
      <template #sub>输入课程 ID（可在教师端课程列表查看）</template>
      <div class="form-grid">
        <div class="field span-2">
          <label>课程 ID<span class="req">*</span></label>
          <input v-model="joinCourseId" class="input mono join-input" placeholder="如：1" />
        </div>
      </div>
      <template #footer>
        <button class="btn btn-plain" @click="showJoin = false">取消</button>
        <button class="btn btn-primary" :disabled="joining" @click="doJoin()">
          {{ joining ? '加入中…' : '加入课程' }}
        </button>
      </template>
    </Modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import CountUp from '@/components/CountUp.vue'
import Modal from '@/components/ui/Modal.vue'
import { toast } from '@/composables/useToast'
import studentApi from '@/api/student'

// 渐变对齐 AppShell avatarStyle 6 色渐变风格（令牌色系：赭红/蓝/绿/琥珀/紫/青）
const GRADIENTS = [
  'linear-gradient(135deg,#c13b30,#8f2a21)',
  'linear-gradient(135deg,#2563a8,#1a4a80)',
  'linear-gradient(135deg,#2e7d4f,#1f5c39)',
  'linear-gradient(135deg,#b57908,#8a5d06)',
  'linear-gradient(135deg,#6d4fa1,#4f3877)',
  'linear-gradient(135deg,#0f766e,#0a544f)',
]
const AVATARS = GRADIENTS

const courses = ref([])
const todos = ref({ assignments: [], exams: [], signIns: [] })
const summary = ref({})
const loading = ref(true)
const showJoin = ref(false)
const joinCourseId = ref('')
const joining = ref(false)

const avgProgress = computed(() => {
  if (!courses.value.length) return 0
  return Math.round(courses.value.reduce((s, c) => s + c.progress, 0) / courses.value.length)
})
const pendingCount = computed(() => todos.value.assignments.length + todos.value.exams.length)

function hashOf(str) {
  return [...(str || '')].reduce((s, c) => s + c.charCodeAt(0), 0)
}
function coverOf(cover) {
  // 后端 coverImage 可能是颜色/渐变/URL；无法识别时按名称哈希取渐变，保证视觉稳定
  if (
    typeof cover === 'string' &&
    (cover.startsWith('#') || cover.startsWith('linear-gradient') || cover.startsWith('http'))
  )
    return cover
  return GRADIENTS[hashOf(cover) % GRADIENTS.length]
}
function avatarStyle(name) {
  return { background: AVATARS[hashOf(name) % AVATARS.length] }
}

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

async function doJoin() {
  const courseId = Number(joinCourseId.value)
  if (!courseId) {
    toast.show('请输入有效的课程 ID', 'error')
    return
  }
  joining.value = true
  try {
    await studentApi.enrollCourse(courseId)
    toast.show('已加入课程', 'ok')
    showJoin.value = false
    joinCourseId.value = ''
    await load()
  } catch (e) {
    toast.show(e?.response?.data?.message || '加入失败，请确认课程 ID 是否正确', 'error')
  } finally {
    joining.value = false
  }
}

onMounted(load)
onMounted(loadVisualization)

// ==================== 学情可视化（D7） ====================
const vizData = ref({ durationTrend: [], radarData: [], heatmapData: [] })
const vizReady = ref(false)

async function loadVisualization() {
  try {
    const res = await studentApi.getVisualizationData()
    const d = res?.data?.data || res?.data || {}
    vizData.value = {
      durationTrend: Array.isArray(d.durationTrend) ? d.durationTrend : [],
      radarData: Array.isArray(d.radarData) ? d.radarData : [],
      heatmapData: Array.isArray(d.heatmapData) ? d.heatmapData : [],
    }
  } catch {
    // 可视化为增强项：加载失败静默降级，不影响驾驶舱主内容
    vizData.value = { durationTrend: [], radarData: [], heatmapData: [] }
  } finally {
    vizReady.value = true
  }
}

const radarDimensions = computed(() => vizData.value.radarData.map((r) => r.dimension || ''))
const heatRows = computed(() => vizData.value.heatmapData)

// 近 14 天学习时长折线（秒 → 分钟）
const durationOption = computed(() => {
  const trend = vizData.value.durationTrend
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 42, right: 16, top: 24, bottom: 26 },
    xAxis: {
      type: 'category',
      data: trend.map((t) => t.date.slice(5)),
      axisLabel: { fontSize: 11, color: 'var(--ac-text-tertiary, #98a2b3)' },
    },
    yAxis: {
      type: 'value',
      name: '分钟',
      nameTextStyle: { fontSize: 11 },
      axisLabel: { fontSize: 11, color: 'var(--ac-text-tertiary, #98a2b3)' },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trend.map((t) => Math.round((Number(t.seconds) || 0) / 60)),
        lineStyle: { color: 'var(--accent, #2563a8)', width: 2.5 },
        itemStyle: { color: 'var(--accent, #2563a8)' },
        areaStyle: { opacity: 0.1 },
      },
    ],
  }
})

// 课程薄弱度雷达（未掌握错题率，百分比）
const radarOption = computed(() => ({
  tooltip: { trigger: 'item' },
  radar: {
    indicator: radarDimensions.value.map((d) => ({ name: d, max: 100 })),
    radius: '62%',
    splitNumber: 4,
    axisName: { fontSize: 11, color: 'var(--ac-text-secondary, #4b5565)' },
  },
  series: [
    {
      type: 'radar',
      data: [
        {
          value: vizData.value.radarData.map((r) => Number(r.weakRate) || 0),
          name: '薄弱度',
          areaStyle: { opacity: 0.25 },
          lineStyle: { color: 'var(--ac-danger, #c13b30)' },
          itemStyle: { color: 'var(--ac-danger, #c13b30)' },
        },
      ],
    },
  ],
}))

// 课程 × 近 7 天学习活跃热力图
const heatOption = computed(() => {
  const rows = heatRows.value
  const dates = [...new Set(rows.map((r) => r.date))].sort()
  const courses = [...new Map(rows.map((r) => [r.courseId, r.courseName || `课程 #${r.courseId}`])).values()]
  const idxDate = new Map(dates.map((d, i) => [d, i]))
  const idxCourse = new Map(courses.map((c, i) => [c, i]))
  const data = rows.map((r) => [
    idxCourse.get(r.courseName || `课程 #${r.courseId}`),
    idxDate.get(r.date),
    Math.round((Number(r.seconds) || 0) / 60),
  ])
  return {
    tooltip: {
      formatter: (p) => `${courses[p.value[0]]}<br/>${dates[p.value[1]]}<br/>${p.value[2]} 分钟`,
    },
    grid: { left: 80, right: 16, top: 16, bottom: 30 },
    xAxis: {
      type: 'category',
      data: dates.map((d) => d.slice(5)),
      axisLabel: { fontSize: 11, color: 'var(--ac-text-tertiary, #98a2b3)' },
      splitArea: { show: true },
    },
    yAxis: {
      type: 'category',
      data: courses,
      axisLabel: { fontSize: 11, color: 'var(--ac-text-tertiary, #98a2b3)' },
      splitArea: { show: true },
    },
    visualMap: {
      min: 0,
      max: 60,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: { color: ['#eef4fb', '#c9dcf2', '#8fb6e0', '#3d78c4', '#1d4e8f'] },
      textStyle: { fontSize: 10, color: 'var(--ac-text-tertiary, #98a2b3)' },
    },
    series: [{ type: 'heatmap', data, label: { show: false } }],
  }
})
</script>

<style scoped>
.join-card {
  align-items: center;
  justify-content: center;
  border: 1.5px dashed var(--line-strong);
  background: var(--surface-2);
  box-shadow: none;
  min-height: 220px;
}
.join-card-inner {
  text-align: center;
  color: var(--ink-3);
}
.join-card-icon {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--surface);
  display: grid;
  place-items: center;
  margin: 0 auto 10px;
  box-shadow: var(--sh-l2);
}
.join-card-title {
  font-weight: 600;
  color: var(--ink-2);
}
.join-input {
  letter-spacing: 2px;
  text-align: center;
  font-size: 16px;
}

/* ============ 学情可视化（D7） ============ */
.viz-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 18px;
}
.viz-card {
  background: var(--ac-bg-elevated, #fff);
  border: 1px solid var(--ac-border, rgba(0, 0, 0, 0.08));
  border-radius: var(--ac-radius-xl, 14px);
  padding: 14px 14px 10px;
}
.viz-wide {
  grid-column: span 1;
}
@media (min-width: 1100px) {
  .viz-wide {
    grid-column: span 2;
  }
}
.viz-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}
.viz-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--ac-text-primary, #1f2430);
}
.viz-sub {
  font-size: 11px;
  color: var(--ac-text-tertiary, #98a2b3);
}
/* 移动端：图表区单列（D10 针对性适配） */
@media (max-width: 768px) {
  .viz-grid {
    grid-template-columns: 1fr;
  }
  .viz-wide {
    grid-column: span 1;
  }
}
</style>
