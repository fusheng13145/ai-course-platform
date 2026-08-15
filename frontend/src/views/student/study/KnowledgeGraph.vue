<template>
  <div class="fade-in">
    <PageHeader
      title="知识图谱"
      subtitle="可视化课程知识点关联，定位学习路径"
      icon="brain"
      icon-bg="accent"
    />

    <div class="stat-grid animate-in d1">
      <StatCard
        label="知识点总数"
        :value="nodeCount"
        icon="layers"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="已掌握"
        :value="masteredCount"
        icon="check"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="平均掌握度"
        :value="avgMasteryText"
        icon="target"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
    </div>

    <section class="l1-card animate-in d2">
      <div class="l1-head">
        <span class="l1-ico">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path
              d="m12 2 8.5 4.7L12 11.4 3.5 6.7 12 2zM3.5 12l8.5 4.7 8.5-4.7M3.5 17.3l8.5 4.7 8.5-4.7"
            />
          </svg>
        </span>
        <div>
          <div class="l1-title">知识图谱</div>
          <div class="l1-sub">点击节点查看详情，按难度与关键词筛选</div>
        </div>
      </div>
      <div class="l1-body">
        <!-- 工具栏 -->
        <div class="kg-toolbar">
          <div class="toolbar-left">
            <el-select
              v-model="selectedCourseId"
              placeholder="选择课程"
              class="course-select"
              @change="loadGraphData"
            >
              <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-input
              v-model="searchKeyword"
              placeholder="搜索知识点..."
              clearable
              :prefix-icon="Search"
              class="search-input"
              @input="filterNodes"
            />
          </div>
          <div class="toolbar-right">
            <el-radio-group v-model="difficultyFilter" size="small" @change="filterNodes">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="EASY">简单</el-radio-button>
              <el-radio-button value="MEDIUM">中等</el-radio-button>
              <el-radio-button value="HARD">困难</el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <!-- 主内容区：图谱 + 详情面板 -->
        <div class="kg-main">
          <!-- 左侧：知识图谱可视化 -->
          <div class="graph-panel">
            <div class="panel-header">
              <h3 class="panel-title">知识图谱</h3>
              <span class="node-count">{{ filteredNodeCount }} 个知识点</span>
            </div>
            <div v-loading="graphLoading" class="graph-container">
              <v-chart
                v-if="!graphLoading && graphOption.series?.[0]?.data?.length > 0"
                :option="graphOption"
                class="graph-chart"
                autoresize
                @click="handleNodeClick"
              />
              <el-empty
                v-else-if="
                  !graphLoading &&
                  (!graphOption.series?.[0]?.data || graphOption.series[0].data.length === 0)
                "
                description="请选择课程查看知识图谱"
                :image-size="100"
              >
                <template #image><div class="empty-deco">🕸️</div></template>
              </el-empty>
            </div>
            <!-- 图例说明 -->
            <div class="graph-legend">
              <span class="legend-item"><i class="legend-dot"></i> 前置关系（虚线）</span>
              <span class="legend-item"><i class="legend-dot solid"></i> 关联关系（实线）</span>
            </div>
          </div>

          <!-- 右侧：详情面板 -->
          <div class="detail-panel">
            <div class="panel-header">
              <h3 class="panel-title">知识点详情</h3>
            </div>

            <!-- 有选中节点时显示详情 -->
            <div v-if="selectedNode" class="detail-content">
              <div class="detail-name-row">
                <el-tag :type="getDifficultyTagType(selectedNode.difficultyLevel)" size="small">
                  {{ getDifficultyLabel(selectedNode.difficultyLevel) }}
                </el-tag>
                <h4 class="detail-name">{{ selectedNode.name }}</h4>
              </div>

              <p class="detail-desc">{{ selectedNode.description || '暂无描述' }}</p>

              <!-- 掌握度进度条 -->
              <div class="mastery-section">
                <div class="mastery-header">
                  <span class="mastery-label">掌握度</span>
                  <span class="mastery-value">{{ selectedNode.mastery ?? 0 }}%</span>
                </div>
                <el-progress
                  :percentage="selectedNode.mastery ?? 0"
                  :stroke-width="10"
                  :color="getMasteryColor(selectedNode.mastery)"
                  :show-text="false"
                />
              </div>

              <!-- 前置知识点 -->
              <div v-if="prerequisiteNodes.length > 0" class="relation-section">
                <h5 class="relation-title">
                  <el-icon><ArrowLeft /></el-icon> 前置知识点
                </h5>
                <div class="relation-list">
                  <span
                    v-for="node in prerequisiteNodes"
                    :key="node.id"
                    class="relation-tag"
                    @click="selectNodeById(node.id)"
                  >
                    {{ node.name }}
                  </span>
                </div>
              </div>

              <!-- 后续知识点 -->
              <div v-if="subsequentNodes.length > 0" class="relation-section">
                <h5 class="relation-title">
                  后续知识点<el-icon><ArrowRight /></el-icon>
                </h5>
                <div class="relation-list">
                  <span
                    v-for="node in subsequentNodes"
                    :key="node.id"
                    class="relation-tag subsequent"
                    @click="selectNodeById(node.id)"
                  >
                    {{ node.name }}
                  </span>
                </div>
              </div>

              <!-- 操作按钮 -->
              <div class="detail-actions">
                <el-button type="primary" size="small" @click="startLearningNode">
                  <el-icon><Reading /></el-icon> 开始学习
                </el-button>
              </div>
            </div>

            <!-- 无选中节点时显示提示 -->
            <div v-else class="empty-detail">
              <el-icon :size="40" color="var(--ac-text-muted)"><Share /></el-icon>
              <p>点击图谱中的节点<br />查看知识点详情</p>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, ArrowLeft, ArrowRight, Reading, Share } from '@element-plus/icons-vue'
