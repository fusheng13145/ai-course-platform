<template>
  <div class="fade-in">
    <!-- 顶部工具栏 -->
    <div class="lp-toolbar">
      <h2 class="page-title">学习路径</h2>
      <el-button type="primary" @click="handleGeneratePath" :loading="generating">
        <el-icon><MagicStick /></el-icon> AI 生成路径
      </el-button>
    </div>

    <!-- 主内容区：路径列表 + 路径详情 -->
    <div v-if="paths.length > 0" class="lp-main">
      <!-- 左侧：推荐路径选择 -->
      <div class="path-selector">
        <h3 class="section-label">我的学习路径</h3>
        <div
          v-for="(p, idx) in paths"
          :key="p.id"
          class="path-card"
          :class="{ active: selectedPathId === p.id }"
          @click="selectPath(p.id)"
        >
          <div class="path-card-header">
            <span class="path-name">{{ p.name || `学习路径 ${idx + 1}` }}</span>
            <el-tag size="small" :type="p.status === 'ACTIVE' ? 'success' : 'info'">
              {{ p.status === 'ACTIVE' ? '进行中' : (p.status === 'COMPLETED' ? '已完成' : '未开始') }}
            </el-tag>
          </div>
          <div class="path-meta">
            <span>{{ p.totalStages || pathNodes.length }} 个节点</span>
            <span>进度 {{ calculatePathProgress(p) }}%</span>
          </div>
          <el-progress
            :percentage="calculatePathProgress(p)"
            :stroke-width="6"
            :show-text="false"
            style="margin-top: 8px"
          />
        </div>
      </div>

      <!-- 右侧：当前路径详情（Timeline） -->
      <div class="path-detail">
        <!-- 路径概览卡片 -->
        <div class="overview-card" v-if="currentPath">
          <div class="overview-grid">
            <div class="overview-item">
              <span class="overview-value">{{ currentPath.name || '学习路径' }}</span>
              <span class="overview-label">当前路径</span>
            </div>
            <div class="overview-item">
              <span class="overview-value">{{ completedNodeCount }}</span>
              <span class="overview-label">已完成节点</span>
            </div>
            <div class="overview-item">
              <span class="overview-value">{{ pathNodes.length }}</span>
              <span class="overview-label">总节点数</span>
            </div>
            <div class="overview-item">
              <span class="overview-value">{{ overallProgress }}%</span>
              <span class="overview-label">整体进度</span>
            </div>
          </div>
        </div>

        <!-- Timeline 学习节点 -->
        <div class="timeline-section">
          <h3 class="section-label">学习进度</h3>
          <el-timeline v-if="pathNodes.length > 0">
            <el-timeline-item
              v-for="(node, index) in pathNodes"
              :key="node.id"
              :timestamp="node.completedAt || node.time || ''"
              placement="top"
              :size="getNodeSize(node)"
              :type="getNodeType(node)"
              :hollow="!node.completed && !node.current"
            >
              <div class="timeline-node-card" @click="handleNodeClick(node)">
                <div class="node-header">
                  <span class="node-index">步骤 {{ index + 1 }}</span>
                  <div class="node-status-tags">
                    <el-tag v-if="node.completed" type="success" size="small">已完成</el-tag>
                    <el-tag v-else-if="node.current" type="warning" size="small">
                      进行中
                      <span v-if="node.current" class="spinning-dot"></span>
                    </el-tag>
                    <el-tag v-else type="info" size="small">未开始</el-tag>
                  </div>
                </div>
                <h4 class="node-title">{{ node.title || node.description || '学习节点' }}</h4>
                <p class="node-desc">{{ node.content || node.detail || '' }}</p>

                <!-- 进度环形指示器 -->
                <div class="node-progress-row" v-if="node.progress !== undefined">
                  <span class="progress-text">完成度 {{ node.progress }}%</span>
                </div>

                <div class="node-actions">
                  <el-button
                    v-if="!node.completed"
                    type="primary"
                    size="small"
                    @click.stop="startLearningNode(node)"
                  >
                    {{ node.current ? '继续学习' : '开始学习' }}
                  </el-button>
                  <el-button
                    v-if="node.completed"
                    text
                    size="small"
                    @click.stop="reviewNode(node)"
                  >
                    复习
                  </el-button>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="该路径暂无学习节点" />
        </div>

        <!-- 每日任务面板 -->
        <div class="daily-tasks-panel" v-if="dailyTasks.length > 0">
          <h3 class="section-label">
            <el-icon><Calendar /></el-icon> 今日任务
          </h3>
          <div class="task-list">
            <div
              v-for="task in dailyTasks"
              :key="task.id"
              class="task-item"
              :class="{ done: task.completed }"
            >
              <div class="task-check" @click="toggleTask(task)">
                <el-icon v-if="task.completed"><Select /></el-icon>
              </div>
              <div class="task-content">
                <span class="task-title">{{ task.title }}</span>
                <span class="task-hint" v-if="task.knowledgePointName">{{ task.knowledgePointName }}</span>
              </div>
              <el-tag size="small" :type="task.priority === 'HIGH' ? 'danger' : task.priority === 'MEDIUM' ? 'warning' : 'info'">
                {{ task.priority === 'HIGH' ? '重要' : (task.priority === 'MEDIUM' ? '一般' : '可选') }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty-state">
      <el-empty description="暂无学习路径">
        <el-button type="primary" @click="handleGeneratePath">
          <el-icon><MagicStick /></el-icon> AI 为我生成学习路径
        </el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Calendar, Select } from '@element-plus/icons-vue'
