<template>
  <section class="kg">
    <div class="course-tab-bar">
      <div>
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
      <span>加载失败</span>
      <p>{{ loadError }}</p>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="teacher" />
    </div>

    <div class="kg-grid">
      <!-- ==================== 关系图 ==================== -->
      <section class="card">
        <header class="card-head">
          <div>
            <h2>
              关系图
              <span class="chip chip-mute">{{ nodes.length }} 点 · {{ links.length }} 边</span>
            </h2>
          </div>
          <div class="kg-view-switch">
            <button
              class="btn btn-ghost"
              :class="{ 'is-active': activeView === 'panorama' }"
              @click="activeView = 'panorama'"
            >
              全景
            </button>
            <button
              class="btn btn-ghost"
              :class="{ 'is-active': activeView === 'tree' }"
              @click="activeView = 'tree'"
            >
              层次树
            </button>
            <button
              class="btn btn-ghost"
              :class="{ 'is-active': activeView === 'chapter' }"
              @click="activeView = 'chapter'"
            >
              章节图谱
            </button>
            <button
              v-if="activeView === 'panorama'"
              class="btn btn-ghost"
              @click="showAllLabels = !showAllLabels"
            >
              {{ showAllLabels ? '精简标签' : '显示全部标签' }}
            </button>
          </div>
        </header>
        <div v-if="activeView === 'chapter'" class="kg-chapter-picker">
          <label>选择章节：</label>
          <select v-model="selectedChapterId" class="kg-chapter-select">
            <option v-for="option in chapterOptions" :key="option.id" :value="option.id">
              {{ option.name }}（{{ option.members }} 实体）
            </option>
          </select>
        </div>
        <div
          v-show="activeView !== 'panorama'"
          ref="echartsEl"
          class="kg-echarts"
          :class="{ 'is-loading': echartsLoading }"
        ></div>
        <svg
          v-if="activeView === 'panorama' && layoutNodes.length"
          class="kg-svg"
          :viewBox="`0 0 ${SVG_W} ${SVG_H}`"
          role="img"
        >
          <line
            v-for="(link, index) in layoutLinks"
            :key="`l${index}`"
            :x1="link.x1"
            :y1="link.y1"
            :x2="link.x2"
            :y2="link.y2"
            :class="{
              'is-related': isRelatedLink(link),
              'is-chapter-frame': link.relationType === '包含章节',
              'is-dim':
                (selectedId !== null && !link.touchesSelected) || (hasPath && !isPathLink(link)),
              'is-path': hasPath && isPathLink(link),
            }"
          />
          <g
            v-for="node in layoutNodes"
            :key="`n${node.id}`"
            :class="[
              'kg-node',
              'kg-node--' + String(node.category ?? 'CONCEPT').toLowerCase(),
              {
                'is-selected': node.id === selectedId,
                'is-path': inPath(node.id),
                'is-dim': hasPath && !inPath(node.id),
              },
            ]"
            @click="select(node.id)"
          >
            <circle :cx="node.x" :cy="node.y" :r="node.r" />
            <title>{{ node.name }}</title>
            <text
              v-if="node.showLabel"
              :x="node.lx"
              :y="node.ly"
              :text-anchor="node.anchor"
              >{{ node.name }}</text
            >
          </g>
        </svg>
        <div
          v-else-if="activeView === 'panorama' && graphLoading"
          class="empty-state compact"
        >
          <b>正在加载图谱…</b>
        </div>
        <div v-else-if="activeView === 'panorama'" class="empty-state compact">
          <span class="e-ico"><Icon name="graph" /></span>
          <b>{{ graphError ? '图谱数据未能加载' : '该课程还没有知识关系' }}</b>
        </div>
        <footer v-if="selectedNode" class="card-foot">
          <span>{{ selectedNode.name }} · {{ categoryText(selectedNode.category) }}</span>
          <span v-if="isStudent" class="chip chip-mute" :title="masteryError || masteryHint">
            我的掌握度 {{ masteryText }}
          </span>
          <button class="text-btn" @click="select(null)">取消选中</button>
        </footer>
      </section>

      <!-- ==================== 选中节点详情 / 图谱导读 ==================== -->
      <section class="card kg-side">
        <!-- 未选中：图例与导读 -->
        <template v-if="!selectedNode">
          <header class="card-head">
            <div>
              <h2>图例</h2>
            </div>
          </header>
          <div class="legend">
            <div class="legend-row">
              <svg viewBox="0 0 64 10" class="legend-line">
                <line x1="2" y1="5" x2="62" y2="5" />
              </svg>
              <span>前置依赖（先修 → 后继）</span>
            </div>
            <div class="legend-row">
              <svg viewBox="0 0 64 10" class="legend-line legend-related">
                <line x1="2" y1="5" x2="62" y2="5" />
              </svg>
              <span>相关（同一主题的横向关联）</span>
            </div>
            <div class="legend-row">
              <svg viewBox="0 0 64 10" class="legend-line">
                <line x1="2" y1="5" x2="62" y2="5" class="is-chapter-frame" />
              </svg>
              <span>包含章节（教材章节骨架）</span>
            </div>
            <div class="legend-row">
              <span class="kg-dot kg-dot--chapter"></span>
              <span>章节（教材大纲，内环）</span>
            </div>
            <div class="legend-row">
              <span class="kg-dot kg-dot--concept"></span>
              <span>教材抽取实体（挂所属章节外环）</span>
            </div>
            <div class="legend-row">
              <span class="kg-dot kg-dot--skill"></span>
              <span>技能</span>
            </div>
          </div>
        </template>

        <!-- 选中：节点详情 + 先修/后继分组 -->
        <template v-else>
          <header class="card-head">
            <div>
              <h2 class="kg-detail-name">{{ selectedNode.name }}</h2>
            </div>
            <button class="text-btn" @click="select(null)">取消选中</button>
          </header>

          <div class="kg-chips">
            <span
              class="chip"
              :class="selectedNode.category === 'SKILL' ? 'chip-accent' : 'chip-info'"
            >
              {{ categoryText(selectedNode.category) }}
            </span>
            <span v-if="selectedNode.difficultyLevel" class="chip chip-mute">
              难度 · {{ selectedNode.difficultyLevel }}
            </span>
            <span class="chip chip-mute"
              >重要性 {{ stars(Number(selectedNode.importance ?? 0)) }}</span
            >
            <span v-if="nodeDetail?.estimatedTime" class="chip chip-mute">
              约 {{ nodeDetail.estimatedTime }} 分钟
            </span>
          </div>

          <p class="kg-desc">
            {{ nodeDetail?.description || (detailLoading ? '正在加载详情…' : '暂无知识点描述。') }}
          </p>

          <div class="kg-rel-groups">
            <div v-if="relationGroups.prereqIn.length" class="kg-rel-group">
              <h3>先修 · 需要先掌握（{{ relationGroups.prereqIn.length }}）</h3>
              <ul>
                <li v-for="item in relationGroups.prereqIn" :key="`in${item.id}`">
                  <button class="text-btn" @click="select(item.id)">{{ item.name }}</button>
                </li>
              </ul>
            </div>
            <div v-if="relationGroups.prereqOut.length" class="kg-rel-group">
              <h3>后继 · 学完它再学（{{ relationGroups.prereqOut.length }}）</h3>
              <ul>
                <li v-for="item in relationGroups.prereqOut" :key="`out${item.id}`">
                  <button class="text-btn" @click="select(item.id)">{{ item.name }}</button>
                </li>
              </ul>
            </div>
            <div v-if="relationGroups.related.length" class="kg-rel-group">
              <h3>相关 · 同一主题（{{ relationGroups.related.length }}）</h3>
              <ul>
                <li v-for="item in relationGroups.related" :key="`rel${item.id}`">
                  <button class="text-btn" @click="select(item.id)">{{ item.name }}</button>
                </li>
              </ul>
            </div>
            <p
              v-if="
                !relationGroups.prereqIn.length &&
                !relationGroups.prereqOut.length &&
                !relationGroups.related.length
              "
              class="modal-hint"
            >
              该节点暂无直接关系。
            </p>
          </div>

          <div v-if="!isStudent" class="kg-actions">
            <button class="btn btn-ghost" @click="setPathEndpoint('source')">设为路径起点</button>
            <button class="btn btn-ghost" @click="setPathEndpoint('target')">设为路径终点</button>
          </div>
        </template>
      </section>
    </div>

    <div class="kg-grid">
      <!-- 路径规划只对教师/管理员开放：POST /knowledge/path/plan 目前被安全配置归入
           「资源写操作」而仅限 TEACHER/ADMIN，学生调用恒 403（已登记 后端待办项 B-13㉖）。
           入口按真实能力隐藏，而不是让学生点进去收一条「规划失败」。 -->
      <section v-if="!isStudent" class="card">
        <header class="card-head">
          <div>
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
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
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
  difficultyLevel?: string
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
  lx: number
  ly: number
  anchor: 'start' | 'end' | 'middle'
  showLabel: boolean
}