import knowledgeApi from '@/api/knowledge'
import knowledgeRelationApi from '@/api/knowledgePointRelation'
import courseApi from '@/api/course'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { getChartTheme, getMonoStatus } from '@/utils/chartTheme'

const route = useRoute()
const router = useRouter()

/* ---- 数据状态 ---- */
const courseList = ref([])
const selectedCourseId = ref(null)
const searchKeyword = ref('')
const difficultyFilter = ref('')
const graphLoading = ref(false)

/* ---- 图谱数据 ---- */
const rawNodes = ref([]) // 原始节点数据
const rawLinks = ref([]) // 原始连线数据
const selectedNode = ref(null) // 当前选中的节点

/* ---- 计算属性：过滤后的节点数量 ---- */
const filteredNodeCount = computed(() => {
  return rawNodes.value.filter((n) => matchFilter(n)).length
})

/** 知识点总数 */
const nodeCount = computed(() => rawNodes.value.length)
/** 已掌握（掌握度≥60%）节点数 */
const masteredCount = computed(() => rawNodes.value.filter((n) => (n.mastery ?? 0) >= 60).length)
/** 平均掌握度 */
const avgMastery = computed(() => {
  if (!rawNodes.value.length) return 0
  const sum = rawNodes.value.reduce((s, n) => s + (n.mastery ?? 0), 0)
  return Math.round(sum / rawNodes.value.length)
})
const avgMasteryText = computed(() => avgMastery.value + '%')

/* ---- 前置/后续节点 ---- */
const prerequisiteNodes = computed(() => {
  if (!selectedNode.value) return []
  return rawLinks.value
    .filter((l) => l.target === selectedNode.value.id)
    .map((l) => rawNodes.value.find((n) => n.id === l.source))
    .filter(Boolean)
})

const subsequentNodes = computed(() => {
  if (!selectedNode.value) return []
  return rawLinks.value
    .filter((l) => l.source === selectedNode.value.id)
    .map((l) => rawNodes.value.find((n) => n.id === l.target))
    .filter(Boolean)
})

