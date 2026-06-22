<template>
  <div class="fade-in">
    <!-- 工具栏 -->
    <div class="kg-toolbar">
      <div class="toolbar-left">
        <el-select v-model="selectedCourseId" placeholder="选择课程" style="width: 220px" @change="loadGraphData">
          <el-option
            v-for="c in courseList"
            :key="c.id"
            :label="c.name"
            :value="c.id"
          />
        </el-select>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索知识点..."
          clearable
          :prefix-icon="Search"
          style="width: 200px"
          @input="filterNodes"
        />
      </div>
      <div class="toolbar-right">
        <el-radio-group v-model="layoutMode" size="small" @change="onLayoutChange">
          <el-radio-button value="force">力导向</el-radio-button>
          <el-radio-button value="circular">环形</el-radio-button>
        </el-radio-group>
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
          <div class="panel-actions">
            <el-tooltip content="聚焦模式" placement="bottom">
              <el-button
                :type="focusMode ? 'warning' : 'default'"
                size="small"
                circle
                @click="toggleFocusMode"
              >
                <el-icon><Aim /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="缩小" placement="bottom">
              <el-button size="small" circle @click="zoomOut">
                <el-icon><ZoomOut /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="重置" placement="bottom">
              <el-button size="small" circle @click="zoomReset">
                <el-icon><RefreshRight /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="放大" placement="bottom">
              <el-button size="small" circle @click="zoomIn">
                <el-icon><ZoomIn /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip :content="isFullscreen ? '退出全屏' : '全屏'" placement="bottom">
              <el-button size="small" circle @click="toggleFullscreen">
                <el-icon><FullScreen /></el-icon>
              </el-button>
            </el-tooltip>
            <span class="node-count">{{ filteredNodeCount }} 个知识点</span>
          </div>
        </div>
        <div class="graph-container" v-loading="graphLoading">
          <v-chart
            v-if="!graphLoading && graphOption.series?.[0]?.data?.length > 0"
            :option="graphOption"
            style="width: 100%; height: 520px"
            autoresize
            @click="handleNodeClick"
          />
          <el-empty
            v-else-if="!graphLoading && (!graphOption.series?.[0]?.data || graphOption.series[0].data.length === 0)"
            description="请选择课程查看知识图谱"
            :image-size="100"
          />
        </div>
        <!-- 图例说明 -->
        <div class="graph-legend">
          <span class="legend-item"><i class="legend-dot" style="background: #7A8BA5"></i> 前置关系（虚线）</span>
          <span class="legend-item"><i class="legend-dot solid" style="background: #B89A8A"></i> 关联关系（实线）</span>
          <span class="legend-item"><i class="legend-dot solid" style="background: #8BA888"></i> 包含关系</span>
          <span class="legend-item"><i class="legend-dot solid" style="background: #C48080"></i> 等价关系</span>
          <span class="legend-item" v-if="focusMode" style="color: var(--ac-warning); font-weight: 600">
            <el-icon style="font-size:12px"><WarningFilled /></el-icon> 聚焦模式已开启
          </span>
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
          <div class="relation-section" v-if="prerequisiteNodes.length > 0">
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
          <div class="relation-section" v-if="subsequentNodes.length > 0">
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

          <!-- 前置依赖链 -->
          <div class="relation-section" v-if="prerequisiteChain.length > 0">
            <h5 class="relation-title">
              <el-icon><Connection /></el-icon> 前置依赖链
            </h5>
            <div class="chain-container">
              <span
                v-for="(node, idx) in prerequisiteChain"
                :key="node.id"
                class="chain-node"
                :class="{ 'chain-current': idx === prerequisiteChain.length - 1 }"
                @click="selectNodeById(node.id)"
              >
                {{ node.name }}
                <el-icon v-if="idx < prerequisiteChain.length - 1" class="chain-arrow"><ArrowRight /></el-icon>
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
          <p>点击图谱中的节点<br/>查看知识点详情</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, ArrowLeft, ArrowRight, Reading, Share, Aim, ZoomOut, ZoomIn, RefreshRight, FullScreen, Connection, WarningFilled } from '@element-plus/icons-vue'