import learningPathApi from '@/api/learningPath'

/* ---- 数据状态 ---- */
const paths = ref([])
const pathNodes = ref([])
const dailyTasks = ref([])
const selectedPathId = ref(null)
const currentPath = ref(null)
const generating = ref(false)
const loading = ref(false)

/* ---- 计算属性 ---- */
const completedNodeCount = computed(() => {
  return pathNodes.value.filter(n => n.status === 'COMPLETED' || n.completed).length
})

const overallProgress = computed(() => {
  if (pathNodes.value.length === 0) return 0
  return Math.round((completedNodeCount.value / pathNodes.value.length) * 100)
})

/* ---- 加载学习路径列表 ---- */
async function loadPaths() {
  loading.value = true
  try {
    const res = await learningPathApi.getMyPaths()
    const list = res.data || res || []
    paths.value = Array.isArray(list) ? list.map(p => ({
      id: p.id,
      name: p.pathName || p.name || '学习路径',
      totalStages: p.totalStages || 0,
      status: p.status || 'PENDING',
      progress: p.progress || 0
    })) : []

    // 自动选中第一个路径
    if (paths.value.length > 0) {
      selectPath(paths.value[0].id)
    }
  } catch (e) {
    console.error('加载学习路径失败:', e)
  } finally {
    loading.value = false
  }
}

/* ---- 选择路径并加载详情 ---- */
async function selectPath(pathId) {
  selectedPathId.value = pathId
  currentPath.value = paths.value.find(p => p.id === pathId)

  try {
    // 加载路径节点
    const nodesRes = await learningPathApi.getPathNodes(pathId)
    const nodes = nodesRes.data || nodesRes || []
    pathNodes.value = Array.isArray(nodes) ? nodes.map(n => ({
      ...n,
      title: n.title || n.description || n.knowledgePointName || '学习节点',
      content: n.content || n.detail || '',
      completed: n.status === 'COMPLETED',
      current: n.status === 'IN_PROGRESS',
      progress: n.progress ? Math.round(Number(n.progress)) : undefined
    })) : []

    // 尝试加载每日任务
    loadDailyTasks(pathId)
  } catch (e) {
    console.error('加载路径节点失败:', e)
  }
}

/* ---- 加载每日任务（增强：使用 learningPathApi） ---- */
async function loadDailyTasks(pathId) {
  try {
    const res = await learningPathApi.getDailyTasks(pathId)
    const data = res.data || res || []
    dailyTasks.value = Array.isArray(data) ? data.map(t => ({
      id: t.id,
      title: t.title || t.name || t.content || '任务',
      completed: t.completed ?? false,
      priority: t.priority || 'MEDIUM',
      knowledgePointName: t.knowledgePointName || ''
    })) : []
  } catch (e) {
    // API 可能不存在，忽略错误
    dailyTasks.value = []
  }
}

/* ---- 计算路径进度 ---- */
function calculatePathProgress(path) {
  if (!path.progress) {
    // 根据当前选中的路径节点计算
    if (selectedPathId.value === path.id && pathNodes.value.length > 0) {
      return overallProgress.value
    }
    return 0
  }
  return Math.round(Number(path.progress))
}

/* ---- 节点样式辅助方法 ---- */
function getNodeType(node) {
  if (node.completed) return 'success'
  if (node.current) return 'warning'
  return 'info'
}

function getNodeSize(node) {
  if (node.current) return 'large'
  return 'normal'
}

/* ---- 操作方法 ---- */
function handleNodeClick(node) {
  // 可以展开更多操作
}

function startLearningNode(node) {
  ElMessage.info(`开始学习：${node.title}`)
  // 实际项目中跳转到对应课程或知识点页面
}

function reviewNode(node) {
  ElMessage.info(`复习：${node.title}`)
}