/* ---- ECharts 图表配置（setup 内按当前主题构建，随主题变化） ---- */
function createGraphOption() {
  const t = getChartTheme()
  const status = getMonoStatus()
  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: t.tooltipBg,
      borderColor: t.tooltipBorder,
      textStyle: { color: t.tooltipText, fontSize: 13 },
      formatter: (params) => {
        if (params.dataType === 'node') {
          const d = params.data
          return `<strong>${d.name}</strong><br/>难度：${getDifficultyLabel(d.difficultyLevel)}<br/>掌握度：${d.mastery ?? 0}%`
        }
        const link = params.data
        return `${link.relationType === 'PREREQUISITE' ? '前置依赖' : '关联'}`
      },
    },
    animationDurationUpdate: 500,
    animationEasingUpdate: 'quarticIn',
    series: [
      {
        type: 'graph',
        layout: 'force',
        data: [],
        links: [],
        roam: true,
        draggable: true,
        force: {
          repulsion: 300,
          edgeLength: [120, 200],
          gravity: 0.1,
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 3 },
        },
        label: {
          show: true,
          position: 'bottom',
          fontSize: 12,
          fontWeight: 500,
          color: t.text,
          formatter: '{b}',
        },
        symbolSize: (val) => {
          const importance = val?.importance || val?.[2] || 3
          return Math.max(24, Math.min(56, importance * 14))
        },
        itemStyle: {
          borderColor: t.sliceBorder,
          borderWidth: 2,
          shadowBlur: 6,
          shadowColor: 'rgba(24,32,48,0.12)',
        },
        lineStyle: {
          color: 'source',
          curveness: 0.15,
          width: 1.5,
          opacity: 0.6,
        },
        categories: [
          { name: '简单', itemStyle: { color: status.EASY } },
          { name: '中等', itemStyle: { color: status.MEDIUM } },
          { name: '困难', itemStyle: { color: status.HARD } },
        ],
      },
    ],
  }
}

const graphOption = ref(createGraphOption())

/* ---- 方法：加载课程列表 ---- */
async function loadCourseList() {
  try {
    const res = await courseApi.getCourseList()
    const list = res.data?.list || res.data || res || []
    courseList.value = Array.isArray(list) ? list : []

    // 如果路由中有课程ID，自动选中
    const routeCourseId = route.query.courseId || route.params.courseId
    if (routeCourseId) {
      selectedCourseId.value = Number(routeCourseId)
      await loadGraphData()
    } else if (courseList.value.length > 0) {
      selectedCourseId.value = courseList.value[0].id
      await loadGraphData()
    }
  } catch (e) {
    console.error('加载课程列表失败:', e)
  }
}

/* ---- 方法：加载图谱数据（增强：优先使用 knowledgeRelationApi） ---- */
async function loadGraphData() {
  if (!selectedCourseId.value) return
  graphLoading.value = true
  try {
    // 优先使用知识关联 API 获取图谱数据
    const res = await knowledgeRelationApi.getGraphData(selectedCourseId.value)
    const data = res.data || res || {}

    // 解析节点
    const nodes = Array.isArray(data.nodes)
      ? data.nodes
      : Array.isArray(data.points)
        ? data.points
        : []
    rawNodes.value = nodes.map((n) => ({
      id: n.id,
      name: n.name || n.title || '未知',
      description: n.description || '',
      difficultyLevel: n.difficultyLevel || n.difficulty || 'MEDIUM',
      mastery: n.mastery ?? n.masteredPercent ?? 0,
      importance: n.importance ?? n.weight ?? 3,
      categoryIndex: getCategoryIndex(n.difficultyLevel || n.difficulty || 'MEDIUM'),
    }))

    // 解析连线
    const links = Array.isArray(data.links)
      ? data.links
      : Array.isArray(data.relations)
        ? data.relations
        : []
    rawLinks.value = links.map((l) => ({
      source: l.source || l.fromId,
      target: l.target || l.toId,
      relationType: l.relationType || l.type || 'RELATED',
      lineStyle: getLineStyle(l.relationType || l.type || 'RELATED'),
    }))

    updateChartOption()
  } catch (e) {
    console.error('加载知识图谱失败:', e)
    // 回退到基础 knowledgeApi
    try {
      const fallbackRes = await knowledgeApi.getKnowledgeGraph(selectedCourseId.value)
      const fData = fallbackRes.data || fallbackRes || {}
      if (fData.nodes || fData.points) {
        const fnodes = Array.isArray(fData.nodes)
          ? fData.nodes
          : Array.isArray(fData.points)
            ? fData.points
            : []
        rawNodes.value = fnodes.map((n) => ({
          id: n.id,
          name: n.name || n.title || '未知',
          description: n.description || '',
          difficultyLevel: n.difficultyLevel || n.difficulty || 'MEDIUM',
          mastery: n.mastery ?? 0,
          importance: 3,
          categoryIndex: getCategoryIndex('MEDIUM'),
        }))
        const flinks = Array.isArray(fData.links) ? fData.links : []
        rawLinks.value = flinks.map((l) => ({
          source: l.source,
          target: l.target,
          relationType: 'RELATED',
          lineStyle: getLineStyle('RELATED'),
        }))
        updateChartOption()
      } else {
        rawNodes.value = []
        rawLinks.value = []
      }
    } catch (e2) {
      rawNodes.value = []
      rawLinks.value = []
    }
  } finally {
    graphLoading.value = false
  }
}

