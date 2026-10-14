<template>
  <div class="fade-in plagiarism-check-manage">
    <PageHeader
      title="查重管理"
      subtitle="创建、执行与查看作业查重任务"
      icon="shield"
      icon-bg="accent"
    >
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon>创建查重任务
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard label="任务总数" :value="tasksError ? '--' : taskTotal" icon="list" tone="accent" />
      <StatCard label="执行中" :value="tasksError ? '--' : runningCount" icon="play" tone="warn" />
      <StatCard label="已完成" :value="tasksError ? '--' : completedCount" icon="check" tone="ok" />
      <StatCard label="待执行" :value="tasksError ? '--' : pendingCount" icon="clock" tone="info" />
    </div>

    <!-- 加载失败提示：见 loadError 注释（失败不得静默渲染成「暂无查重任务」与 0 值读数） -->
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: var(--ac-spacing-md)"
    />

    <!-- 查重任务列表 -->
    <PcTaskTable
      :loading="loadingTasks"
      :task-list="taskList"
      :page="taskPage"
      :empty-text="tasksError ? '查重任务未能加载，请查看上方提示后重试' : '暂无查重任务'"
      @execute="handleExecute"
      @view-detail="handleViewDetail"
      @page-change="onTaskPageChange"
      @size-change="onTaskSizeChange"
    />

    <!-- 查重匹配详情 -->
    <PcMatchTable
      :selected-task="selectedTask"
      :loading="loadingMatches"
      :match-list="matchList"
      :page="matchPage"
      :empty-text="matchesError ? '匹配详情未能加载，请查看上方提示后重试' : '暂无匹配记录'"
      @page-change="onMatchPageChange"
      @size-change="onMatchSizeChange"
    />

    <!-- 创建查重任务对话框 -->
    <PcCreateDialog v-model="showCreateDialog" @created="fetchTaskList" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import plagiarismApi from '@/api/plagiarismCheck'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import PcTaskTable from './components/PcTaskTable.vue'
import PcMatchTable from './components/PcMatchTable.vue'
import PcCreateDialog from './components/PcCreateDialog.vue'

// ========== 状态 ==========
const loadingTasks = ref(false)
const loadingMatches = ref(false)
const showCreateDialog = ref(false)

const taskList = ref([])
const matchList = ref([])
const selectedTask = ref(null)

/**
 * 两路独立加载错误（失败可见）：
 * - tasksError：查重任务列表加载失败（原实现仅 console.error 后静默清空，
 *   统计卡谎报 0、表格渲染成「暂无查重任务」，管理员会误判平台无任何任务）；
 * - matchesError：匹配详情加载失败（同上，表格静默变空）。
 * 两路互不复位，由 loadError 聚合展示，避免一路成功清掉另一路的失败提示。
 */
const tasksError = ref('')
const matchesError = ref('')
const loadError = computed(() =>
  [tasksError.value, matchesError.value].filter(Boolean).join('；'),
)

// 分页
const taskPage = reactive({ page: 1, size: 10, total: 0 })
const matchPage = reactive({ page: 1, size: 10, total: 0 })

// 统计概览
const taskTotal = computed(() => taskList.value.length)
const runningCount = computed(() => taskList.value.filter((r) => r.status === 'RUNNING').length)
const completedCount = computed(() => taskList.value.filter((r) => r.status === 'COMPLETED').length)
const pendingCount = computed(() => taskList.value.filter((r) => r.status === 'PENDING').length)

// ========== API 方法 ==========

async function fetchTaskList() {
  loadingTasks.value = true
  tasksError.value = ''
  try {
    const res = await plagiarismApi.getCheckHistory('')
    const rawData = res.data?.records || res.data?.list || res.data || res || []
    const list = Array.isArray(rawData) ? rawData : []
    taskList.value = list
    taskPage.total = rawData?.total ?? rawData?.count ?? list.length
  } catch (error) {
    console.error('获取查重任务列表失败:', error)
    taskList.value = []
    taskPage.total = 0
    tasksError.value = '查重任务加载失败，请检查网络后重试'
  } finally {
    loadingTasks.value = false
  }
}

async function fetchMatchList() {
  if (!selectedTask.value) return
  loadingMatches.value = true
  matchesError.value = ''
  try {
    const res = await plagiarismApi.getCheckMatches(selectedTask.value.id)
    const rawData = res.data?.records || res.data?.list || res.data || res || []
    const list = Array.isArray(rawData) ? rawData : []
    matchList.value = list
    matchPage.total = rawData?.total ?? rawData?.count ?? list.length
  } catch (error) {
    console.error('获取匹配列表失败:', error)
    matchList.value = []
    matchPage.total = 0
    matchesError.value = '查重匹配详情加载失败，请检查网络后重试'
  } finally {
    loadingMatches.value = false
  }
}

async function handleExecute(row) {
  try {
    await ElMessageBox.confirm(`确定对任务 #${row.id} 执行查重？`, '确认操作', {
      confirmButtonText: '确定执行',
      cancelButtonText: '取消',
      type: 'info',
    })
    await plagiarismApi.executeCheck(row.id)
    ElMessage.success('查重任务已开始执行')
    await fetchTaskList()
    if (selectedTask.value?.id === row.id) {
      await fetchMatchList()
    }
  } catch (e) {
    if (e !== 'cancel') {
      console.error('执行查重失败:', e)
      ElMessage.error('操作失败')
    }
  }
}

async function handleViewDetail(row) {
  selectedTask.value = row
  matchPage.page = 1
  await fetchMatchList()
}

function onTaskPageChange(page) {
  taskPage.page = page
  fetchTaskList()
}

function onTaskSizeChange(size) {
  taskPage.size = size
  taskPage.page = 1
  fetchTaskList()
}

function onMatchPageChange(page) {
  matchPage.page = page
  fetchMatchList()
}

function onMatchSizeChange(size) {
  matchPage.size = size
  matchPage.page = 1
  fetchMatchList()
}

// ========== 初始化 ==========
onMounted(() => {
  fetchTaskList()
})
</script>

<style scoped>
.plagiarism-check-manage {
  max-width: 1400px;
  margin: 0 auto;
}
</style>