interface LayoutLink extends KgLink {
  x1: number
  y1: number
  x2: number
  y2: number
  touchesSelected: boolean
}

interface KgNodeDetail extends KgNode {
  description?: string
  estimatedTime?: number
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

const SVG_W = 900
const SVG_H = 640
const CX = SVG_W / 2
const CY = SVG_H / 2
const MODE_TEXT: Record<string, string> = {
  ENGINE: '引擎计算',
  LOCAL: '本地兜底',
  UNAVAILABLE: '引擎不可用',
}
const CATEGORY_TEXT: Record<string, string> = {
  CHAPTER: '章节',
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
/** 标签密度开关：默认只标注章节骨架与高度数节点，避免 500+ 节点标签互相遮挡 */
const showAllLabels = ref(false)
/** 视图切换：全景（SVG 分层径向）/ 层次树 / 章节图谱（ECharts，与已验收 DeepGraph 原型同款） */
const activeView = ref<'panorama' | 'tree' | 'chapter'>('panorama')
const echartsEl = ref<HTMLDivElement | null>(null)
const echartsLoading = ref(false)
const selectedChapterId = ref('')

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

/**
 * 章节骨架与归属（layoutNodes / chapterOptions / treeOption / chapterGraphOption
 * 四处视图共用的唯一事实来源，消除各视图重复实现导致的口径漂移）：
 * - parentOf/childrenOf/roots/depthOf：包含章节骨架（source=子章节 → target=父章节）；
 * - memberChapter：属于归属（source=实体 → target=章节）；
 * - chapterWeight：子树权重 = 1 + 子章节子树权重 + 直接挂靠实体数（记忆化）；
 * - degree：全图度数（节点大小与标签密度用）。
 */
const chapterSkeleton = computed(() => {
  const chapterIds = new Set(
    nodes.value.filter((n) => String(n.category ?? '') === 'CHAPTER').map((n) => String(n.id)),
  )
  const parentOf = new Map<string, string>()
  const memberChapter = new Map<string, string>()
  for (const link of links.value) {
    const s = String(link.source)
    const t = String(link.target)
    if (link.relationType === '包含章节' && chapterIds.has(s) && chapterIds.has(t)) {
      parentOf.set(s, t)
    } else if (link.relationType === '属于' && chapterIds.has(t)) {
      memberChapter.set(s, t)
    }
  }
  const childrenOf = new Map<string, string[]>()
  const roots: string[] = []
  for (const id of chapterIds) {
    const parent = parentOf.get(id)
    if (!parent || !chapterIds.has(parent)) {
      roots.push(id)
      continue
    }
    if (!childrenOf.has(parent)) childrenOf.set(parent, [])
    childrenOf.get(parent)!.push(id)
  }
  const depthOf = new Map<string, number>()
  const computeDepth = (id: string, seen: Set<string>): number => {
    const known = depthOf.get(id)
    if (known !== undefined) return known
    const parent = parentOf.get(id)
    if (!parent || !chapterIds.has(parent) || seen.has(id)) {
      depthOf.set(id, 1)
      return 1
    }
    seen.add(id)
    const depth = Math.min(4, computeDepth(parent, seen) + 1)
    depthOf.set(id, depth)
    return depth
  }
  for (const id of chapterIds) computeDepth(id, new Set())
  const directMembers = new Map<string, number>()
  for (const [, chapterId] of memberChapter) {
    directMembers.set(chapterId, (directMembers.get(chapterId) ?? 0) + 1)
  }
  const chapterWeight = new Map<string, number>()
  const computeWeight = (id: string, seen: Set<string>): number => {
    const known = chapterWeight.get(id)
    if (known !== undefined) return known
    if (seen.has(id)) return 1
    seen.add(id)
    let weight = 1 + (directMembers.get(id) ?? 0)
    for (const child of childrenOf.get(id) ?? []) {
      weight += computeWeight(child, seen)
    }
    chapterWeight.set(id, weight)
    return weight
  }
  for (const id of chapterIds) computeWeight(id, new Set())
  const degree = new Map<string, number>()
  for (const link of links.value) {
    const s = String(link.source)
    const t = String(link.target)
    degree.set(s, (degree.get(s) ?? 0) + 1)
    degree.set(t, (degree.get(t) ?? 0) + 1)
  }
  return { chapterIds, parentOf, childrenOf, roots, depthOf, memberChapter, chapterWeight, degree }
})

/**
 * 全景视图：分层径向布局（内环章节骨架、外环教材抽取实体）。
 * 骨架/归属/权重/度数全部来自共享 computed chapterSkeleton（唯一事实来源）。
 */
const layoutNodes = computed<LayoutNode[]>(() => {
  const list = nodes.value
  if (!list.length) return []
  const skeleton = chapterSkeleton.value
  const R_MAX = Math.min(CX, CY) - 60

  // 稳定散列：知识点 id 均以同一数字开头，旧实现只看首字符导致抖动全部同值失效
  const idHash = (id: string) => [...id].reduce((acc, ch) => acc + ch.charCodeAt(0), 0)

  const positions = new Map<string, { x: number; y: number; angle: number; root?: boolean }>()

  const placeChapter = (id: string, startAngle: number, endAngle: number, depth: number) => {
    const mid = (startAngle + endAngle) / 2
    const ring =
      R_MAX * [0, 0.34, 0.6, 0.8][Math.min(depth, 3)] +
      ((idHash(id) % 9) - 4) * (R_MAX * 0.006)
    positions.set(id, {
      x: CX + ring * Math.cos(mid),
      y: CY + ring * Math.sin(mid),
      angle: mid,
      root: depth === 1,
    })
    // 子章节按权重瓜分扇区内带
    const children = skeleton.childrenOf.get(id) ?? []
    let cursor = startAngle
    const totalChildWeight = children.reduce(
      (sum, c) => sum + (skeleton.chapterWeight.get(c) ?? 1),
      0,
    )
    for (const child of children) {
      const span =
        ((endAngle - startAngle) * (skeleton.chapterWeight.get(child) ?? 1)) /
        Math.max(1, totalChildWeight)
      placeChapter(child, cursor, cursor + span, depth + 1)
      cursor += span
    }
    // 直接挂在本章的实体：扇区内按子环错开（每 20 个一圈），避免全部堆在同一圆上
    const members = [...skeleton.memberChapter.entries()]
      .filter(([, cid]) => cid === id)
      .map(([eid]) => eid)
    const memberRingBase = Math.min(R_MAX * 0.94, ring + R_MAX * 0.1)
    const inner = Math.min(startAngle, endAngle)
    const outer = Math.max(startAngle, endAngle)
    members.forEach((eid, index) => {
      if (positions.has(eid)) return
      const subRing = Math.floor(index / 20)
      const inRing = index % 20
      const angle =
        inner + ((outer - inner) * (inRing + 0.5)) / Math.max(1, Math.min(20, members.length))
      const radius = memberRingBase + subRing * R_MAX * 0.045
      const jitter = ((idHash(eid) % 9) - 4) * (R_MAX * 0.005)
      positions.set(eid, {
        x: CX + (radius + jitter) * Math.cos(angle),
        y: CY + (radius + jitter) * Math.sin(angle),
        angle,
      })
    })
  }

  // 根章扇区占比 ∝ 子树权重（章节 + 实体），保证同章内容视觉聚合
  const roots = skeleton.roots
  const totalRootWeight = roots.reduce((sum, r) => sum + (skeleton.chapterWeight.get(r) ?? 1), 0)
  const looseOthers = list.filter(
    (n) => !skeleton.chapterIds.has(String(n.id)) && !skeleton.memberChapter.has(String(n.id)),
  )
  const gap = Math.PI * 2 * 0.012
  const usable = Math.PI * 2 - gap * Math.max(0, roots.length - 1) - (looseOthers.length ? gap : 0)
  let cursor = -Math.PI / 2
  for (const root of roots) {
    const weight = skeleton.chapterWeight.get(root) ?? 1
    const span = (usable * weight) / Math.max(1, totalRootWeight)
    placeChapter(root, cursor, cursor + span, 1)
    cursor += span + gap
  }

  // 无归属散点：最外环均分
  looseOthers.forEach((node, index) => {
    const angle = (Math.PI * 2 * index) / Math.max(1, looseOthers.length)
    positions.set(String(node.id), {
      x: CX + R_MAX * 0.985 * Math.cos(angle),
      y: CY + R_MAX * 0.985 * Math.sin(angle),
      angle,
    })
  })

  return list.flatMap((node) => {
    const pos = positions.get(String(node.id))
    if (!pos) return []
    const r = 6 + Math.min(20, skeleton.degree.get(String(node.id)) ?? 0) * 0.4
    const cos = Math.cos(pos.angle)
    const sin = Math.sin(pos.angle)
    let lx = pos.x
    let ly = pos.y
    let anchor: 'start' | 'end' | 'middle' = 'middle'
    if (cos > 0.35) {
      lx = pos.x + 13
      ly = pos.y + 4
      anchor = 'start'
    } else if (cos < -0.35) {
      lx = pos.x - 13
      ly = pos.y + 4
      anchor = 'end'
    } else if (sin < 0) {
      ly = pos.y - r - 8
    } else {
      ly = pos.y + r + 14
    }
    const showLabel =
      showAllLabels.value ||
      (pos.root ?? false) ||
      (skeleton.degree.get(String(node.id)) ?? 0) >= 10
    return [{ ...node, x: pos.x, y: pos.y, r, lx, ly, anchor, showLabel }]
  })
})

/** 章节下拉选项（层次树/章节图谱共用的选择器），按章号排序 */
const chapterOptions = computed(() => {
  const memberCount = new Map<string, number>()
  for (const [, chapterId] of chapterSkeleton.value.memberChapter) {
    memberCount.set(chapterId, (memberCount.get(chapterId) ?? 0) + 1)
  }
  const out = nodes.value
    .filter((n) => String(n.category ?? '') === 'CHAPTER')
    .map((n) => ({
      id: String(n.id),
      name: n.name ?? '',
      members: memberCount.get(String(n.id)) ?? 0,
    }))
  const order = (name: string): number => {
    const match = name.match(/第\s*(\d+)\s*章/)
    return match ? Number(match[1]) : 999
  }
  return out.sort((a, b) => {
    const oa = order(a.name)
    const ob = order(b.name)
    if (oa !== ob) return oa - ob
    return a.name.localeCompare(b.name, 'zh-Hans-CN')
  })
})

/** 默认选中实体最多的章节 */
watch(
  chapterOptions,
  (options) => {
    if (!options.length) {
      selectedChapterId.value = ''
      return
    }
    if (!options.some((option) => option.id === selectedChapterId.value)) {
      const best = [...options].sort((a, b) => b.members - a.members)[0]
      selectedChapterId.value = best.id
    }
  },
  { immediate: true },
)

const TREE_COLORS = {
  root: '#4a90d9',
  depth1: '#42b983',
  depth2: '#f5b041',
  deeper: '#b2b8be',
}

/** 层次树视图：教材大纲骨架 radial tree（同已验收 DeepGraph 原型的层次知识图谱） */
const treeOption = computed(() => {
  const skeleton = chapterSkeleton.value
  if (!skeleton.chapterIds.size) return null
  const nameOf = (id: string) =>
    nodes.value.find((n) => String(n.id) === id)?.name ?? id
  const toTreeNode = (id: string, depth: number, seen: Set<string>): Record<string, unknown> => {
    const children = (skeleton.childrenOf.get(id) ?? [])
      .filter((child) => !seen.has(child))
      .map((child) => toTreeNode(child, depth + 1, new Set([...seen, child])))
    return {
      name: nameOf(id),
      itemStyle: {
        color:
          depth <= 0
            ? TREE_COLORS.root
            : depth === 1
              ? TREE_COLORS.depth1
              : depth === 2
                ? TREE_COLORS.depth2
                : TREE_COLORS.deeper,
      },
      lineStyle: { color: nodeColorFallback(Math.max(0, depth - 1)) },
      label: { fontSize: depth <= 1 ? 13 : 11 },
      symbolSize: depth <= 1 ? 22 : depth === 2 ? 13 : 9,
      children,
    }
  }
  // 单一根（教材大纲根节点）直接作为树根的子层，避免多包一层合成的根
  const rootChildren =
    skeleton.roots.length === 1
      ? (skeleton.childrenOf.get(skeleton.roots[0]) ?? []).map((id) =>
          toTreeNode(id, 1, new Set()),
        )
      : skeleton.roots.map((id) => toTreeNode(id, 1, new Set()))
  return {
    tooltip: { trigger: 'item', triggerOn: 'mousemove' },
    series: [
      {
        type: 'tree',
        data: [
          {
            name: '计算机网络 · 教材图谱',
            itemStyle: { color: TREE_COLORS.root },
            symbolSize: 26,
            label: { fontSize: 14, fontWeight: 600 },
            children: rootChildren,
          },
        ],
        layout: 'radial',
        roam: true,
        top: '8%',
        left: '8%',
        bottom: '8%',
        right: '8%',
        symbol: 'circle',
        expandAndCollapse: true,
        initialTreeDepth: 3,
        animationDuration: 400,
        lineStyle: { width: 1.2, color: '#b2b8be', curveness: 0 },
      },
    ],
  }
})

function nodeColorFallback(depth: number): string {
  return depth <= 0
    ? TREE_COLORS.root
    : depth === 1
      ? TREE_COLORS.depth1
      : depth === 2
        ? TREE_COLORS.depth2
        : TREE_COLORS.deeper
}

/** 章节图谱视图：所选章节的实体子图（force graph，边带关系标签） */
const chapterGraphOption = computed(() => {
  if (!selectedChapterId.value) return null
  const skeleton = chapterSkeleton.value
  const chapterNode = nodes.value.find((n) => String(n.id) === selectedChapterId.value)
  if (!chapterNode) return null
  const memberIds = new Set<string>()
  for (const [entityId, chapterId] of skeleton.memberChapter) {
    if (chapterId === selectedChapterId.value) memberIds.add(entityId)
  }
  const inChapter = (id: string) =>
    memberIds.has(String(id)) || String(id) === selectedChapterId.value
  const data = [
    {
      id: selectedChapterId.value,
      name: chapterNode.name ?? '',
      category: 0,
      symbolSize: 42,
      itemStyle: { color: '#e74c3c' },
      label: { fontWeight: 600, fontSize: 13 },
    },
    ...nodes.value
      .filter((n) => memberIds.has(String(n.id)))
      .map((n) => ({
        id: String(n.id),
        name: n.name ?? '',
        category: 1,
        symbolSize: 12 + Math.min(18, (n.importance ?? 3) * 2),
        itemStyle: { color: '#3498db' },
      })),
  ]
  const linksIn = links.value
    .filter((link) => inChapter(String(link.source)) && inChapter(String(link.target)))
    .filter(
      (link) =>
        link.relationType !== '属于' || String(link.target) === selectedChapterId.value,
    )
    .map((link) => ({
      source: String(link.source),
      target: String(link.target),
      value: link.relationType ?? '相关',
    }))
  return {
    tooltip: {},
    legend: { show: false },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        data,
        links: linksIn,
        categories: [{ name: '章节' }, { name: '实体' }],
        force: { repulsion: 220, edgeLength: 90, gravity: 0.08 },
        label: { show: true, fontSize: 11, color: '#2c3e50' },
        edgeLabel: {
          show: true,
          fontSize: 9,
          color: '#8a8a8a',
          formatter: '{c}',
        },
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 7,
        lineStyle: { color: '#b2b8be', width: 1.1, curveness: 0.08 },
        emphasis: { focus: 'adjacency', lineStyle: { width: 2.2 } },
      },
    ],
  }
})