/* ---- 方法：更新图表配置 ---- */
function updateChartOption() {
  const filtered = rawNodes.value.filter((n) => matchFilter(n))
  const nodeIdSet = new Set(filtered.map((n) => n.id))

  // 过滤连线，只保留两端都在过滤结果中的
  const filteredLinks = rawLinks.value.filter(
    (l) => nodeIdSet.has(l.source) && nodeIdSet.has(l.target),
  )

  graphOption.value.series[0].data = filtered.map((n) => ({
    ...n,
    itemStyle: { color: getDifficultyColor(n.difficultyLevel) },
    category: getCategoryIndex(n.difficultyLevel),
  }))
  graphOption.value.series[0].links = filteredLinks
}

/* ---- 方法：过滤逻辑 ---- */
function matchFilter(node) {
  // 难度筛选
  if (difficultyFilter.value && node.difficultyLevel !== difficultyFilter.value) return false
  // 搜索关键词
  if (searchKeyword.value && !node.name.toLowerCase().includes(searchKeyword.value.toLowerCase()))
    return false
  return true
}

function filterNodes() {
  updateChartOption()
}

/* ---- 通过ID选择节点 ---- */
function selectNodeById(nodeId) {
  const node = rawNodes.value.find((n) => n.id === nodeId)
  if (node) {
    selectedNode.value = { ...node }
  }
}

/* ---- 方法：开始学习该知识点 ---- */
function startLearningNode() {
  if (!selectedNode.value || !selectedCourseId.value) return
  router.push(`/student/courses/${selectedCourseId.value}`)
  ElMessage.info(`正在跳转到「${selectedNode.value.name}」的学习页面`)
}

/* ---- 辅助方法 ---- */

function getDifficultyColor(level) {
  const status = getMonoStatus()
  return status[level] || status.MEDIUM
}

function getDifficultyLabel(level) {
  const map = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
  return map[level] || '未知'
}

function getDifficultyTagType(level) {
  const map = { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }
  return map[level] || 'info'
}

function getCategoryIndex(level) {
  const map = { EASY: 0, MEDIUM: 1, HARD: 2 }
  return map[level] ?? 1
}

function getMasteryColor(mastery) {
  if (mastery >= 80) return 'var(--ac-success)'
  if (mastery >= 50) return 'var(--ac-primary)'
  if (mastery >= 30) return 'var(--ac-warning)'
  return 'var(--ac-danger)'
}

function getLineStyle(relationType) {
  const status = getMonoStatus()
  if (relationType === 'PREREQUISITE') {
    return { type: 'dashed', color: status.MEDIUM, opacity: 0.5 }
  }
  return { type: 'solid', color: status.WARNING, opacity: 0.5 }
}

