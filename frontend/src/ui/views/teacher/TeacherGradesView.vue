<template>
  <section class="module">
    <PageHead title="成绩管理">
      <template #actions>
        <button class="btn btn-ghost" :disabled="!selectedCourseId" @click="openPolicy">
          成绩方案
        </button>
        <button class="btn btn-ghost" :disabled="!filteredGradeList.length" @click="handleExport">
          导出成绩
        </button>
        <button class="btn btn-ghost" :disabled="loading" @click="refresh">刷新</button>
      </template>
    </PageHead>

    <div v-if="loadNotice" class="callout">
      <span>数据可用性</span>
      <p>{{ loadNotice }}</p>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="teacher" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>成绩总览</h2>
        </div>
        <div class="filter-row">
          <select
            v-model="selectedCourseId"
            class="input filter-course"
            aria-label="按课程筛选成绩"
            @change="loadGradeData"
          >
            <option :value="null">全部课程</option>
            <option v-for="course in courseList" :key="course.id" :value="course.id">
              {{ course.name || `课程 ${course.id}` }}
            </option>
          </select>
          <input
            v-model="searchText"
            class="input filter-search"
            type="search"
            placeholder="搜索学号/姓名/课程…"
          />
        </div>
      </header>

      <div v-if="filteredGradeList.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-140">学号</th>
              <th>姓名</th>
              <th v-if="!selectedCourseId" class="w-220">课程</th>
              <th class="w-96">作业均分</th>
              <th class="w-96">考试均分</th>
              <th class="w-96">总评</th>
              <th class="w-96">等级</th>
              <th class="w-96 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in filteredGradeList" :key="row.id">
              <td>{{ row.studentNo }}</td>
              <td>
                <b class="s-name">{{ row.studentName }}</b>
              </td>
              <td v-if="!selectedCourseId">{{ row.courseName }}</td>
              <td>{{ display(row.homeworkScore) }}</td>
              <td>{{ display(row.examScore) }}</td>
              <td>
                <b>{{ display(row.totalScore) }}</b>
              </td>
              <td>
                <span class="chip" :class="levelChip(row.gradeLevel)">{{
                  row.gradeLevel || '—'
                }}</span>
              </td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="viewStudentDetail(row)">详情</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else-if="loading" class="empty-state compact"><b>正在加载成绩…</b></div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="award" /></span>
        <b>{{ searchText.trim() ? '没有匹配的学生' : '暂无可显示的学生成绩' }}</b>
        <p>
          {{
            searchText.trim() ? '换个学号、姓名或课程关键词。' : '选择课程后重试，或等待成绩录入。'
          }}
        </p>
      </div>

      <footer class="card-foot">
        <span>共 {{ filteredGradeList.length }} 名学生</span>
      </footer>
    </section>

    <div class="grades-side">
      <section class="card">
        <header class="card-head">
          <div>
            <h2>总评分布</h2>
          </div>
        </header>
        <div v-if="!hasScores" class="empty-state compact"><b>暂无可统计的总评成绩</b></div>
        <ul v-else class="dist-list">
          <li v-for="bar in distribution" :key="bar.label">
            <span class="dist-label">{{ bar.label }}</span>
            <span class="dist-track"><i :style="{ width: `${bar.pct}%` }" /></span>
            <b class="dist-num">{{ bar.count }}</b>
          </li>
        </ul>
      </section>

      <section class="card">
        <header class="card-head">
          <div>
            <h2>待批阅任务</h2>
          </div>
        </header>
        <div v-if="pendingUnavailable" class="empty-state compact">
          <b>待批阅任务暂不可用</b>
          <p>暂无数据，成绩表不受影响。</p>
        </div>
        <div v-else-if="!pendingTasks.length" class="empty-state compact">
          <b>没有待批阅任务</b>
        </div>
        <ul v-else class="pending-list">
          <li v-for="task in pendingTasks" :key="task.id">
            <span class="p-name">{{ task.taskName }}</span>
            <span class="chip chip-warn">{{ display(task.pendingCount) }} 份</span>
            <button class="text-btn" @click="goToGrading(task)">进入批阅</button>
          </li>
        </ul>
      </section>
    </div>

    <Modal
      v-model:open="policyOpen"
      :title="`成绩方案 · ${policyCourseName}`"
      width="680px"
      :close-on-backdrop="false"
    >
      <div v-if="policyLoading" class="empty-state compact"><b>正在读取成绩方案…</b></div>
      <div v-else-if="policyError" class="callout">
        <span>成绩方案</span>
        <p>{{ policyError }}</p>
        <button class="btn btn-ghost btn-sm" @click="loadPolicy">重试</button>
      </div>
      <template v-else>
        <table v-if="policyComponents.length" class="policy-table">
          <thead>
            <tr>
              <th>构成项</th>
              <th class="w-140">权重 %</th>
              <th class="w-64"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(component, index) in policyComponents" :key="component.key">
              <td>
                <input
                  v-model="component.name"
                  class="input"
                  :data-testid="`policy-name-${index}`"
                  maxlength="40"
                  placeholder="如 作业"
                />
              </td>
              <td>
                <input
                  v-model.number="component.weight"
                  class="input"
                  :data-testid="`policy-weight-${index}`"
                  type="number"
                  min="0"
                  max="100"
                  step="1"
                />
              </td>
              <td>
                <button class="text-btn danger" @click="removePolicyComponent(index)">移除</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty-state compact"><b>尚无成绩构成项</b></div>
        <div class="policy-foot">
          <button class="btn btn-ghost btn-sm" @click="addPolicyComponent">添加构成项</button>
          <span class="policy-sum" :class="{ danger: policyWeightSum !== 100 }">
            合计 {{ policyWeightSum }}%
          </span>
        </div>
      </template>
      <template #footer>
        <button class="btn btn-ghost" :disabled="policyBusy" @click="policyOpen = false">
          取消
        </button>
        <button
          class="btn btn-primary"
          :disabled="policyBusy || policyLoading || Boolean(policyError)"
          @click="savePolicy"
        >
          {{ policyBusy ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <Modal
      v-model:open="detailDialogVisible"
      :title="currentStudent ? `成绩构成 · ${currentStudent.studentName}` : '成绩构成'"
      width="680px"
    >
      <div v-if="currentStudent" class="detail-body">
        <dl class="detail-meta">
          <div>
            <dt>学号</dt>
            <dd>{{ currentStudent.studentNo }}</dd>
          </div>
          <div>
            <dt>课程</dt>
            <dd>{{ currentStudent.courseName }}</dd>
          </div>
          <div>
            <dt>总评</dt>
            <dd>{{ display(currentStudent.totalScore) }}</dd>
          </div>
          <div>
            <dt>等级</dt>
            <dd>{{ currentStudent.gradeLevel || '—' }}</dd>
          </div>
        </dl>
        <table v-if="currentStudent.details.length">
          <thead>
            <tr>
              <th>构成项</th>
              <th class="w-96">得分</th>
              <th class="w-96">权重</th>
              <th class="w-96">贡献</th>
              <th class="w-96">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="detail in currentStudent.details" :key="detail.taskName">
              <td>{{ detail.taskName }}</td>
              <td>{{ display(detail.score) }}</td>
              <td>{{ display(detail.weight) }}</td>
              <td>{{ display(detail.contribution) }}</td>
              <td>
                <span class="chip" :class="detail.graded ? 'chip-ok' : 'chip-warn'">
                  {{ detail.graded ? '已评分' : '待评分' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty-state compact">
          <b>该课程尚无成绩构成项</b>
          <p>可能尚未配置成绩方案。</p>
        </div>
        <div v-if="currentStudent.warnings.length" class="callout">
          <span>WARNINGS</span>
          <p>{{ currentStudent.warnings.join('；') }}</p>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="detailDialogVisible = false">关闭</button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHead from '../../components/PageHead.vue'
import Modal from '../../components/Modal.vue'
import Stat from '../../components/Stat.vue'
import Icon from '../../components/Icon.vue'
import { useUi } from '../../composables/ui'
import { useTeacherGrades, type TeacherGradesTone } from '@/composables/useTeacherGrades'
import gradeApi from '@/api/grade'
import { unwrap } from '@/utils/response'
import type { Metric } from '../../nav'

const router = useRouter()
const { toast } = useUi()

const tones: Record<TeacherGradesTone, 'info' | 'ok' | 'warn' | 'danger'> = {
  info: 'info',
  ok: 'ok',
  warn: 'warn',
  error: 'danger',
}

const {
  loading,
  searchText,
  selectedCourseId,
  courseList,
  overview,
  gradeList,
  pendingTasks,
  pendingUnavailable,
  loadNotice,
  detailDialogVisible,
  currentStudent,
  filteredGradeList,
  refresh,
  loadGradeData,
  handleExport,
  viewStudentDetail,
  goToGrading,
} = useTeacherGrades({
  notify: (message, tone) => toast(message, tones[tone]),
  openGrading: (task) => {
    router.push(`/teacher/courses/${task.courseId}/grading?taskId=${String(task.id)}`)
  },
})

interface PolicyComponent {
  key: string
  name: string
  weight: number
  source?: string
}

interface PolicyComponentInput {
  key?: string
  name?: string
  weight?: number | string
  source?: string
}

interface GradePolicyResponse {
  name?: string
  components?: PolicyComponentInput[] | null
}

const policyOpen = ref(false)
const policyLoading = ref(false)
const policyBusy = ref(false)
const policyError = ref('')
const policyComponents = ref<PolicyComponent[]>([])
let policyKeySeq = 0

const policyCourseName = computed(() => {
  const course = courseList.value.find(
    (item) => String(item.id) === String(selectedCourseId.value || ''),
  )
  return course?.name || '当前课程'
})

const policyWeightSum = computed(() =>
  policyComponents.value.reduce((sum, component) => {
    const weight = Number(component.weight)
    return sum + (Number.isFinite(weight) ? weight : 0)
  }, 0),
)

function defaultPolicyComponents(): PolicyComponent[] {
  return [
    { key: 'SIGN_IN', name: '出勤', weight: 10, source: 'sign_in' },
    { key: 'HOMEWORK', name: '作业', weight: 30, source: 'submission' },
    { key: 'EXAM', name: '考试', weight: 60, source: 'exam' },
  ]
}

function policyErrorText(error: unknown): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || '成绩方案读取失败，请稍后重试'
}

function normalizePolicyComponent(row: PolicyComponentInput, index: number): PolicyComponent {
  return {
    key: String(row.key || `ITEM_${index + 1}`),
    name: String(row.name || row.key || `构成项 ${index + 1}`),
    weight: Number(row.weight ?? 0),
    source: row.source,
  }
}

async function loadPolicy() {
  const courseId = selectedCourseId.value
  if (!courseId) return
  policyLoading.value = true
  policyError.value = ''
  try {
    const data = unwrap<GradePolicyResponse | null>(await gradeApi.getGradePolicy(courseId))
    const components = Array.isArray(data?.components) ? data.components : []
    policyComponents.value =
      components.length > 0
        ? components.map((component, index) => normalizePolicyComponent(component, index))
        : defaultPolicyComponents()
  } catch (error) {
    policyComponents.value = []
    policyError.value = policyErrorText(error)
  } finally {
    policyLoading.value = false
  }
}

function openPolicy() {
  policyOpen.value = true
  void loadPolicy()
}

function addPolicyComponent() {
  policyKeySeq += 1
  policyComponents.value.push({
    key: `CUSTOM_${policyKeySeq}`,
    name: '',
    weight: 0,
    source: 'manual',
  })
}

function removePolicyComponent(index: number) {
  policyComponents.value.splice(index, 1)
}

async function savePolicy() {
  const courseId = selectedCourseId.value
  if (!courseId || policyBusy.value) return
  if (policyComponents.value.length === 0) {
    toast('请至少添加一个成绩构成项', 'warn')
    return
  }
  const components = policyComponents.value.map((component, index) => ({
    key: component.key || `ITEM_${index + 1}`,
    name: component.name.trim(),
    weight: Number(component.weight),
    source: component.source || 'manual',
  }))
  if (components.some((component) => !component.name)) {
    toast('构成项名称不能为空', 'warn')
    return
  }
  if (
    components.some(
      (component) =>
        !Number.isFinite(component.weight) || component.weight < 0 || component.weight > 100,
    )
  ) {
    toast('权重必须是 0–100 的数字', 'warn')
    return
  }
  if (Math.abs(policyWeightSum.value - 100) > 0.01) {
    toast('权重合计须为 100%', 'warn')
    return
  }
  policyBusy.value = true
  policyError.value = ''
  try {
    await gradeApi.saveGradePolicy({
      courseId,
      name: '默认方案',
      components,
    })
    toast('成绩方案已保存', 'ok')
    policyOpen.value = false
    await loadGradeData()
  } catch (error) {
    policyError.value = policyErrorText(error)
  } finally {
    policyBusy.value = false
  }
}

const metrics = computed<Metric[]>(() => [
  {
    label: '平均分',
    value: overview.value.avgScore ?? '—',
    unit: '分',
    note: '当前范围',
    icon: 'chart',
  },
  {
    label: '最高分',
    value: overview.value.maxScore ?? '—',
    unit: '分',
    note: '当前范围',
    icon: 'award',
    tone: 'ok',
  },
  {
    label: '最低分',
    value: overview.value.minScore ?? '—',
    unit: '分',
    note: '当前范围',
    icon: 'alert',
  },
  {
    label: '待批阅任务',
    value: overview.value.pendingCount ?? '—',
    unit: '项',
    note: '当前范围',
    icon: 'clock',
    tone: 'warn',
  },
])

const BUCKETS = [
  { label: '90-100', min: 90 },
  { label: '80-89', min: 80 },
  { label: '70-79', min: 70 },
  { label: '60-69', min: 60 },
  { label: '0-59', min: null },
]

const scores = computed(() =>
  gradeList.value.map((row) => row.totalScore).filter((score): score is number => score !== null),
)
const hasScores = computed(() => scores.value.length > 0)
const distribution = computed(() => {
  const counts = BUCKETS.map((bucket) => ({
    label: bucket.label,
    count:
      bucket.min === null
        ? scores.value.filter((score) => score < 60).length
        : scores.value.filter((score) => score >= bucket.min && score < bucket.min + 10).length,
  }))
  const max = Math.max(1, ...counts.map((item) => item.count))
  return counts.map((item) => ({ ...item, pct: Math.round((item.count / max) * 100) }))
})

function display(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

function levelChip(level: string): string {
  const map: Record<string, string> = {
    A: 'chip-ok',
    B: 'chip-accent',
    C: 'chip-warn',
    D: 'chip-danger',
    F: 'chip-mute',
  }
  return map[level] || 'chip-mute'
}
</script>

<style scoped>
.filter-course {
  height: 34px;
  width: auto;
  min-width: 150px;
  padding: 0 8px;
  font-size: 11px;
}
.filter-search {
  flex: 1;
  min-width: 180px;
  height: 34px;
}
.s-name {
  font-weight: 550;
}
.grades-side {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 14px;
}
.dist-list,
.pending-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.dist-list li {
  display: grid;
  grid-template-columns: 62px 1fr 34px;
  align-items: center;
  gap: 10px;
}
.dist-label {
  font-family: var(--font-mono);
  font-size: 9.5px;
  color: var(--ink-3);
}
.dist-track {
  height: 8px;
  border-radius: var(--r-xs);
  background: var(--surface-3);
  overflow: hidden;
}
.dist-track i {
  display: block;
  height: 100%;
  border-radius: var(--r-xs);
  background: var(--accent);
}
.dist-num {
  text-align: right;
  font-family: var(--font-display);
  font-size: 14px;
}
.pending-list li {
  display: flex;
  align-items: center;
  gap: 10px;
}
.p-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}
.policy-table {
  width: 100%;
}
.policy-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}
.policy-sum {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--ink-2);
}
.policy-sum.danger {
  color: var(--danger);
}
.detail-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.detail-meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}
.detail-meta dt {
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.08em;
  color: var(--ink-3);
}
.detail-meta dd {
  margin: 3px 0 0;
  font-size: 13px;
  font-weight: 600;
}
@media (max-width: 900px) {
  .grades-side {
    grid-template-columns: 1fr;
  }
  .detail-meta {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
