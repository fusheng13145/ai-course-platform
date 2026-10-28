<template>
  <section class="proctor">
    <div v-if="loadError" class="callout">
      <span>ERROR / 加载失败</span>
      <p>{{ loadError }}</p>
    </div>

    <div class="stat-grid kpi-grid">
      <V2Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="teacher" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">PROCTOR / SESSIONS</p>
          <h2>
            进行中考试会话 <span class="chip chip-mute">{{ filteredSessions.length }}</span>
          </h2>
        </div>
        <label class="course-only">
          <input v-model="onlyThisCourse" type="checkbox" :disabled="!scopedCourseId" />
          <span>仅本课程</span>
        </label>
      </header>

      <p v-if="!scopedCourseId" class="modal-hint">
        未关联到具体课程，已列出全部进行中的考试会话。
      </p>

      <div v-if="filteredSessions.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>试卷</th>
              <th class="w-140">学生</th>
              <th class="w-220">开始时间</th>
              <th class="w-220">结束时间</th>
              <th class="w-96">状态</th>
              <th class="w-140">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in filteredSessions"
              :key="row.id"
              :class="{ 'is-active': row.id === selected?.id }"
            >
              <td>
                <b class="p-text">{{ paperTitle(row) }}</b>
              </td>
              <td>{{ row.studentName || row.student || '（未指定）' }}</td>
              <td>{{ fmt(row.startTime) || '—' }}</td>
              <td>{{ fmt(row.endTime) || '—' }}</td>
              <td>
                <span class="chip" :class="sessionChip(row.status)">{{
                  sessionText(row.status)
                }}</span>
              </td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="selectSession(row)">查看风险</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载考试会话…</b></div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><V2Icon name="shield" /></span>
        <b>{{ sessionsError ? '会话列表未能加载' : '暂无进行中的考试' }}</b>
        <p>{{ sessionsError ? '请查看上方提示后重试。' : '考试开始后，会话会自动出现在这里。' }}</p>
      </div>

      <footer class="card-foot">
        <span>共 {{ filteredSessions.length }} 个会话</span>
        <button class="btn btn-sm" :disabled="loading" @click="load">刷新</button>
      </footer>
    </section>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">PROCTOR / RISK</p>
          <h2>
            风险汇总 <span class="chip chip-warn">{{ risk.list.length }}</span>
          </h2>
        </div>
        <span class="head-hint">
          {{ selected ? `当前会话：${paperTitle(selected)}` : '请选择上方一个考试会话' }}
        </span>
      </header>

      <div v-if="selected && risk.list.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-140">学生</th>
              <th class="w-140">风险类型</th>
              <th>说明</th>
              <th class="w-220">时间</th>
              <th class="w-140">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(row, index) in risk.list"
              :key="index"
              :class="{ 'is-active': row === selectedRisk }"
            >
              <td>{{ pick(row, ['studentName', 'student', 'userName']) || '—' }}</td>
              <td>
                <span class="chip chip-danger">
                  {{ pick(row, ['riskType', 'type', 'riskTypeName']) || '—' }}
                </span>
              </td>
              <td>{{ pick(row, ['description', 'detail', 'reason', 'message']) || '—' }}</td>
              <td>{{ fmt(pick(row, ['time', 'createTime', 'happenTime'])) || '—' }}</td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="selectRisk(row)">查看事件</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="selected && risk.loading" class="empty-state compact">
        <b>正在加载风险记录…</b>
      </div>
      <div v-else class="empty-state compact">
        <b>{{
          selected
            ? riskError
              ? '风险记录未能加载'
              : '该会话暂无风险记录'
            : '请选择考试会话以查看风险'
        }}</b>
        <p v-if="selected && riskError">请查看上方提示后重试。</p>
      </div>
    </section>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">PROCTOR / EVENTS</p>
          <h2>
            监考事件 <span class="chip chip-mute">{{ events.list.length }}</span>
          </h2>
        </div>
        <span class="head-hint">
          {{
            selectedRisk
              ? `风险：${pick(selectedRisk, ['riskType', 'type']) || '详情'}`
              : '请在上方风险列表中选择一条'
          }}
        </span>
      </header>

      <div v-if="selectedRisk && events.list.length" class="table-wrap events-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-140">事件类型</th>
              <th>说明</th>
              <th class="w-220">时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in events.list" :key="index">
              <td>
                <span class="chip" :class="eventChip(pick(row, ['eventType', 'type']))">
                  {{ pick(row, ['eventType', 'type']) || '—' }}
                </span>
              </td>
              <td>{{ pick(row, ['description', 'detail', 'message', 'content']) || '—' }}</td>
              <td>
                {{ fmt(pick(row, ['time', 'createTime', 'happenTime', 'eventTime'])) || '—' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="selectedRisk && events.loading" class="empty-state compact">
        <b>正在加载监考事件…</b>
      </div>
      <div v-else class="empty-state compact">
        <b>{{
          selectedRisk
            ? eventsError
              ? '监考事件未能加载'
              : '该风险暂无监考事件'
            : '请选择风险记录以查看监考事件'
        }}</b>
        <p v-if="selectedRisk && eventsError">请查看上方提示后重试。</p>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import V2Stat from '../../../components/V2Stat.vue'
import V2Icon from '../../../components/V2Icon.vue'
import examApi from '@/api/exam'
import proctorApi from '@/api/proctor'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'
import type { V2Metric } from '../../../nav'

interface SessionRow {
  id: number | string
  examPaperId?: number | string
  courseId?: number | string
  studentName?: string
  student?: string
  startTime?: string
  endTime?: string
  status?: string
  [key: string]: unknown
}

const props = defineProps<{ courseId?: number | string }>()

const loading = ref(false)
const sessions = ref<SessionRow[]>([])
const sessionsError = ref('')
const riskError = ref('')
const eventsError = ref('')
const paperMap = ref<Record<string, string>>({})
const paperCourse = ref<Record<string, number>>({})
const onlyThisCourse = ref(false)
const selected = ref<SessionRow | null>(null)
const selectedRisk = ref<Record<string, unknown> | null>(null)
const risk = reactive({ loading: false, list: [] as Record<string, unknown>[] })
const events = reactive({ loading: false, list: [] as Record<string, unknown>[] })

const scopedCourseId = computed(() => Number(props.courseId) || 0)
const loadError = computed(() => sessionsError.value || riskError.value || eventsError.value)
// /exam-sessions/active 不带 courseId（旧页因此「仅本课程」恒为空），用已加载的试卷回填
function rowCourseId(row: SessionRow): number {
  const direct = Number(row.courseId)
  if (direct) return direct
  return Number(paperCourse.value[String(row.examPaperId)]) || 0
}
const filteredSessions = computed(() => {
  if (!onlyThisCourse.value || !scopedCourseId.value) return sessions.value
  return sessions.value.filter((row) => rowCourseId(row) === scopedCourseId.value)
})

const metrics = computed<V2Metric[]>(() => [
  {
    label: '进行中考试',
    value: sessions.value.length,
    unit: '场',
    note: onlyThisCourse.value ? '仅本课程' : '全部课程',
    icon: 'play',
  },
  {
    label: '风险事件',
    value: risk.list.length,
    unit: '条',
    note: '当前会话',
    icon: 'alert',
    tone: 'warn',
  },
  {
    label: '监考事件',
    value: events.list.length,
    unit: '条',
    note: '当前风险',
    icon: 'file',
    tone: 'ok',
  },
])

function pick(obj: Record<string, unknown> | null | undefined, keys: string[]): string {
  if (!obj) return ''
  for (const key of keys) {
    const value = obj[key]
    if (value !== undefined && value !== null && value !== '') return String(value)
  }
  return ''
}

function fmt(value: unknown): string {
  if (!value) return ''
  const date = new Date(String(value))
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`
}

function rowsOf(body: unknown): Record<string, unknown>[] {
  if (Array.isArray(body)) return body as Record<string, unknown>[]
  const data = body as { records?: unknown[]; list?: unknown[] } | null
  return (data?.records || data?.list || []) as Record<string, unknown>[]
}

function sessionText(status?: string): string {
  const map: Record<string, string> = {
    IN_PROGRESS: '进行中',
    ONGOING: '进行中',
    ENDED: '已结束',
    FINISHED: '已结束',
    PENDING: '待开始',
  }
  return map[String(status)] || status || '未知'
}

function sessionChip(status?: string): string {
  if (status === 'IN_PROGRESS' || status === 'ONGOING') return 'chip-warn'
  if (status === 'ENDED' || status === 'FINISHED') return 'chip-mute'
  return 'chip-accent'
}

function eventChip(type: string): string {
  if (['CHEAT', 'COPY', 'LEAVE', 'TAB_SWITCH', 'FACE_MISS'].includes(type)) return 'chip-danger'
  if (['WARN', 'WARNING', 'SUSPICIOUS'].includes(type)) return 'chip-warn'
  return 'chip-info'
}

function paperTitle(row: SessionRow | null): string {
  if (!row) return '—'
  return paperMap.value[String(row.examPaperId)] || `试卷#${row.examPaperId ?? '—'}`
}

async function load() {
  loading.value = true
  sessionsError.value = ''
  try {
    const body = unwrap(await examApi.getActiveExams())
    const arr = rowsOf(body) as SessionRow[]
    sessions.value = arr
    const ids = [...new Set(arr.map((row) => row.examPaperId).filter(Boolean))]
    await Promise.all(
      ids.map(async (id) => {
        try {
          const paper = await examApi.getExamPaperById(id as number | string)
          const data = (paper as { data?: { title?: string; courseId?: number } })?.data ?? paper
          paperMap.value[String(id)] = (data as { title?: string })?.title || `试卷#${id}`
          const course = (data as { courseId?: number })?.courseId
          if (course) paperCourse.value[String(id)] = Number(course)
        } catch (error) {
          logger.error('加载试卷信息失败', id, error)
        }
      }),
    )
    if (selected.value) {
      const still = arr.find((row) => row.id === selected.value?.id)
      if (still) selected.value = still
    }
  } catch (error) {
    logger.error('加载进行中考试失败', error)
    sessions.value = []
    sessionsError.value = '进行中考试会话加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

async function selectSession(row: SessionRow) {
  selected.value = row
  selectedRisk.value = null
  events.list = []
  riskError.value = ''
  eventsError.value = ''
  risk.loading = true
  risk.list = []
  try {
    risk.list = rowsOf(unwrap(await proctorApi.getExamRisk(row.id)))
  } catch (error) {
    logger.error('加载风险汇总失败', error)
    risk.list = []
    riskError.value = '风险记录加载失败，请检查网络后重试'
  } finally {
    risk.loading = false
  }
}

async function resolveProctorSessionId(
  row: Record<string, unknown>,
): Promise<string | number | null> {
  const direct = pick(row, ['proctorSessionId', 'sessionId', 'proctorId', 'proctorSession'])
  if (direct) return direct
  const studentId = pick(row, ['studentId', 'studentUserId', 'userId'])
  if (studentId && selected.value) {
    try {
      const response = await proctorApi.getStudentSession(selected.value.id, studentId)
      const body = (response as { data?: Record<string, unknown> })?.data ?? response
      const data = body as Record<string, unknown>
      return (data?.id || data?.proctorSessionId || data?.sessionId || null) as
        string | number | null
    } catch (error) {
      logger.error('获取学生监考会话失败', error)
    }
  }
  return null
}

async function selectRisk(row: Record<string, unknown>) {
  selectedRisk.value = row
  events.loading = true
  events.list = []
  eventsError.value = ''
  try {
    const targetId = (await resolveProctorSessionId(row)) || selected.value?.id
    if (!targetId) return
    events.list = rowsOf(unwrap(await proctorApi.getSessionEvents(targetId)))
  } catch (error) {
    logger.error('加载监考事件失败', error)
    events.list = []
    eventsError.value = '监考事件加载失败，请检查网络后重试'
  } finally {
    events.loading = false
  }
}

watch(scopedCourseId, () => {
  onlyThisCourse.value = false
})

onMounted(load)
</script>

<style scoped>
.proctor {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.proctor .stat-grid {
  margin-bottom: 0;
}
.card-head {
  align-items: center;
}
.card-head h2 .chip {
  margin-left: 6px;
  vertical-align: 2px;
}
.head-hint {
  font-size: 10.5px;
  color: var(--ink-3);
}
.course-only {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--ink-2);
}
.p-text {
  display: block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
.events-wrap {
  max-height: 360px;
  overflow-y: auto;
}
tbody tr.is-active {
  background: var(--surface-3);
}
</style>