let echartsModule: Record<string, unknown> | null = null
let chartInstance: {
  setOption: (option: unknown, opts?: { notMerge?: boolean }) => void
  resize: () => void
  dispose: () => void
  on: (event: string, handler: (params: { data?: { id?: string | number } }) => void) => void
} | null = null

async function renderEchartsView() {
  if (activeView.value === 'panorama' || !echartsEl.value) {
    chartInstance?.dispose()
    chartInstance = null
    return
  }
  const option =
    activeView.value === 'tree' ? treeOption.value : chapterGraphOption.value
  if (!option) {
    chartInstance?.dispose()
    chartInstance = null
    return
  }
  echartsLoading.value = true
  try {
    // 关键：等 DOM 真正可见（v-show 的 display 已恢复、卡片完成布局）再 init——
    // 隐藏容器宽高为 0，ECharts 力导/树布局会塌缩成一条竖带（2026-11-22 实测根因）
    await nextTick()
    if (!echartsEl.value || echartsEl.value.clientWidth < 80) {
      await new Promise((resolve) => requestAnimationFrame(resolve))
      if (!echartsEl.value || echartsEl.value.clientWidth < 80) return
    }
    if (!echartsModule) {
      const echarts = await import('echarts/core')
      const { TreeChart, GraphChart } = await import('echarts/charts')
      const {
        TooltipComponent,
        LegendComponent,
        TitleComponent,
      } = await import('echarts/components')
      const { CanvasRenderer } = await import('echarts/renderers')
      echarts.use([TreeChart, GraphChart, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer])
      echartsModule = echarts as unknown as Record<string, unknown>
    }
    const echarts = echartsModule as unknown as {
      init: (el: HTMLElement) => {
        setOption: (option: unknown, opts?: { notMerge?: boolean }) => void
        resize: () => void
        dispose: () => void
        on: (event: string, handler: (params: { data?: { id?: string | number } }) => void) => void
      }
    }
    if (!chartInstance) {
      chartInstance = echarts.init(echartsEl.value)
      chartInstance.on('click', (params) => {
        const id = params?.data?.id
        if (id !== undefined) select(id)
      })
    }
    chartInstance.setOption(option, { notMerge: true })
    chartInstance.resize()
    requestAnimationFrame(() => chartInstance?.resize())
  } catch (error) {
    logger.error('ECharts 渲染失败', error)
  } finally {
    echartsLoading.value = false
  }
}