import knowledgeApi from '@/api/knowledge'
import knowledgeRelationApi from '@/api/knowledgePointRelation'
import courseApi from '@/api/course'

const route = useRoute()
const router = useRouter()

/* ---- 数据状态 ---- */
const courseList = ref([])
const selectedCourseId = ref(null)
const searchKeyword = ref('')
const difficultyFilter = ref('')
const graphLoading = ref(false)

/* ---- 图谱数据 ---- */
const rawNodes = ref([])       // 原始节点数据
const rawLinks = ref([])       // 原始连线数据
const selectedNode = ref(null) // 当前选中的节点
const layoutMode = ref('force')       // 布局模式：force / circular
const focusMode = ref(false)          // 聚焦模式
const isFullscreen = ref(false)       // 全屏状态
const zoomLevel = ref(1)              // 当前缩放级别
const prerequisiteChain = ref([])     // 前置依赖链

/* ---- 莫兰迪色系 ---- */
const morandiColors = {
  EASY: '#8BA888',     // 鼠尾草绿 - 简单
  MEDIUM: '#7A8BA5',   // 蓝灰 - 中等
  HARD: '#C48080'      // 灰玫瑰红 - 困难
}

/* ---- 计算属性：过滤后的节点数量 ---- */
const filteredNodeCount = computed(() => {
  return rawNodes.value.filter(n => matchFilter(n)).length
})

/* ---- 前置/后续节点 ---- */
const prerequisiteNodes = computed(() => {
  if (!selectedNode.value) return []
  return rawLinks.value
    .filter(l => l.target === selectedNode.value.id)
    .map(l => rawNodes.value.find(n => n.id === l.source))
    .filter(Boolean)
})

const subsequentNodes = computed(() => {
  if (!selectedNode.value) return []
  return rawLinks.value
    .filter(l => l.source === selectedNode.value.id)
    .map(l => rawNodes.value.find(n => n.id === l.target))
    .filter(Boolean)
})