function toggleTask(task) {
  task.completed = !task.completed
  ElMessage.success(task.completed ? '任务已完成' : '任务已取消')
}

/* ---- AI 生成学习路径 ---- */
async function handleGeneratePath() {
  try {
    await ElMessageBox.confirm(
      'AI 将根据你的学习情况生成个性化学习路径，确定要继续吗？',
      'AI 生成路径',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )

    generating.value = true
    // 调用生成API（需要 courseId 参数）
    const res = await learningPathApi.generatePath(null)
    ElMessage.success('学习路径已生成')
    await loadPaths()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('生成路径失败:', e)
      ElMessage.error('生成失败，请稍后重试')
    }
  } finally {
    generating.value = false
  }
}

/* ---- 初始化 ---- */
onMounted(() => {
  loadPaths()
})
</script>

<style scoped>
.lp-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-lg);
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0;
}

.lp-main {
  display: grid;
  grid-template-columns: minmax(240px, 280px) 1fr;
  gap: var(--ac-spacing-md);
  min-height: 500px;
}

/* ---- 左侧路径选择 ---- */
.path-selector {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  height: fit-content;
  max-height: calc(100vh - 180px);
  overflow-y: auto;
}

.section-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0 0 var(--ac-spacing-md);
  display: flex;
  align-items: center;
  gap: 6px;
}

.path-card {
  padding: var(--ac-spacing-md);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  cursor: pointer;
  transition: all var(--ac-transition-normal);
  margin-bottom: var(--ac-spacing-sm);
  background: var(--ac-bg);
}

.path-card:hover {
  transform: translateY(-1px);
  box-shadow: var(--ac-shadow-sm);
}

.path-card.active {
  border-color: var(--ac-primary);
  background: var(--ac-primary-bg);
}

.path-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.path-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.path-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--ac-text-muted);
}

/* ---- 右侧路径详情 ---- */
.path-detail {
  min-width: 0;
}

.overview-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  margin-bottom: var(--ac-spacing-md);
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--ac-spacing-md);
}

.overview-item {
  text-align: center;
}

.overview-value {
  display: block;
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-primary);
  line-height: 1.3;
}

.overview-label {
  font-size: 12px;
  color: var(--ac-text-muted);
}

.timeline-section {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  margin-bottom: var(--ac-spacing-md);
}

/* ---- Timeline 节点卡片 ---- */
.timeline-node-card {
  background: var(--ac-bg);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-md);
  transition: all var(--ac-transition-fast);
  cursor: default;
}

.timeline-node-card:hover {
  box-shadow: var(--ac-shadow-sm);
  border-left: 3px solid var(--ac-primary);
}

.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-xs);
}

.node-index {
  font-size: 12px;
  color: var(--ac-text-muted);
  font-weight: 500;
}

.node-status-tags {
  display: flex;
  gap: 4px;
  align-items: center;
}

.spinning-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ac-warning);
  animation: spin-pulse 1.5s infinite;
  margin-left: 4px;
}

@keyframes spin-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}

.node-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0 0 4px;
}

.node-desc {
  font-size: 13px;
  color: var(--ac-text-secondary);
  line-height: 1.6;
  margin: 0 0 var(--ac-spacing-sm);
}

.node-progress-row {
  margin-bottom: var(--ac-spacing-sm);
}

.progress-text {
  font-size: 12px;
  color: var(--ac-primary);
  font-weight: 500;
}

.node-actions {
  display: flex;
  gap: var(--ac-spacing-sm);
}

/* ---- 每日任务面板 ---- */
.daily-tasks-panel {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
}

.task-list {
  margin-top: var(--ac-spacing-sm);
}

.task-item {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  padding: 10px 12px;
  border-radius: var(--ac-radius-sm);
  border: 1px solid var(--ac-border);
  margin-bottom: 6px;
  transition: all var(--ac-transition-fast);
  background: var(--ac-bg);
}

.task-item:hover {
  background: var(--ac-bg-hover);
}

.task-item.done {
  opacity: 0.6;
}

.task-item.done .task-title {
  text-decoration: line-through;
  color: var(--ac-text-muted);
}

.task-check {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid var(--ac-border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: all var(--ac-transition-fast);
  font-size: 12px;
  color: var(--ac-success);
}

.task-check:hover {
  border-color: var(--ac-success);
  background: rgba(139,168,136,0.1);
}

.task-content {
  flex: 1;
  min-width: 0;
}

.task-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--ac-text-primary);
  display: block;
}

.task-hint {
  font-size: 11px;
  color: var(--ac-text-muted);
  display: block;
  margin-top: 2px;
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
}

@media (max-width: 900px) {
  .lp-main {
    grid-template-columns: 1fr;
  }
  .overview-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