watch(
  [activeView, treeOption, chapterGraphOption, selectedChapterId],
  () => {
    void renderEchartsView()
  },
  { flush: 'post' },
)
onBeforeUnmount(() => {
  chartInstance?.dispose()
  chartInstance = null
})

/**
 * 路径高亮（doc 11 F2.2「路径规划与路径高亮」）：只对 **found 的路径** 生效。
 * 未找到路径时集合为空，图上一个点都不点亮——否则"不可达"会被画成"整张图都重要"。
 */
const pathIds = computed<Set<string>>(() => {
  const plan = pathResult.value
  if (!plan || !plan.found || !Array.isArray(plan.path)) return new Set()
  return new Set(
    plan.path.map((step) => String(step.id ?? '')).filter((id) => id && id !== 'undefined'),
  )
})

/** 路径上相邻两步之间的边（无向，两个方向都记，接口不保证 source/target 与路径顺序一致） */
const pathEdgeKeys = computed<Set<string>>(() => {
  const plan = pathResult.value
  const steps = plan?.path || []
  if (!plan || !plan.found || steps.length < 2) return new Set()
  const keys = new Set<string>()
  for (let i = 0; i < steps.length - 1; i += 1) {
    const from = String(steps[i].id ?? '')
    const to = String(steps[i + 1].id ?? '')
    if (!from || !to) continue
    keys.add(`${from}-${to}`)
    keys.add(`${to}-${from}`)
  }
  return keys
})