/* ---- ECharts 图表配置 ---- */
const graphOption = ref({
  tooltip: {
    trigger: 'item',
    backgroundColor: '#FFFCF8',
    borderColor: '#DDD6CE',
    textStyle: { color: '#3D3835', fontSize: 13 },
    formatter: (params) => {
      if (params.dataType === 'node') {
        const d = params.data
        return `<strong>${d.name}</strong><br/>难度：${getDifficultyLabel(d.difficultyLevel)}<br/>掌握度：${d.mastery ?? 0}%`
      }
      const link = params.data
      return `${link.relationType === 'PREREQUISITE' ? '前置依赖' : '关联'}`
    }
  },
  animationDurationUpdate: 500,
  animationEasingUpdate: 'quarticIn',
  series: [{
    type: 'graph',
    layout: 'force',
    data: [],
    links: [],
    roam: true,
    draggable: true,
    force: {
      repulsion: 300,
      edgeLength: [120, 200],
      gravity: 0.1
    },
    emphasis: {
      focus: 'adjacency',
      lineStyle: { width: 3 },
      label: { fontWeight: 'bold', fontSize: 14 }
    },
    blur: {
      itemStyle: { opacity: 0.15 },
      lineStyle: { opacity: 0.1 },
      label: { opacity: 0.2 }
    },
    label: {
      show: true,
      position: 'bottom',
      fontSize: 12,
      fontWeight: 500,
      color: '#3D3835',
      formatter: '{b}'
    },
    edgeLabel: {
      show: true,
      fontSize: 10,
      color: '#999',
      formatter: (params) => {
        return params.data?.relationType === 'PREREQUISITE' ? '前置' : '关联'
      }
    },
    symbolSize: (val) => {
      const importance = val?.importance || val?.[2] || 3
      return Math.max(24, Math.min(56, importance * 14))
    },
    itemStyle: {
      borderColor: '#fff',
      borderWidth: 2,
      shadowBlur: 6,
      shadowColor: 'rgba(61,56,53,0.12)'
    },
    lineStyle: {
      color: 'source',
      curveness: 0.15,
      width: 1.5,
      opacity: 0.6
    },
    categories: [
      { name: '简单', itemStyle: { color: morandiColors.EASY } },
      { name: '中等', itemStyle: { color: morandiColors.MEDIUM } },
      { name: '困难', itemStyle: { color: morandiColors.HARD } }
    ]
  }]
})

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
    const nodes = Array.isArray(data.nodes) ? data.nodes :
                  (Array.isArray(data.points) ? data.points : [])
    rawNodes.value = nodes.map(n => ({
      id: n.id,
      name: n.name || n.title || '未知',
      description: n.description || '',
      difficultyLevel: n.difficultyLevel || n.difficulty || 'MEDIUM',
      mastery: n.mastery ?? n.masteredPercent ?? 0,
      importance: n.importance ?? n.weight ?? 3,
      categoryIndex: getCategoryIndex(n.difficultyLevel || n.difficulty || 'MEDIUM')
    }))

    // 解析连线
    const links = Array.isArray(data.links) ? data.links :
                   (Array.isArray(data.relations) ? data.relations : [])
    rawLinks.value = links.map(l => ({
      source: l.source || l.fromId,
      target: l.target || l.toId,
      relationType: l.relationType || l.type || 'RELATED',
      lineStyle: getLineStyle(l.relationType || l.type || 'RELATED')
    }))

    updateChartOption()
  } catch (e) {
    console.error('加载知识图谱失败:', e)
    // 回退到基础 knowledgeApi
    try {
      const fallbackRes = await knowledgeApi.getKnowledgeGraph(selectedCourseId.value)
      const fData = fallbackRes.data || fallbackRes || {}
      if (fData.nodes || fData.points) {
        const fnodes = Array.isArray(fData.nodes) ? fData.nodes : (Array.isArray(fData.points) ? fData.points : [])
        rawNodes.value = fnodes.map(n => ({
          id: n.id, name: n.name || n.title || '未知', description: n.description || '',
          difficultyLevel: n.difficultyLevel || n.difficulty || 'MEDIUM',
          mastery: n.mastery ?? 0, importance: 3, categoryIndex: getCategoryIndex('MEDIUM')
        }))
        const flinks = Array.isArray(fData.links) ? fData.links : []
        rawLinks.value = flinks.map(l => ({ source: l.source, target: l.target, relationType: 'RELATED', lineStyle: getLineStyle('RELATED') }))
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
  const filtered = rawNodes.value.filter(n => matchFilter(n))
  const nodeIdSet = new Set(filtered.map(n => n.id))

  // 过滤连线，只保留两端都在过滤结果中的
  const filteredLinks = rawLinks.value.filter(
    l => nodeIdSet.has(l.source) && nodeIdSet.has(l.target)
  )

  // 计算度中心性（连接数）
  const degreeMap = new Map()
  filteredLinks.forEach(l => {
    degreeMap.set(l.source, (degreeMap.get(l.source) || 0) + 1)
    degreeMap.set(l.target, (degreeMap.get(l.target) || 0) + 1)
  })

  graphOption.value.series[0].layout = layoutMode.value
  if (layoutMode.value === 'circular') {
    graphOption.value.series[0].force = null
    graphOption.value.series[0].roam = true
  } else {
    graphOption.value.series[0].force = {
      repulsion: 300,
      edgeLength: [120, 200],
      gravity: 0.1
    }
  }

  graphOption.value.series[0].data = filtered.map(n => {
    const degree = degreeMap.get(n.id) || 0
    const baseSize = Math.max(24, Math.min(56, (n.importance || 3) * 14))
    const degreeSize = Math.min(baseSize + degree * 6, 64)
    return {
      ...n,
      symbolSize: degreeSize,
      itemStyle: {
        color: getDifficultyColor(n.difficultyLevel),
        borderColor: '#fff',
        borderWidth: 2,
        shadowBlur: 6,
        shadowColor: 'rgba(61,56,53,0.12)'
      },
      category: getCategoryIndex(n.difficultyLevel)
    }
  })
  graphOption.value.series[0].links = filteredLinks.map(l => {
    const lineStyle = getLineStyle(l.relationType)
    return {
      ...l,
      lineStyle
    }
  })

  // 聚焦模式：如果选中了节点，高亮其邻接节点
  if (focusMode.value && selectedNode.value) {
    const focusId = selectedNode.value.id
    const adjacentIds = new Set()
    filteredLinks.forEach(l => {
      if (l.source === focusId) adjacentIds.add(l.target)
      if (l.target === focusId) adjacentIds.add(l.source)
    })
    graphOption.value.series[0].data = graphOption.value.series[0].data.map(n => {
      if (n.id === focusId) {
        return { ...n, itemStyle: { ...n.itemStyle, shadowBlur: 20, shadowColor: 'rgba(122,139,165,0.5)', borderWidth: 3 } }
      }
      if (adjacentIds.has(n.id)) {
        return { ...n, itemStyle: { ...n.itemStyle, opacity: 0.9 } }
      }
      return { ...n, itemStyle: { ...n.itemStyle, opacity: 0.2 } }
    })
    graphOption.value.series[0].links = graphOption.value.series[0].links.map(l => {
      if (l.source === focusId || l.target === focusId) {
        return { ...l, lineStyle: { ...l.lineStyle, opacity: 0.9, width: 2.5 } }
      }
      return { ...l, lineStyle: { ...l.lineStyle, opacity: 0.08 } }
    })
  }
}

/* ---- 方法：过滤逻辑 ---- */
function matchFilter(node) {
  // 难度筛选
  if (difficultyFilter.value && node.difficultyLevel !== difficultyFilter.value) return false
  // 搜索关键词
  if (searchKeyword.value && !node.name.toLowerCase().includes(searchKeyword.value.toLowerCase())) return false
  return true
}

function filterNodes() {
  updateChartOption()
}

/* ---- 通过ID选择节点 ---- */
function selectNodeById(nodeId) {
  const node = rawNodes.value.find(n => n.id === nodeId)
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
  return morandiColors[level] || morandiColors.MEDIUM
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
  if (relationType === 'PREREQUISITE') {
    return { type: 'dashed', color: '#7A8BA5', opacity: 0.5 }
  }
  return { type: 'solid', color: '#B89A8A', opacity: 0.5 }
}

/* ---- 空数据（API不可用时不再使用硬编码模拟数据） ---- */
function loadDemoData() {
  rawNodes.value = []
  rawLinks.value = []
  updateChartOption()
}

/* ---- 节点点击事件（增强：加载真实掌握度，计算前置依赖链） ---- */
async function handleNodeClick(params) {
  if (params.dataType !== 'node') return
  const clickedNode = rawNodes.value.find(n => n.id === params.data.id)
  if (clickedNode) {
    selectedNode.value = { ...clickedNode }
    // 计算前置依赖链（从根节点到当前节点的路径）
    computePrerequisiteChain(clickedNode.id)
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
      const origIdx = rawNodes.value.findIndex(n => n.id === clickedNode.id)
      if (origIdx >= 0) {
        rawNodes.value[origIdx].mastery = selectedNode.value.mastery
      }
    } catch (e) {
      // 掌握度 API 不可用时使用图谱中已有的值
    }
    // 聚焦模式下重新渲染
    if (focusMode.value) {
      updateChartOption()
    }
  }
}

/* ---- 计算前置依赖链（BFS回溯） ---- */
function computePrerequisiteChain(nodeId) {
  const chain = []
  const visited = new Set()

  // 构建反向邻接表（target -> sources）
  const reverseAdj = new Map()
  rawLinks.value.forEach(l => {
    if (!reverseAdj.has(l.target)) reverseAdj.set(l.target, [])
    reverseAdj.get(l.target).push(l.source)
  })

  // BFS向上追溯
  const queue = [nodeId]
  visited.add(nodeId)
  while (queue.length > 0) {
    const current = queue.shift()
    const sources = reverseAdj.get(current) || []
    for (const src of sources) {
      if (!visited.has(src)) {
        visited.add(src)
        queue.push(src)
      }
    }
  }

  // 从根节点开始排序
  const allVisited = [...visited]
  const ordered = []
  const inDegree = new Map()
  allVisited.forEach(id => {
    inDegree.set(id, 0)
  })
  allVisited.forEach(id => {
    const sources = reverseAdj.get(id) || []
    sources.forEach(src => {
      if (inDegree.has(src)) {
        inDegree.set(id, (inDegree.get(id) || 0) + 1)
      }
    })
  })
  // 拓扑排序
  const topoQueue = []
  allVisited.forEach(id => {
    if ((inDegree.get(id) || 0) === 0) topoQueue.push(id)
  })
  while (topoQueue.length > 0) {
    const current = topoQueue.shift()
    ordered.push(current)
    const sources = reverseAdj.get(current) || []
    sources.forEach(src => {
      if (inDegree.has(src)) {
        inDegree.set(src, (inDegree.get(src) || 0) - 1)
        if (inDegree.get(src) === 0) topoQueue.push(src)
      }
    })
  }

  // 映射为节点对象
  prerequisiteChain.value = ordered
    .filter(id => id !== nodeId)
    .map(id => rawNodes.value.find(n => n.id === id))
    .filter(Boolean)
    .concat([rawNodes.value.find(n => n.id === nodeId)].filter(Boolean))
}

/* ---- 布局切换 ---- */
function onLayoutChange() {
  updateChartOption()
}

/* ---- 聚焦模式切换 ---- */
function toggleFocusMode() {
  focusMode.value = !focusMode.value
  if (!focusMode.value) {
    // 退出聚焦模式，恢复所有节点
    updateChartOption()
  } else if (selectedNode.value) {
    updateChartOption()
  }
}

/* ---- 缩放控制 ---- */
function zoomIn() {
  zoomLevel.value = Math.min(zoomLevel.value + 0.2, 3)
  graphOption.value.series[0].zoom = zoomLevel.value
}

function zoomOut() {
  zoomLevel.value = Math.max(zoomLevel.value - 0.2, 0.3)
  graphOption.value.series[0].zoom = zoomLevel.value
}

function zoomReset() {
  zoomLevel.value = 1
  graphOption.value.series[0].zoom = 1
}

/* ---- 全屏切换 ---- */
function toggleFullscreen() {
  const graphPanel = document.querySelector('.graph-panel')
  if (!graphPanel) return
  if (!isFullscreen.value) {
    if (graphPanel.requestFullscreen) {
      graphPanel.requestFullscreen()
    }
    isFullscreen.value = true
  } else {
    if (document.exitFullscreen) {
      document.exitFullscreen()
    }
    isFullscreen.value = false
  }
  // 监听全屏变化
  document.addEventListener('fullscreenchange', () => {
    isFullscreen.value = !!document.fullscreenElement
  }, { once: true })
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

.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
}

.kg-main {
  display: grid;
  grid-template-columns: 1fr minmax(300px, 360px);
  gap: var(--ac-spacing-md);
  min-height: 600px;
}

.graph-panel, .detail-panel {
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

.panel-actions {
  display: flex;
  align-items: center;
  gap: 4px;
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
  background: var(--ac-primary);
}

.legend-dot.solid {
  height: 3px;
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
  background: rgba(184,154,138,0.12);
  color: #9A8070;
}

.relation-tag.subsequent:hover {
  background: var(--ac-secondary);
  color: #fff;
}

.chain-container {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.chain-node {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  background: rgba(122,139,165,0.08);
  color: var(--ac-text-secondary);
  cursor: pointer;
  transition: all var(--ac-transition-fast);
  white-space: nowrap;
}

.chain-node:hover {
  background: var(--ac-primary-light);
  color: #fff;
}

.chain-node.chain-current {
  background: var(--ac-primary-bg);
  color: var(--ac-primary-dark);
  font-weight: 600;
}

.chain-arrow {
  font-size: 10px;
  color: var(--ac-text-muted);
  margin: 0 2px;
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

@media (max-width: 1024px) {
  .kg-main {
    grid-template-columns: 1fr;
  }
  .detail-panel {
    min-height: 300px;
  }
}
</style>