/* ---- 节点点击事件（增强：加载真实掌握度） ---- */
async function handleNodeClick(params) {
  if (params.dataType !== 'node') return
  const clickedNode = rawNodes.value.find((n) => n.id === params.data.id)
  if (clickedNode) {
    selectedNode.value = { ...clickedNode }
    // 尝试从 API 获取该知识点的掌握度
    try {
      const res = await knowledgeApi.getMastery(clickedNode.id, undefined)
      const mData = res.data || res
      if (mData != null && typeof mData === 'number') {
        selectedNode.value.mastery = Math.round(mData)
      } else if (mData?.masteryPercent != null) {
        selectedNode.value.mastery = Math.round(mData.masteryPercent)
      }
      // 更新原始节点中的掌握度
      const origIdx = rawNodes.value.findIndex((n) => n.id === clickedNode.id)
      if (origIdx >= 0) {
        rawNodes.value[origIdx].mastery = selectedNode.value.mastery
      }
    } catch (e) {
      // 掌握度 API 不可用时使用图谱中已有的值
    }
  }
}

/* ---- 初始化 ---- */
onMounted(() => {
  loadCourseList()
})
</script>

<style scoped>
.kg-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-md);
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
}

.course-select {
  width: 220px;
}
.search-input {
  width: 200px;
}
.graph-chart {
  width: 100%;
  height: 520px;
}

.kg-main {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: var(--ac-spacing-md);
  min-height: 600px;
}

.graph-panel,
.detail-panel {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  overflow: hidden;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--ac-spacing-md) var(--ac-spacing-lg);
  border-bottom: 1px solid var(--ac-border);
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}

.node-count {
  font-size: 12px;
  color: var(--ac-text-muted);
  background: var(--ac-bg);
  padding: 2px 10px;
  border-radius: 999px;
}

.graph-container {
  padding: var(--ac-spacing-md);
  min-height: 520px;
}

.graph-legend {
  display: flex;
  justify-content: center;
  gap: var(--ac-spacing-lg);
  padding: var(--ac-spacing-sm) var(--ac-spacing-lg) var(--ac-spacing-md);
  border-top: 1px solid var(--ac-border);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ac-text-muted);
}

.legend-dot {
  display: inline-block;
  width: 16px;
  height: 3px;
  border-radius: 2px;
  background: var(--info);
}

.legend-dot.solid {
  background: var(--warn);
}

/* ---- 详情面板 ---- */
.detail-content {
  padding: var(--ac-spacing-lg);
}

.detail-name-row {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  margin-bottom: var(--ac-spacing-sm);
}

.detail-name {
  font-size: 17px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0;
}

.detail-desc {
  font-size: 13px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  margin-bottom: var(--ac-spacing-md);
}

.mastery-section {
  margin-bottom: var(--ac-spacing-lg);
}

.mastery-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.mastery-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--ac-text-secondary);
}

.mastery-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-primary);
}

.relation-section {
  margin-bottom: var(--ac-spacing-md);
}

.relation-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ac-text-secondary);
  margin: 0 0 var(--ac-spacing-sm);
  display: flex;
  align-items: center;
  gap: 4px;
}

.relation-title .el-icon {
  font-size: 14px;
}

.relation-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.relation-tag {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  background: var(--ac-primary-bg);
  color: var(--ac-primary-dark);
  cursor: pointer;
  transition: all var(--ac-transition-fast);
  border: 1px solid transparent;
}

.relation-tag:hover {
  background: var(--ac-primary-light);
  color: #fff;
  border-color: var(--ac-primary-light);
}

.relation-tag.subsequent {
  background: var(--violet-soft);
  color: var(--violet);
}

.relation-tag.subsequent:hover {
  background: var(--violet);
  color: #fff;
}

.detail-actions {
  margin-top: var(--ac-spacing-lg);
  padding-top: var(--ac-spacing-md);
  border-top: 1px solid var(--ac-border);
}

.empty-detail {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  color: var(--ac-text-muted);
  text-align: center;
}

.empty-detail p {
  margin-top: var(--ac-spacing-sm);
  font-size: 14px;
  line-height: 1.6;
}

.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

@media (max-width: 1024px) {
  .kg-main {
    grid-template-columns: 1fr;
  }
  .detail-panel {
    min-height: 300px;
  }
}
</style>