const hasPath = computed(() => pathIds.value.size > 0)

function inPath(id: unknown): boolean {
  return pathIds.value.has(String(id))
}

function isPathLink(link: KgLink): boolean {
  return pathEdgeKeys.value.has(`${String(link.source)}-${String(link.target)}`)
}

function isRelatedLink(link: KgLink): boolean {
  return link.relationType === 'RELATED'
}

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

/**
 * 选中节点的直接关系按学习语义分组：先修（谁在我前面）/ 后继（学完它再学什么）/ 相关（横向关联）。
 * 旧版「来自/指向」不区分关系类型，用户需要脑补方向，是本次重设计的直接动因。
 */
const relationGroups = computed(() => {
  const prereqIn: { id: number | string; name: string }[] = []
  const prereqOut: { id: number | string; name: string }[] = []
  const related: { id: number | string; name: string }[] = []
  if (selectedId.value === null) return { prereqIn, prereqOut, related }
  const byId = new Map(nodes.value.map((node) => [String(node.id), node]))
  const seen = new Set<string>()
  for (const link of links.value) {
    const isSource = String(link.source) === String(selectedId.value)
    const isTarget = String(link.target) === String(selectedId.value)
    if (!isSource && !isTarget) continue
    const peerId = String(isSource ? link.target : link.source)
    const peer = byId.get(peerId)
    if (!peer) continue
    const dedupe = `${link.relationType ?? ''}-${peerId}`
    if (seen.has(dedupe)) continue
    seen.add(dedupe)
    const item = { id: peerId, name: peer.name || '未知节点' }
    if (link.relationType === 'RELATED') related.push(item)
    else if (isSource) prereqOut.push(item)
    else prereqIn.push(item)
  }
  return { prereqIn, prereqOut, related }
})

