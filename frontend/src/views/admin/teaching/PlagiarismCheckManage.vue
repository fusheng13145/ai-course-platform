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
      <StatCard label="任务总数" :value="taskTotal" icon="list" tone="accent" />
      <StatCard label="执行中" :value="runningCount" icon="play" tone="warn" />
      <StatCard label="已完成" :value="completedCount" icon="check" tone="ok" />
      <StatCard label="待执行" :value="pendingCount" icon="clock" tone="info" />
    </div>

    <!-- 上半部分：查重任务列表 -->
    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico"
          ><svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /></svg
        ></span>
        <div>
          <h3 class="l1-title">查重任务与匹配</h3>
        </div>
      </div>
      <div class="l1-body">
        <div class="section-header">
          <h3 class="section-title">查重任务</h3>
        </div>

        <div class="content-card">
          <el-table v-loading="loadingTasks" :data="taskList" style="width: 100%" stripe>
            <el-table-column prop="id" label="任务ID" width="90" />
            <el-table-column
              prop="assignmentTitle"
              label="作业名称"
              min-width="180"
              show-overflow-tooltip
            />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small" effect="light">
                  {{ statusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="totalSubmissions" label="提交数" width="80" align="center" />
            <el-table-column prop="matchCount" label="匹配数" width="80" align="center" />
            <el-table-column prop="avgSimilarity" label="平均相似度" width="120" align="center">
              <template #default="{ row }">
                <el-progress
                  :percentage="Math.round((row.avgSimilarity ?? 0) * 100)"
                  :color="similarityColor(row.avgSimilarity)"
                  :stroke-width="14"
                  :text-inside="true"
                />
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="170" />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button
                  text
                  type="warning"
                  size="small"
                  :disabled="row.status === 'RUNNING'"
                  @click="handleExecute(row)"
                >
                  执行查重
                </el-button>
                <el-button text type="primary" size="small" @click="handleViewDetail(row)">
                  查看详情
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无查重任务">
                <template #image><div class="empty-deco">🔍</div></template>
              </el-empty>
            </template>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="taskPage.page"
              v-model:page-size="taskPage.size"
              :total="taskPage.total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @size-change="fetchTaskList"
              @current-change="fetchTaskList"
            />
          </div>
        </div>

        <!-- 下半部分：查重匹配详情 -->
        <div v-if="selectedTask" class="section-header" style="margin-top: 24px">
          <h3 class="section-title">
            查重匹配详情
            <span class="section-subtitle">
              — 任务 #{{ selectedTask.id }}
              <template v-if="selectedTask.assignmentTitle"
                >（{{ selectedTask.assignmentTitle }}）</template
              >
            </span>
          </h3>
        </div>

        <div v-if="selectedTask" class="content-card">
          <el-table v-loading="loadingMatches" :data="matchList" style="width: 100%" stripe>
            <el-table-column prop="studentA" label="学生A" min-width="160" show-overflow-tooltip />
            <el-table-column prop="studentB" label="学生B" min-width="160" show-overflow-tooltip />
            <el-table-column label="相似度" width="140" align="center">
              <template #default="{ row }">
                <el-progress
                  :percentage="Math.round((row.similarity ?? 0) * 100)"
                  :color="similarityColor(row.similarity)"
                  :stroke-width="18"
                  :text-inside="true"
                />
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="matchStatusTagType(row.status)" size="small" effect="light">
                  {{ matchStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="matchedContent"
              label="匹配内容"
              min-width="220"
              show-overflow-tooltip
            />
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="matchPage.page"
              v-model:page-size="matchPage.size"
              :total="matchPage.total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @size-change="fetchMatchList"
              @current-change="fetchMatchList"
            />
          </div>
        </div>
      </div>
    </section>

    <!-- 创建查重任务对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建查重任务" width="480px" destroy-on-close>
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="100px"
        label-position="top"
      >
        <el-form-item label="作业 ID" prop="assignmentId">
          <el-input v-model="createForm.assignmentId" placeholder="请输入作业 ID" clearable />
        </el-form-item>
        <p class="form-tip">请输入需要查重的作业 ID，系统将基于该作业的全部提交进行相似度比对。</p>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate"> 确认创建 </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import plagiarismApi from '@/api/plagiarismCheck'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

// ========== 状态 ==========
const loadingTasks = ref(false)
const loadingMatches = ref(false)
const creating = ref(false)

const taskList = ref([])
const matchList = ref([])
const selectedTask = ref(null)

const showCreateDialog = ref(false)
const createFormRef = ref(null)
const createForm = reactive({
  assignmentId: '',
})
const createRules = {
  assignmentId: [{ required: true, message: '请输入作业 ID', trigger: 'blur' }],
}

// 分页
const taskPage = reactive({ page: 1, size: 10, total: 0 })
const matchPage = reactive({ page: 1, size: 10, total: 0 })

// 统计概览
const taskTotal = computed(() => taskList.value.length)
const runningCount = computed(() => taskList.value.filter((r) => r.status === 'RUNNING').length)
const completedCount = computed(() => taskList.value.filter((r) => r.status === 'COMPLETED').length)
const pendingCount = computed(() => taskList.value.filter((r) => r.status === 'PENDING').length)

// ========== API 方法 ==========

/**
 * 获取查重任务列表
 */
async function fetchTaskList() {
  loadingTasks.value = true
  try {
    const res = await plagiarismApi.getCheckHistory('')
    const rawData = res.data?.records || res.data?.list || res.data || res || []
    const list = Array.isArray(rawData) ? rawData : []
    taskList.value = list
    taskPage.total = rawData?.total ?? rawData?.count ?? list.length
  } catch (error) {
    console.error('获取查重任务列表失败:', error)
    taskList.value = []
  } finally {
    loadingTasks.value = false
  }
}

/**
 * 获取匹配列表
 */
async function fetchMatchList() {
  if (!selectedTask.value) return
  loadingMatches.value = true
  try {
    const res = await plagiarismApi.getCheckMatches(selectedTask.value.id)
    const rawData = res.data?.records || res.data?.list || res.data || res || []
    const list = Array.isArray(rawData) ? rawData : []
    matchList.value = list
    matchPage.total = rawData?.total ?? rawData?.count ?? list.length
  } catch (error) {
    console.error('获取匹配列表失败:', error)
    matchList.value = []
  } finally {
    loadingMatches.value = false
  }
}

/**
 * 创建查重任务
 */
async function handleCreate() {
  if (!createFormRef.value) return
  try {
    await createFormRef.value.validate()
  } catch {
    return
  }
  creating.value = true
  try {
    await plagiarismApi.createCheck({ assignmentId: createForm.value.assignmentId })
    ElMessage.success('查重任务创建成功')
    showCreateDialog.value = false
    createForm.assignmentId = ''
    await fetchTaskList()
  } catch (error) {
    console.error('创建查重任务失败:', error)
    ElMessage.error('创建失败，请稍后重试')
  } finally {
    creating.value = false
  }
}

/**
 * 执行查重
 */
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

/**
 * 查看详情
 */
async function handleViewDetail(row) {
  selectedTask.value = row
  matchPage.page = 1
  await fetchMatchList()
}

// ========== 工具函数 ==========

function statusTagType(status) {
  const map = { PENDING: 'info', RUNNING: 'warning', COMPLETED: 'success' }
  return map[status] || 'info'
}

function statusText(status) {
  const map = { PENDING: '待执行', RUNNING: '执行中', COMPLETED: '已完成' }
  return map[status] || status || '未知'
}

function matchStatusTagType(status) {
  const map = { CONFIRMED: 'danger', PENDING: 'warning', DISMISSED: 'info' }
  return map[status] || 'info'
}

function matchStatusText(status) {
  const map = { CONFIRMED: '已确认', PENDING: '待审核', DISMISSED: '已忽略' }
  return map[status] || status || '未知'
}

function similarityColor(similarity) {
  if (similarity == null) return '#7A8BA5'
  const pct = similarity * 100
  if (pct >= 80) return '#C48080'
  if (pct >= 60) return '#CBB58A'
  return '#8BA888'
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

/* 页面头部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--ac-spacing-lg);
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  letter-spacing: -0.3px;
}

.page-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-top: 4px;
}

/* 区域标题 */
.section-header {
  margin-bottom: var(--ac-spacing-md);
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.section-subtitle {
  font-size: 14px;
  font-weight: 400;
  color: var(--ac-text-muted);
}

/* 内容卡片 */
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
}

/* 分页 */
.pagination-wrapper {
  margin-top: var(--ac-spacing-md);
  display: flex;
  justify-content: flex-end;
}

/* 表单提示 */
.form-tip {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: -8px;
  line-height: 1.5;
}

/* 空状态装饰 */
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

/* 响应式 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
  }
}
</style>
