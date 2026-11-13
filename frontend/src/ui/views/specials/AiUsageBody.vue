<template>
  <section class="ai-usage-body">
    <!-- 口径先摆在前头：数据源 ai_call_logs（2026-10-13 起埋点）。
         2026-11-07 P2 批次起提供按课程 / 按日 / 按能力三个维度；出题治理＝总闸 + 逐人名单。 -->
    <div class="card caveat-card">
      <p class="card-kicker">SCOPE / 口径边界</p>
      <p class="scope-line">
        数据源 <code>ai_call_logs</code>（2026-10-13 起埋点），当前统计窗
        <b>近 {{ rangeDays }} 天</b>{{ rangeText }}。
      </p>
      <p v-if="collectionNote" class="scope-note">{{ collectionNote }}</p>
      <p class="scope-note">
        按日/按能力无调用的分组<b>不出行</b>（只存事实行，不补示例读数）；人均 token 配额为平台级
        100 次/日、200k tokens/日（环境变量配置），逐人出题限制见下方「出题治理」。
      </p>
    </div>

    <div v-if="listError" class="callout" role="alert">
      <span>ERROR / AI 用量</span>
      <p>{{ listError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="load()">
        {{ loading ? '重试中…' : '重试' }}
      </button>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">AI USAGE / BY COURSE</p>
          <h2>
            按课程聚合 <span class="chip chip-mute">{{ rows.length }} 行</span>
          </h2>
        </div>
        <div class="filter-row">
          <button
            v-for="option in RANGE_OPTIONS"
            :key="option"
            class="btn btn-sm"
            :class="option === rangeDays ? 'btn-primary' : 'btn-ghost'"
            :disabled="loading"
            :aria-pressed="option === rangeDays"
            @click="setRange(option)"
          >
            近 {{ option }} 天
          </button>
          <button class="btn btn-sm btn-ghost" :disabled="loading" @click="reloadAll()">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>

      <div v-if="rows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>课程</th>
              <th class="w-96">调用次数</th>
              <th class="w-96">成功率</th>
              <th class="w-120">平均耗时</th>
              <th class="w-120">Token 合计</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in rows" :key="rowKey(row, index)">
              <td>
                <b>{{ row.courseName || '—' }}</b>
                <small class="cell-sub">
                  {{
                    row.courseId === null || row.courseId === undefined
                      ? '无课程归属（通识问答／批改）'
                      : `课程 ID ${row.courseId}`
                  }}
                </small>
              </td>
              <td>{{ formatCount(row.calls) }}</td>
              <td>{{ formatRate(row.successRate) }}</td>
              <td>{{ formatDuration(row.avgDurationMs) }}</td>
              <td>{{ formatCount(row.tokens) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else-if="loading" class="empty-state compact">
        <b>正在加载 AI 用量…</b>
      </div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="sparkle" /></span>
        <b>{{ emptyDescription() }}</b>
        <p>
          {{
            listError
              ? '请查看上方提示后重试。'
              : '统计窗内没有调用记录时不会给出 0 行表格，避免看起来像“已统计过”。'
          }}
        </p>
      </div>

      <p class="hint">
        「未绑定课程」行必须保留：它统计的是通识问答、作文批改等没有课程归属的调用，丢掉它总量就对不上。
      </p>
    </section>

    <section class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">AI USAGE / BY DAY</p>
          <h2>按日趋势</h2>
        </div>
      </header>
      <div
        v-if="dayRows.length"
        class="bar-chart"
        role="img"
        aria-label="近 N 天每日 AI 调用次数柱状图"
      >
        <div v-for="bucket in dayBuckets" :key="bucket.day" class="bar-col">
          <span class="bar-val">{{ bucket.calls }}</span>
          <i class="bar" :style="{ '--v': `${bucket.pct}%` }"></i>
          <span class="bar-label">{{ bucket.label }}</span>
        </div>
      </div>
      <div v-else-if="dayLoading" class="empty-state compact">
        <b>正在加载按日趋势…</b>
      </div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="chart" /></span>
        <b>{{ dayError ? '按日趋势加载失败' : `近 ${rangeDays} 天没有调用记录` }}</b>
        <p>{{ dayError ? dayError : '无调用时不画 0 高度柱，避免看起来像「统计过但为 0」。' }}</p>
      </div>
      <p class="hint">
        无调用的日期按 0 补齐区间横轴（峰值归一化）；柱高只反映调用次数，不反映 token 量。
      </p>
    </section>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">AI USAGE / BY SCENE</p>
          <h2>
            按能力聚合 <span class="chip chip-mute">{{ sceneRows.length }} 类</span>
          </h2>
        </div>
      </header>
      <div v-if="sceneRows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>能力</th>
              <th class="w-96">调用次数</th>
              <th class="w-96">成功率</th>
              <th class="w-120">平均耗时</th>
              <th class="w-120">Token 合计</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in sceneRows" :key="`${row.scene}-${index}`">
              <td>
                <b>{{ sceneLabel(row.scene) }}</b>
                <small class="cell-sub">{{ row.scene }}</small>
              </td>
              <td>{{ formatCount(row.calls) }}</td>
              <td>{{ formatRate(row.successRate) }}</td>
              <td>{{ formatDuration(row.avgDurationMs) }}</td>
              <td>{{ formatCount(row.tokens) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="sceneLoading" class="empty-state compact">
        <b>正在加载按能力聚合…</b>
      </div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="sparkle" /></span>
        <b>{{ sceneError ? '按能力聚合加载失败' : `近 ${rangeDays} 天没有调用记录` }}</b>
      </div>
      <p class="hint">
        「能力」即调用场景（ai_call_logs.scene）：问答/追问/作文批改润色/学习建议/出题/知识点抽取等；未登记的新场景以原始代码展示，不猜测语义。
      </p>
    </section>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">AI GOVERNANCE / QUESTION GENERATION</p>
          <h2>
            出题治理
            <span class="chip" :class="masterEnabled === false ? 'chip-warn' : 'chip-ok'">
              {{ masterEnabled === false ? '教师出题已全禁' : '教师出题允许' }}
            </span>
          </h2>
        </div>
        <div class="filter-row">
          <button
            class="btn btn-sm"
            :class="masterEnabled === false ? 'btn-primary' : 'btn-ghost'"
            :disabled="govBusy || masterEnabled === null"
            @click="toggleMaster()"
          >
            {{ govBusy ? '处理中…' : masterEnabled === false ? '恢复教师出题' : '全禁教师出题' }}
          </button>
        </div>
      </header>

      <div v-if="govError" class="callout" role="alert">
        <span>ERROR / 出题治理</span>
        <p>{{ govError }}</p>
        <button class="btn btn-sm" :disabled="govBusy" @click="loadGovernance()">重试</button>
      </div>

      <div class="restrict-form">
        <input
          v-model="newRestriction.userId"
          class="input"
          inputmode="numeric"
          placeholder="教师用户 ID"
          aria-label="教师用户 ID"
        />
        <input
          v-model="newRestriction.reason"
          class="input"
          placeholder="限制原因（展示给管理员）"
          aria-label="限制原因"
          maxlength="255"
        />
        <button
          class="btn btn-sm btn-primary"
          :disabled="govBusy || !newRestriction.userId"
          @click="addRestriction()"
        >
          加入限制名单
        </button>
      </div>

      <div v-if="restrictions.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>教师</th>
              <th>原因</th>
              <th class="w-120">加入时间</th>
              <th class="w-96">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in restrictions" :key="row.userId">
              <td>
                <b>{{ row.fullName || row.username || '—' }}</b>
                <small class="cell-sub">用户 ID {{ row.userId }}</small>
              </td>
              <td>{{ row.reason || '—' }}</td>
              <td>{{ formatDay(row.createdAt) }}</td>
              <td>
                <button class="text-btn" :disabled="govBusy" @click="removeRestriction(row.userId)">
                  移出名单
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="!govError" class="empty-state compact">
        <b>没有逐人限制</b>
        <p>总闸允许出题且名单为空＝全体教师可正常使用 AI 出题。</p>
      </div>
      <p class="hint">
        两层判定：总闸（system_configs 的 ai.question-generation.teacher-enabled）与逐人名单
        （ai_generation_restrictions）；生效点在教师出题入口，管理员不受限。仅校级管理员可改（院系管理员写入会被后端
        403）。
      </p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Icon from '../../components/Icon.vue'
import Stat from '../../components/Stat.vue'
import { useUi } from '../../composables/ui'
import { useListFeedback } from '@/composables/useListFeedback'
import {
  getAIUsageByCourse,
  getAIUsageByDay,
  getAIUsageByScene,
  getAiRestrictions,
  restrictAiGeneration,
  unrestrictAiGeneration,
  getConfigs,
  updateConfig,
} from '@/api/admin'
import { unwrap } from '@/utils/response'
import type { Metric } from '../../nav'

interface UsageRow {
  courseId?: number | string | null
  courseName?: string | null
  calls?: number | null
  tokens?: number | null
  avgDurationMs?: number | null
  successCalls?: number | null
  successRate?: number | null
}

interface UsagePayload {
  rangeDays?: number
  since?: string
  until?: string
  collectionNote?: string
  totalCalls?: number
  totalTokens?: number
  distinctCourses?: number
  unboundCalls?: number
  avgDurationMs?: number
  successRate?: number | null
  courses?: UsageRow[]
}

interface DayRow {
  day?: string | null
  calls?: number | null
  tokens?: number | null
  avgDurationMs?: number | null
  successCalls?: number | null
  successRate?: number | null
}

interface SceneRow {
  scene?: string | null
  calls?: number | null
  tokens?: number | null
  avgDurationMs?: number | null
  successCalls?: number | null
  successRate?: number | null
}

interface RestrictionRow {
  userId: number
  username?: string | null
  fullName?: string | null
  reason?: string | null
  restrictedBy?: number | null
  createdAt?: string | null
}

const RANGE_OPTIONS = [7, 30, 90] as const

/** ai_call_logs.scene → 中文能力名；未登记场景回退原始代码（不猜语义） */
const SCENE_LABELS: Record<string, string> = {
  QA_ASK: '课程问答',
  QA_CONTINUE: '追问续聊',
  ESSAY_CORRECT: '作文批改',
  ESSAY_POLISH: '作文润色',
  LEARNING_ADVICE: '学习建议',
  LEARNING_PATH: '学习路径',
  LEARNING_RECOMMEND: '学习推荐',
  QUESTION_GENERATE: 'AI 出题',
  QUESTION_RECOMMEND: '题目推荐',
  WEAK_POINTS: '薄弱点分析',
}

const MASTER_KEY = 'ai.question-generation.teacher-enabled'

const { toast } = useUi()

const rangeDays = ref<number>(30)
const rows = ref<UsageRow[]>([])
const payload = ref<UsagePayload>({})

const dayRows = ref<DayRow[]>([])
const dayLoading = ref(false)
const dayError = ref('')

const sceneRows = ref<SceneRow[]>([])
const sceneLoading = ref(false)
const sceneError = ref('')

const masterEnabled = ref<boolean | null>(null)
const restrictions = ref<RestrictionRow[]>([])
const govBusy = ref(false)
const govError = ref('')
const newRestriction = reactive({ userId: '', reason: '' })

const { loading, listError, run, emptyDescription } = useListFeedback({
  failMessage: 'AI 用量加载失败，请稍后重试。',
  toastMessage: 'AI 用量加载失败',
  emptyText: '该统计窗内未产生 AI 调用',
  errorEmptyText: '无法确认 AI 用量，请查看上方提示后重试',
  notify: (message) => toast(message, 'danger'),
})

const collectionNote = computed(() => String(payload.value.collectionNote || ''))
const rangeText = computed(() => {
  const since = String(payload.value.since || '').slice(0, 10)
  const until = String(payload.value.until || '').slice(0, 10)
  return since && until ? `（${since} → ${until}）` : ''
})

/**
 * 失败时不给 0/0%：那会被读成"统计过且确实为 0"。
 * 这与 §12「不得用假读数撑住页面」是同一条判据。
 */
const metrics = computed<Metric[]>(() => {
  const data = payload.value
  const failed = Boolean(listError.value) || !data.totalCalls
  return [
    {
      label: '总调用',
      value: listError.value ? '—' : formatCount(data.totalCalls),
      unit: '次',
      note: `近 ${rangeDays.value} 天`,
      icon: 'sparkle',
    },
    {
      label: '成功率',
      value:
        listError.value || data.successRate == null
          ? '—'
          : `${(data.successRate * 100).toFixed(1)}%`,
      note: '全部调用中 success=1 的占比',
      icon: 'check',
      tone: failed ? undefined : 'ok',
    },
    {
      label: '累计 Token',
      value: listError.value ? '—' : formatCount(data.totalTokens),
      unit: 'tokens',
      note: 'prompt + completion 估算',
      icon: 'chart',
    },
    {
      label: '覆盖课程',
      value: listError.value ? '—' : formatCount(data.distinctCourses),
      unit: '门',
      note: `另有未绑定课程 ${formatCount(data.unboundCalls)} 次`,
      icon: 'path',
    },
  ]
})

/** 按日趋势：区间补零 + 峰值归一化（与 AnalyticsTab 的自绘柱同款画法） */
const dayBuckets = computed(() => {
  if (!dayRows.value.length) return []
  const byDay = new Map<string, number>()
  for (const row of dayRows.value) {
    if (row.day) byDay.set(String(row.day).slice(0, 10), Number(row.calls) || 0)
  }
  const days: { day: string; label: string; calls: number; pct: number }[] = []
  const today = new Date()
  for (let i = rangeDays.value - 1; i >= 0; i -= 1) {
    const d = new Date(today)
    d.setDate(today.getDate() - i)
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    days.push({ day: key, label: key.slice(5), calls: byDay.get(key) || 0, pct: 0 })
  }
  const peak = Math.max(...days.map((d) => d.calls), 0)
  if (peak > 0) for (const d of days) d.pct = (d.calls / peak) * 100
  return days
})

function sceneLabel(scene?: string | null): string {
  if (!scene) return '—'
  return SCENE_LABELS[scene] || scene
}

function rowKey(row: UsageRow, index: number): string {
  return `${row.courseId ?? 'unbound'}-${index}`
}

function formatCount(value?: number | null): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return new Intl.NumberFormat('zh-CN').format(Number(value))
}

function formatRate(value?: number | null): string {
  if (value === null || value === undefined) return '—'
  return `${(Number(value) * 100).toFixed(1)}%`
}

function formatDuration(value?: number | null): string {
  if (value === null || value === undefined) return '—'
  return `${Number(value).toFixed(0)} ms`
}

function formatDay(value?: string | null): string {
  return value ? String(value).slice(0, 10) : '—'
}

function errMsg(error: unknown, fallback: string): string {
  const resp = (error as { response?: { data?: { message?: string } } })?.response
  return resp?.data?.message || fallback
}

async function load() {
  await run(async () => {
    const data = unwrap<UsagePayload>(await getAIUsageByCourse({ days: rangeDays.value }))
    if (!data || typeof data !== 'object') throw new Error('AI 用量响应格式无效')
    payload.value = data
    rows.value = Array.isArray(data.courses) ? data.courses : []
  })
}

async function loadDay() {
  dayLoading.value = true
  dayError.value = ''
  try {
    const data = unwrap<{ days?: DayRow[] }>(await getAIUsageByDay({ days: rangeDays.value }))
    dayRows.value = Array.isArray(data?.days) ? data.days : []
  } catch (error) {
    dayRows.value = []
    dayError.value = errMsg(error, '按日趋势加载失败，请稍后重试')
  } finally {
    dayLoading.value = false
  }
}

async function loadScene() {
  sceneLoading.value = true
  sceneError.value = ''
  try {
    const data = unwrap<{ scenes?: SceneRow[] }>(await getAIUsageByScene({ days: rangeDays.value }))
    sceneRows.value = Array.isArray(data?.scenes) ? data.scenes : []
  } catch (error) {
    sceneRows.value = []
    sceneError.value = errMsg(error, '按能力聚合加载失败，请稍后重试')
  } finally {
    sceneLoading.value = false
  }
}

async function loadGovernance() {
  govBusy.value = true
  govError.value = ''
  try {
    const [configs, list] = await Promise.all([getConfigs({ group: 'ai' }), getAiRestrictions()])
    const configRows = (unwrap<Record<string, unknown>[]>(configs) || []) as Array<
      Record<string, unknown>
    >
    const master = configRows.find(
      (row) => row?.configKey === MASTER_KEY || row?.key === MASTER_KEY,
    )
    const rawValue = master ? String(master.value ?? master.configValue ?? '') : ''
    // 缺省视为允许（与 AiGenerationPolicy 口径一致：只有显式 "false" 才禁）
    masterEnabled.value = rawValue === '' ? null : rawValue !== 'false'
    restrictions.value = (unwrap<RestrictionRow[]>(list) || []) as RestrictionRow[]
  } catch (error) {
    govError.value = errMsg(error, '出题治理读取失败，请稍后重试')
  } finally {
    govBusy.value = false
  }
}

async function toggleMaster() {
  if (govBusy.value || masterEnabled.value === null) return
  const next = !masterEnabled.value
  govBusy.value = true
  try {
    await updateConfig(MASTER_KEY, { value: next ? 'true' : 'false', group: 'ai' })
    masterEnabled.value = next
    toast(next ? '已恢复教师出题' : '已全禁教师出题（立即生效）', 'ok')
  } catch (error) {
    toast(errMsg(error, '总闸切换失败，请稍后重试'), 'danger')
  } finally {
    govBusy.value = false
  }
}

async function addRestriction() {
  const userId = Number(newRestriction.userId)
  if (!userId || govBusy.value) return
  govBusy.value = true
  try {
    await restrictAiGeneration(userId, { reason: newRestriction.reason || undefined })
    newRestriction.userId = ''
    newRestriction.reason = ''
    await loadGovernance()
    toast('已加入限制名单', 'ok')
  } catch (error) {
    toast(errMsg(error, '加入限制名单失败，请稍后重试'), 'danger')
  } finally {
    govBusy.value = false
  }
}

async function removeRestriction(userId: number) {
  if (govBusy.value) return
  govBusy.value = true
  try {
    await unrestrictAiGeneration(userId)
    await loadGovernance()
    toast('已移出限制名单', 'ok')
  } catch (error) {
    toast(errMsg(error, '移出失败，请稍后重试'), 'danger')
  } finally {
    govBusy.value = false
  }
}

function setRange(days: number) {
  if (days === rangeDays.value) return
  rangeDays.value = days
  void reloadAll()
}

function reloadAll() {
  void load()
  void loadDay()
  void loadScene()
}

onMounted(() => {
  void reloadAll()
  void loadGovernance()
})
</script>

<style scoped>
.ai-usage-body {
  display: grid;
  gap: 14px;
}
.caveat-card {
  display: grid;
  gap: 6px;
}
.scope-line {
  margin: 0;
  color: var(--text-2);
}
.scope-note {
  margin: 0;
  color: var(--text-3);
  font-size: 13px;
}
.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 150px;
  padding: 8px 4px 0;
  border-bottom: 1px solid var(--line);
  overflow-x: auto;
}
.bar-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  min-width: 10px;
  flex: 1 0 auto;
}
.bar-col .bar {
  width: 100%;
  height: var(--v, 0%);
  min-height: 2px;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--accent) 55%, transparent),
    var(--accent)
  );
  border-radius: 3px 3px 0 0;
}
.bar-col .bar-val {
  font-size: 9px;
  color: var(--text-3);
}
.bar-col .bar-label {
  font-size: 8.5px;
  color: var(--text-3);
  writing-mode: vertical-rl;
  height: 34px;
}
.restrict-form {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 4px 0 8px;
}
.restrict-form .input {
  flex: 1 1 160px;
}
</style>