/** 选中节点的描述/时长不在图负载里，按需拉取一次并缓存 */
const nodeDetail = ref<KgNodeDetail | null>(null)
const detailLoading = ref(false)
const detailCache = new Map<string, KgNodeDetail>()

watch(selectedId, async (id) => {
  nodeDetail.value = null
  mastery.value = null
  masteryError.value = ''
  if (id === null) return
  const key = String(id)
  if (detailCache.has(key)) {
    nodeDetail.value = detailCache.get(key) ?? null
    if (isStudent.value) void loadMastery(Number(id))
    return
  }
  detailLoading.value = true
  try {
    const raw = unwrap(await knowledgeApi.getKnowledgePointById(id)) as KgNodeDetail | null
    if (raw) {
      detailCache.set(key, raw)
      if (selectedId.value !== null && String(selectedId.value) === key) nodeDetail.value = raw
    }
  } catch (error) {
    logger.error('知识点详情加载失败', error)
  } finally {
    detailLoading.value = false
  }
  if (isStudent.value) void loadMastery(Number(id))
})

function stars(value: number): string {
  const clamped = Math.max(0, Math.min(5, value))
  return '★'.repeat(clamped) + '☆'.repeat(5 - clamped)
}

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

/** 把选中节点一键填进下方路径规划的起点/终点 */
function setPathEndpoint(which: 'source' | 'target') {
  if (selectedId.value === null) return
  const id = Number(selectedId.value)
  if (!id) return
  if (which === 'source') pathSource.value = id
  else pathTarget.value = id
  toast('已填入路径规划表单，调整跳数后点击「规划路径」', 'info')
}

