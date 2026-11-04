<template>
  <section class="kg">
    <div class="course-tab-bar">
      <div>
        <p class="card-kicker">COURSE / KNOWLEDGE GRAPH</p>
        <h2>知识图谱</h2>
      </div>
      <div class="course-tab-actions">
        <button class="btn btn-ghost" :disabled="graphLoading" @click="loadGraph">重载图谱</button>
        <button class="btn btn-ghost" :disabled="overviewLoading" @click="loadOverview">
          重载概览
        </button>
      </div>
    </div>

    <div v-if="loadError" class="callout">
      <span>ERROR / 加载失败</span>
      <p>{{ loadError }}</p>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="teacher" />
    </div>

    <div class="kg-grid">
      <section class="card">
        <header class="card-head">
          <div>
            <p class="card-kicker">GRAPH / RELATIONS</p>
            <h2>
              关系图
              <span class="chip chip-mute">{{ nodes.length }} 点 · {{ links.length }} 边</span>
            </h2>
          </div>
          <span class="head-hint">点击节点查看它的直接关系</span>
        </header>
        <svg v-if="layoutNodes.length" class="kg-svg" :viewBox="`0 0 ${SVG_W} ${SVG_H}`" role="img">
          <line
            v-for="(link, index) in layoutLinks"
            :key="`l${index}`"
            :x1="link.x1"
            :y1="link.y1"
            :x2="link.x2"
            :y2="link.y2"
            :class="{ 'is-dim': selectedId !== null && !link.touchesSelected }"
          />
          <g
            v-for="node in layoutNodes"
            :key="`n${node.id}`"
            :class="['kg-node', { 'is-selected': node.id === selectedId }]"
            @click="select(node.id)"
          >
            <circle :cx="node.x" :cy="node.y" :r="node.r" />
            <text :x="node.x" :y="node.y - node.r - 5">{{ node.name }}</text>
          </g>
        </svg>
        <div v-else-if="graphLoading" class="empty-state compact"><b>正在加载图谱…</b></div>
        <div v-else class="empty-state compact">
          <span class="e-ico"><Icon name="graph" /></span>
          <b>{{ graphError ? '图谱数据未能加载' : '该课程还没有知识关系' }}</b>
          <p>
            {{
              graphError
                ? '请查看上方提示后重试。'
                : '在「知识点管理」里建立前置依赖后，这里会出现关系图。'
            }}
          </p>
        </div>
        <footer v-if="selectedNode" class="card-foot">
          <span>{{ selectedNode.name }} · {{ categoryText(selectedNode.category) }}</span>
          <span
            v-if="isStudent"
            class="chip chip-mute"
            :title="masteryError || '按当前登录学生计算'"
          >
            我的掌握度 {{ masteryText }}
          </span>
          <button class="text-btn" @click="select(null)">取消选中</button>
        </footer>
      </section>

      <section class="card">
        <header class="card-head">
          <div>
            <p class="card-kicker">NEIGHBOURS</p>
            <h2>直接关系</h2>
          </div>
        </header>
        <p v-if="!selectedNode" class="modal-hint">未选中节点。</p>
        <ul v-else-if="neighbours.length" class="nb-list">
          <li v-for="(item, index) in neighbours" :key="index">
            <span class="chip" :class="item.direction === 'out' ? 'chip-accent' : 'chip-info'">
              {{ item.direction === 'out' ? '指向' : '来自' }}
            </span>
            <span class="nb-name">{{ item.name }}</span>
            <span class="nb-rel">{{ relationText(item.relationType) }}</span>
          </li>
        </ul>
        <div v-else class="empty-state compact"><b>该节点暂无直接关系</b></div>
      </section>
    </div>

    <div class="kg-grid">
      <section class="card">
        <header class="card-head">
          <div>
            <p class="card-kicker">PATH / PLAN</p>
            <h2>学习路径规划</h2>
          </div>
        </header>
        <div class="form-grid">
          <label class="field">
            <span>起点</span>
            <select v-model="pathSource" class="input">
              <option :value="0">选择知识点</option>
              <option v-for="node in nodes" :key="`s${node.id}`" :value="Number(node.id)">
                {{ node.name }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>终点</span>
            <select v-model="pathTarget" class="input">
              <option :value="0">选择知识点</option>
              <option v-for="node in nodes" :key="`t${node.id}`" :value="Number(node.id)">
                {{ node.name }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>最大跳数</span>
            <input v-model.number="maxHops" class="input" type="number" min="1" max="8" />
          </label>
          <div class="field">
            <span>&nbsp;</span>
            <button
              class="btn btn-primary"
              :disabled="planning || !pathSource || !pathTarget"
              @click="planPath"
            >
              {{ planning ? '规划中…' : '规划路径' }}
            </button>
          </div>
        </div>
        <p v-if="pathError" class="form-error">{{ pathError }}</p>
        <ol v-if="pathResult?.path?.length" class="path-list">
          <li v-for="(step, index) in pathResult.path" :key="`p${index}`">
            <b>{{ index + 1 }}</b>
            <span>{{ step.name || `知识点 #${step.id}` }}</span>
          </li>
        </ol>
        <p v-else-if="pathResult && !pathResult.found" class="modal-hint">
          两点之间在 {{ maxHops }} 跳内不可达。
        </p>
        <footer v-if="pathResult" class="card-foot">
          <span>{{ pathResult.hops ?? 0 }} 跳</span>
          <span class="chip" :class="modeChip(pathResult.mode)">{{
            modeText(pathResult.mode)
          }}</span>
        </footer>
        <p v-if="pathResult?.note" class="modal-hint">{{ pathResult.note }}</p>
      </section>

      <section v-if="!isStudent" class="card">
        <header class="card-head">
          <div>
            <p class="card-kicker">ENGINE / 8001</p>
            <h2>图谱引擎</h2>
          </div>
        </header>
        <div class="course-tab-actions engine-actions">
          <button class="btn btn-ghost" :disabled="syncing" @click="syncEngine">
            {{ syncing ? '同步中…' : '同步到引擎' }}
          </button>
          <button class="btn btn-ghost" :disabled="subLoading" @click="loadSubGraph">
            {{ subLoading ? '拉取中…' : '拉取引擎子图' }}
          </button>
        </div>
        <p v-if="syncError" class="form-error">{{ syncError }}</p>
        <p v-if="subError" class="form-error">{{ subError }}</p>
        <div v-if="syncMode || syncNote" class="engine-row">
          <span class="chip" :class="modeChip(syncMode)">{{ modeText(syncMode) }}</span>
          <span class="engine-note">{{ syncNote }}</span>
        </div>
        <template v-if="subGraph">
          <div class="engine-row">
            <span class="chip" :class="modeChip(subMode)">{{ modeText(subMode) }}</span>
            <span class="engine-note">
              子图 {{ subGraph.nodes.length }} 节点 / {{ subGraph.edges.length }} 边
            </span>
          </div>
          <ul class="nb-list">
            <li v-for="(node, index) in subGraph.nodes.slice(0, 6)" :key="`sn${index}`">
              <span class="nb-name">{{ node.name }}</span>
              <span class="nb-rel">{{ categoryText(node.type) }}</span>
            </li>
          </ul>
          <p v-if="subNote" class="modal-hint">{{ subNote }}</p>
        </template>
        <div v-else-if="!syncError && !subError" class="empty-state compact">
          <b>尚未与引擎交互</b>
          <p>同步或拉取子图后，这里显示引擎返回的模式与规模。</p>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import Stat from '../../../components/Stat.vue'
import Icon from '../../../components/Icon.vue'
import { useUi } from '../../../composables/ui'
import knowledgeApi from '@/api/knowledge'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'
import type { Metric } from '../../../nav'

interface KgNode {
  id: number | string
  name?: string
  category?: string
  importance?: number
}

interface KgLink {
  source: number | string
  target: number | string
  relationType?: string
}

interface LayoutNode extends KgNode {
  x: number
  y: number
  r: number
}

interface LayoutLink extends KgLink {
  x1: number
  y1: number
  x2: number
  y2: number
  touchesSelected: boolean
}

interface PathStep {
  id?: number | string
  name?: string
}

interface PathResult {
  path?: PathStep[]
  hops?: number
  found?: boolean
  mode?: string
  note?: string
}

interface SubGraph {
  nodes: { id?: number | string; name?: string; type?: string }[]
  edges: { source_id?: number | string; target_id?: number | string }[]
}

const SVG_W = 640
const SVG_H = 420

const MODE_TEXT: Record<string, string> = {
  ENGINE: '引擎计算',
  LOCAL: '本地兜底',
  UNAVAILABLE: '引擎不可用',
}
const CATEGORY_TEXT: Record<string, string> = {
  CONCEPT: '概念',
  SKILL: '技能',
  SUMMARY: '小结',
}

// 学生复用同一张图：只读、隐藏引擎写入面板，并在选中节点时叠加「我的掌握度」
const props = defineProps<{ courseId: number; mode?: 'teacher' | 'student' }>()
const isStudent = computed(() => props.mode === 'student')
const auth = useAuthStore()
const { toast } = useUi()

const overview = reactive<Record<string, number | null>>({
  totalPoints: null,
  totalRelations: null,
  corePoints: null,
  coverageRate: null,
})
const overviewLoading = ref(false)
const overviewError = ref('')

const nodes = ref<KgNode[]>([])
const links = ref<KgLink[]>([])
const graphLoading = ref(false)
const graphError = ref('')
const selectedId = ref<number | string | null>(null)

const pathSource = ref(0)
const pathTarget = ref(0)
const maxHops = ref(5)
const planning = ref(false)
const pathError = ref('')
const pathResult = ref<PathResult | null>(null)

const syncing = ref(false)
const syncError = ref('')
const syncMode = ref('')
const syncNote = ref('')
const subLoading = ref(false)
const subError = ref('')
const subMode = ref('')
const subNote = ref('')
const subGraph = ref<SubGraph | null>(null)

const loadError = computed(() => overviewError.value || graphError.value)

const metrics = computed<Metric[]>(() => [
  {
    label: '知识点',
    value: display(overview.totalPoints),
    unit: '个',
    note: '课程概览',
    icon: 'path',
  },
  {
    label: '知识关系',
    value: display(overview.totalRelations),
    unit: '条',
    note: '课程概览',
    icon: 'graph',
  },
  {
    label: '核心知识点',
    value: display(overview.corePoints),
    unit: '个',
    note: '课程概览',
    icon: 'award',
    tone: 'ok',
  },
  {
    label: '覆盖率',
    value:
      overview.coverageRate === null ? '—' : `${Math.round(Number(overview.coverageRate) * 100)}%`,
    note: '已掌握口径',
    icon: 'check',
    tone: 'warn',
  },
])

const layoutNodes = computed<LayoutNode[]>(() => {
  const count = nodes.value.length
  if (!count) return []
  const cx = SVG_W / 2
  const cy = SVG_H / 2
  const radius = Math.min(cx, cy) - 62
  return nodes.value.map((node, index) => {
    const angle = (Math.PI * 2 * index) / count - Math.PI / 2
    const importance = Number(node.importance ?? 3)
    return {
      ...node,
      x: cx + radius * Math.cos(angle),
      y: cy + radius * Math.sin(angle),
      r: 9 + Math.max(0, Math.min(10, importance)) * 1.1,
    }
  })
})

const layoutLinks = computed<LayoutLink[]>(() => {
  const byId = new Map(layoutNodes.value.map((node) => [String(node.id), node]))
  return links.value.flatMap((link) => {
    const from = byId.get(String(link.source))
    const to = byId.get(String(link.target))
    if (!from || !to) return []
    return [
      {
        ...link,
        x1: from.x,
        y1: from.y,
        x2: to.x,
        y2: to.y,
        touchesSelected:
          selectedId.value === null ||
          String(link.source) === String(selectedId.value) ||
          String(link.target) === String(selectedId.value),
      },
    ]
  })
})

const selectedNode = computed(() =>
  selectedId.value === null
    ? null
    : nodes.value.find((node) => String(node.id) === String(selectedId.value)) || null,
)

const neighbours = computed(() => {
  if (selectedId.value === null) return []
  const byId = new Map(nodes.value.map((node) => [String(node.id), node]))
  return links.value.flatMap((link) => {
    const isSource = String(link.source) === String(selectedId.value)
    const isTarget = String(link.target) === String(selectedId.value)
    if (!isSource && !isTarget) return []
    const peer = byId.get(String(isSource ? link.target : link.source))
    return [
      {
        direction: isSource ? ('out' as const) : ('in' as const),
        name: peer?.name || '未知节点',
        relationType: link.relationType || '',
      },
    ]
  })
})

function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

function modeText(mode?: string): string {
  return MODE_TEXT[String(mode || '')] || '未知'
}

function modeChip(mode?: string): string {
  const key = String(mode || '')
  if (key === 'ENGINE') return 'chip-ok'
  if (key === 'LOCAL') return 'chip-warn'
  if (key === 'UNAVAILABLE') return 'chip-danger'
  return 'chip-mute'
}

function categoryText(value?: string): string {
  return CATEGORY_TEXT[String(value || '')] || String(value || '概念')
}

function relationText(value?: string): string {
  const map: Record<string, string> = {
    PREREQUISITE: '前置依赖',
    RELATED: '相关',
    CONTAINS: '包含',
  }
  return map[String(value || '')] || String(value || '关联')
}

function select(id: number | string | null) {
  selectedId.value = id
  mastery.value = null
  masteryError.value = ''
  if (isStudent.value && id !== null) void loadMastery(Number(id))
}

const mastery = ref<{ score: number | null; level?: string } | null>(null)
const masteryLoading = ref(false)
const masteryError = ref('')
const masteryText = computed(() => {
  if (masteryLoading.value) return '读取中…'
  if (masteryError.value) return '—'
  if (!mastery.value || mastery.value.score === null) return '暂无数据'
  return `${Math.round(mastery.value.score)} 分${mastery.value.level ? ' · ' + mastery.value.level : ''}`
})

async function loadMastery(pointId: number) {
  const userId = auth.userId
  if (!userId) return
  masteryLoading.value = true
  try {
    const raw = unwrap(unwrap(await knowledgeApi.getMastery(pointId, userId))) as {
      score?: number
      masteryScore?: number
      level?: string
    } | null
    const score = raw?.score ?? raw?.masteryScore ?? null
    mastery.value = { score: typeof score === 'number' ? score : null, level: raw?.level }
  } catch (e) {
    masteryError.value = e instanceof Error ? e.message : '掌握度读取失败'
  } finally {
    masteryLoading.value = false
  }
}

async function loadOverview() {
  if (!props.courseId) return
  overviewLoading.value = true
  overviewError.value = ''
  try {
    const body = (unwrap(unwrap(await knowledgeApi.getKnowledgeOverview(props.courseId))) ||
      {}) as Record<string, unknown>
    overview.totalPoints = numberOrNull(body.totalPoints)
    overview.totalRelations = numberOrNull(body.totalRelations)
    overview.corePoints = numberOrNull(body.corePoints)
    overview.coverageRate = numberOrNull(body.coverageRate)
  } catch (error) {
    logger.error('加载知识概览失败', error)
    overviewError.value = '知识概览加载失败，请检查网络后重试'
  } finally {
    overviewLoading.value = false
  }
}

function numberOrNull(value: unknown): number | null {
  const number = Number(value)
  return value === null || value === undefined || !Number.isFinite(number) ? null : number
}

async function loadGraph() {
  if (!props.courseId) return
  graphLoading.value = true
  graphError.value = ''
  try {
    const body = (unwrap(await knowledgeApi.getRelationGraphData(props.courseId)) || {}) as {
      nodes?: KgNode[]
      links?: KgLink[]
    }
    nodes.value = Array.isArray(body.nodes) ? body.nodes : []
    links.value = Array.isArray(body.links) ? body.links : []
    if (
      selectedId.value &&
      !nodes.value.some((node) => String(node.id) === String(selectedId.value))
    ) {
      selectedId.value = null
    }
  } catch (error) {
    logger.error('加载图谱数据失败', error)
    nodes.value = []
    links.value = []
    graphError.value = '图谱数据加载失败，请检查网络后重试'
  } finally {
    graphLoading.value = false
  }
}

async function planPath() {
  if (!pathSource.value || !pathTarget.value) return
  planning.value = true
  pathError.value = ''
  pathResult.value = null
  try {
    const body = (unwrap(
      unwrap(
        await knowledgeApi.planLearningPath(pathSource.value, pathTarget.value, maxHops.value),
      ),
    ) || {}) as PathResult
    pathResult.value = body
  } catch (error) {
    logger.error('路径规划失败', error)
    pathError.value = '路径规划失败，请稍后重试'
  } finally {
    planning.value = false
  }
}

async function syncEngine() {
  syncing.value = true
  syncError.value = ''
  syncMode.value = ''
  syncNote.value = ''
  try {
    const body = (unwrap(unwrap(await knowledgeApi.ingestKnowledgeGraph(props.courseId))) ||
      {}) as {
      mode?: string
      note?: string
    }
    syncMode.value = body.mode || ''
    syncNote.value =
      body.note || (body.mode === 'ENGINE' ? '已同步至图谱引擎' : '已在消费侧确认数据')
    toast('引擎同步完成', 'ok')
  } catch (error) {
    logger.error('图谱引擎同步失败', error)
    syncError.value = '图谱引擎同步失败，请检查网络后重试'
  } finally {
    syncing.value = false
  }
}

async function loadSubGraph() {
  subLoading.value = true
  subError.value = ''
  try {
    const body = (unwrap(unwrap(await knowledgeApi.getKnowledgeSubGraph(props.courseId, 3))) ||
      {}) as {
      mode?: string
      note?: string
      nodes?: SubGraph['nodes']
      edges?: SubGraph['edges']
    }
    subMode.value = body.mode || ''
    subNote.value = body.note || ''
    subGraph.value = {
      nodes: Array.isArray(body.nodes) ? body.nodes : [],
      edges: Array.isArray(body.edges) ? body.edges : [],
    }
  } catch (error) {
    logger.error('引擎子图加载失败', error)
    subGraph.value = null
    subError.value = '引擎子图加载失败，请检查网络后重试'
  }
}

function loadAll() {
  pathResult.value = null
  pathError.value = ''
  pathSource.value = 0
  pathTarget.value = 0
  selectedId.value = null
  subGraph.value = null
  subMode.value = ''
  subNote.value = ''
  syncMode.value = ''
  syncNote.value = ''
  void loadOverview()
  void loadGraph()
}

watch(() => props.courseId, loadAll, { immediate: true })
</script>

<style scoped>
.kg {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.kg-grid {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 14px;
  align-items: start;
}
.head-hint {
  font-size: 10.5px;
  color: var(--ink-3);
}
.kg-svg {
  width: 100%;
  height: auto;
  display: block;
}
.kg-svg line {
  stroke: var(--line);
  stroke-width: 1.2;
}
.kg-svg line.is-dim {
  stroke-opacity: 0.25;
}
.kg-node {
  cursor: pointer;
}
.kg-node circle {
  fill: var(--surface-3);
  stroke: var(--accent);
  stroke-width: 1.4;
}
.kg-node text {
  font-size: 10px;
  fill: var(--ink-2);
  text-anchor: middle;
  pointer-events: none;
}
.kg-node.is-selected circle {
  fill: var(--accent-soft);
  stroke-width: 2.4;
}
.nb-list,
.path-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.nb-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.nb-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nb-rel {
  font-family: var(--font-mono);
  font-size: 9.5px;
  color: var(--ink-3);
}
.path-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}
.path-list b {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: var(--r-circle);
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 10px;
}
.engine-actions {
  margin-bottom: 12px;
}
.engine-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.engine-note {
  font-size: 11px;
  color: var(--ink-2);
}
@media (max-width: 1100px) {
  .kg-grid {
    grid-template-columns: 1fr;
  }
}
</style>