function select(id: number | string | null) {
  selectedId.value = id
}

const mastery = ref<{
  score: number | null
  level?: string
  answered?: boolean
  answeredQuestions?: number
  totalQuestions?: number
  note?: string
} | null>(null)
const masteryLoading = ref(false)
const masteryError = ref('')
const masteryHint = computed(() => {
  if (masteryLoading.value) return '正在读取掌握度…'
  if (masteryError.value) return masteryError.value
  if (!mastery.value) return '选择知识点后按当前登录学生读取'
  if (!mastery.value.answered || mastery.value.score === null) {
    return mastery.value.note || '该知识点暂无作答记录'
  }
  const total = mastery.value.totalQuestions ?? 0
  const answered = mastery.value.answeredQuestions ?? 0
  return total > 0 ? `基于 ${answered}/${total} 道关联题` : '基于真实作答记录'
})
const masteryText = computed(() => {
  if (masteryLoading.value) return '读取中…'
  if (masteryError.value) return '—'
  if (!mastery.value || !mastery.value.answered || mastery.value.score === null) return '未评估'
  return `${Math.round(mastery.value.score)} 分${mastery.value.level ? ' · ' + mastery.value.level : ''}`
})

async function loadMastery(pointId: number) {
  const userId = auth.userId
  if (!userId) return
  masteryLoading.value = true
  masteryError.value = ''
  try {
    const raw = unwrap(unwrap(await knowledgeApi.getMastery(pointId, userId))) as
      | number
      | {
          score?: number | null
          masteryScore?: number | null
          level?: string
          answered?: boolean
          answeredQuestions?: number
          totalQuestions?: number
          note?: string
        }
      | null
    if (typeof raw === 'number') {
      // 兼容旧版后端：裸数值代表有证据的百分比，而不是“暂无数据”。
      mastery.value = { score: raw, answered: true }
      return
    }
    const score = raw?.score ?? raw?.masteryScore ?? null
    mastery.value = {
      score: typeof score === 'number' ? score : null,
      level: raw?.level,
      answered: raw?.answered ?? typeof score === 'number',
      answeredQuestions: raw?.answeredQuestions ?? 0,
      totalQuestions: raw?.totalQuestions ?? 0,
      note: raw?.note,
    }
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
    // 后端在「两点之间没有可达路径」时返回 **404 + note**（见 KnowledgeController）。
    // 这是业务结论，不是故障：照"规划失败"提示会让学生以为系统坏了、并去重试一条不存在的路。
    const status = (error as { response?: { status?: number; data?: { message?: string } } })
      ?.response
    if (status?.status === 404) {
      pathResult.value = {
        found: false,
        hops: 0,
        path: [],
        mode: 'UNAVAILABLE',
        note: status.data?.message || '',
      }
      return
    }
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
  grid-template-columns: 1.6fr 1fr;
  gap: 14px;
  align-items: start;
}
.kg-svg {
  width: 100%;
  height: auto;
  display: block;
}
.kg-echarts {
  width: 100%;
  height: 560px;
}
.kg-echarts.is-loading {
  opacity: 0.5;
}
.kg-chapter-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--ink-2);
}
.kg-chapter-select {
  min-width: 320px;
  padding: 6px 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-ctl);
  background: var(--surface-2);
  color: var(--ink-1);
}
.kg-svg line {
  stroke: var(--line-strong, var(--line));
  stroke-width: 1.3;
  transition:
    stroke-opacity 0.2s,
    stroke 0.2s;
}
.kg-svg line.is-related {
  stroke-dasharray: 5 4;
  stroke: var(--ink-3);
}
.kg-svg line.is-dim {
  stroke-opacity: 0.08;
}
/* 路径高亮：命中的边加粗提色，未命中的边压暗，让"从哪走到哪"一眼可读 */
.kg-svg line.is-path {
  stroke: var(--accent);
  stroke-width: 2.6;
  stroke-opacity: 1;
}
.kg-node {
  cursor: pointer;
}
.kg-node circle {
  fill: var(--surface-3);
  stroke: var(--accent);
  stroke-width: 1.4;
  transition:
    stroke-width 0.15s,
    fill 0.15s;
}
/* 分层径向：章节骨架（橙）/教材抽取实体（蓝）/技能（绿） */
.kg-node--chapter circle {
  fill: #f5b041;
  stroke: #b95c00;
}
.kg-node--chapter text {
  font-size: 12px;
  font-weight: 600;
  fill: #8a4a00;
}
.kg-node--concept circle {
  stroke: var(--accent);
}
.kg-node--concept text {
  font-size: 9.5px;
  fill: var(--ink-3);
}
.kg-node--skill circle {
  fill: var(--ok-soft, #e8f7ee);
  stroke: var(--ok, #27ae60);
}
.kg-node--skill text {
  font-size: 10px;
  fill: var(--ok, #27ae60);
}
/* 章节骨架边（包含章节）：更醒目的框架线 */
.is-chapter-frame {
  stroke: #e6b973;
  stroke-width: 1.6;
}
.kg-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: var(--r-circle, 50%);
  margin-right: 6px;
  vertical-align: middle;
}
.kg-dot--chapter {
  background: #f5b041;
  border: 1px solid #b95c00;
}
.kg-dot--concept {
  background: var(--surface-3);
  border: 1.4px solid var(--accent);
}
.kg-dot--skill {
  background: var(--ok-soft, #e8f7ee);
  border: 1.4px solid var(--ok, #27ae60);
}
.kg-node:hover circle {
  stroke-width: 2.4;
}
.kg-node text {
  font-size: 12px;
  fill: var(--ink-2);
  pointer-events: none;
}
.kg-node.is-selected circle {
  fill: var(--accent-soft);
  stroke-width: 2.6;
}
.kg-node.is-selected text {
  fill: var(--accent);
  font-weight: 600;
}
.kg-node.is-path circle {
  fill: var(--accent);
  stroke: var(--accent-strong);
  stroke-width: 2.6;
}
.kg-node.is-path text {
  fill: var(--accent-ink);
  font-weight: 600;
}
.kg-node.is-dim {
  opacity: 0.35;
}

/* ---- 右侧：详情 / 导读 ---- */
.kg-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.kg-detail-name {
  word-break: break-all;
}
.kg-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.kg-desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-2);
  background: var(--surface-2);
  border-radius: var(--r-ctl);
  padding: 10px 12px;
}
.kg-rel-groups {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.kg-rel-group h3 {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--ink-3);
  font-weight: 600;
}
.kg-rel-group ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.kg-rel-group li {
  display: flex;
}
.kg-rel-group .text-btn {
  border: 1px solid var(--line);
  border-radius: var(--r-chip);
  padding: 3px 10px;
  font-size: 12px;
  background: var(--surface);
}
.kg-rel-group .text-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.kg-actions {
  display: flex;
  gap: 8px;
}
.legend {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.legend-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--ink-2);
}
.legend-line {
  width: 64px;
  height: 10px;
  flex: none;
}
.legend-line line {
  stroke: var(--line-strong, var(--line));
  stroke-width: 1.6;
}
.legend-related line {
  stroke-dasharray: 5 4;
  stroke: var(--ink-3);
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
  font-size: 12px;
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
  font-size: 12px;
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
  font-size: 12px;
  color: var(--ink-2);
}
@media (max-width: 1100px) {
  .kg-grid {
    grid-template-columns: 1fr;
  }
}
</style>
