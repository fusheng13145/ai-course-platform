<template>
  <div class="course-management fade-in">
    <PageHeader
      title="课程管理"
      subtitle="审核、启停与维护平台全部课程"
      icon="book"
      icon-bg="accent"
    >
      <span class="head-hint">共 {{ courseList.length }} 门课程</span>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="课程总数"
        :value="courseList.length"
        icon="book"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="待审核"
        :value="pendingCount"
        icon="warn"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="已启用"
        :value="activeCount"
        icon="check"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="已停用"
        :value="inactiveCount"
        icon="clock"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background: var(--accent-soft); color: var(--accent)">
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
              d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M4 19.5A2.5 2.5 0 0 0 6.5 22H20V2H6.5A2.5 2.5 0 0 0 4 4.5v15z"
            />
          </svg>
        </span>
        <div>
          <div class="l1-title">课程列表</div>
          <div class="l1-sub">支持审核状态与启用状态筛选</div>
        </div>
      </div>
      <div class="l1-body">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <el-select v-model="statusFilter" placeholder="审核状态" clearable style="width: 130px">
              <el-option label="待审核" value="PENDING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已拒绝" value="REJECTED" />
            </el-select>
            <el-select v-model="activeFilter" placeholder="启用状态" clearable style="width: 130px">
              <el-option label="已启用" value="ACTIVE" />
              <el-option label="已停用" value="INACTIVE" />
            </el-select>
          </div>
          <div class="toolbar-right">
            <el-input v-model="search" placeholder="搜索课程" clearable style="width: 220px">
              <template #prefix
                ><el-icon><Search /></el-icon
              ></template>
            </el-input>
          </div>
        </div>

        <div v-loading="loading" class="table-area">
          <el-table :data="filteredList" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="课程名称" min-width="160" />
            <el-table-column prop="category" label="分类" width="100">
              <template #default="{ row }">{{ row.category || '未分类' }}</template>
            </el-table-column>
            <el-table-column label="教师" width="120">
              <template #default="{ row }">{{ row.teacherName || `ID:${row.teacherId}` }}</template>
            </el-table-column>
            <el-table-column prop="approvalStatus" label="审核状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getApprovalType(row.approvalStatus)" size="small">{{
                  getApprovalText(row.approvalStatus)
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="启用状态" width="100">
              <template #default="{ row }">
                <span
                  class="status-dot"
                  :class="isActive(row) ? 'status-dot--active' : 'status-dot--inactive'"
                  ><i></i>{{ isActive(row) ? '已启用' : '已停用' }}</span
                >
              </template>
            </el-table-column>
            <el-table-column label="操作" width="320" fixed="right">
              <template #default="{ row }">
                <div class="action-row">
                  <el-button
                    v-if="row.approvalStatus === 'PENDING'"
                    text
                    type="success"
                    @click="handleApprove(row)"
                    >通过</el-button
                  >
                  <el-button
                    v-if="row.approvalStatus === 'PENDING'"
                    text
                    type="danger"
                    @click="handleReject(row)"
                    >拒绝</el-button
                  >
                  <el-button text type="primary" @click="viewDetail(row)">查看</el-button>
                  <el-button
                    text
                    :type="isActive(row) ? 'warning' : 'success'"
                    @click="handleToggleStatus(row)"
                    >{{ isActive(row) ? '停用' : '启用' }}</el-button
                  >
                  <el-button text type="danger" @click="handleDelete(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!loading && filteredList.length === 0" class="empty-wrap">
            <el-empty description="暂无课程数据，调整筛选条件试试">
              <template #image><div class="empty-deco">📖</div></template>
            </el-empty>
          </div>
        </div>
      </div>
    </section>

    <!-- 课程详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="课程详情" width="700px" append-to-body>
      <el-descriptions v-if="currentCourse" :column="2" border>
        <el-descriptions-item label="课程ID">{{ currentCourse.id }}</el-descriptions-item>
        <el-descriptions-item label="课程名称">{{ currentCourse.name }}</el-descriptions-item>
        <el-descriptions-item label="课程编码">{{
          currentCourse.code || '无'
        }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{
          currentCourse.category || '未分类'
        }}</el-descriptions-item>
        <el-descriptions-item label="学分">{{ currentCourse.credits || 0 }}</el-descriptions-item>
        <el-descriptions-item label="难度">{{
          getDifficultyText(currentCourse.difficulty)
        }}</el-descriptions-item>
        <el-descriptions-item label="教师ID">{{ currentCourse.teacherId }}</el-descriptions-item>
        <el-descriptions-item label="审核状态"
          ><el-tag :type="getApprovalType(currentCourse.approvalStatus)" size="small">{{
            getApprovalText(currentCourse.approvalStatus)
          }}</el-tag></el-descriptions-item
        >
        <el-descriptions-item label="启用状态"
          ><el-tag :type="isActive(currentCourse) ? 'success' : 'info'" size="small">{{
            isActive(currentCourse) ? '已启用' : '已停用'
          }}</el-tag></el-descriptions-item
        >
        <el-descriptions-item label="报名方式">{{
          getEnrollmentTypeText(currentCourse.enrollmentType)
        }}</el-descriptions-item>
        <el-descriptions-item label="最大人数">{{
          currentCourse.maxStudents || '不限'
        }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ currentCourse.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="课程简介" :span="2">{{
          currentCourse.description || '暂无简介'
        }}</el-descriptions-item>
        <el-descriptions-item v-if="currentCourse.approvalComment" label="审核备注" :span="2">{{
          currentCourse.approvalComment
        }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import courseApi from '@/api/course'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

const courseList = ref([])
const search = ref('')
const statusFilter = ref('')
const activeFilter = ref('')
const loading = ref(false)
const detailDialogVisible = ref(false)
const currentCourse = ref(null)

const normalizeStatus = (status) =>
  String(status || '')
    .trim()
    .toUpperCase()
const isActive = (course) => normalizeStatus(course?.status) === 'ACTIVE'

const pendingCount = computed(
  () => courseList.value.filter((c) => c.approvalStatus === 'PENDING').length,
)
const activeCount = computed(() => courseList.value.filter(isActive).length)
const inactiveCount = computed(
  () => courseList.value.filter((c) => normalizeStatus(c.status) === 'INACTIVE').length,
)

const filteredList = computed(() => {
  let list = courseList.value
  if (statusFilter.value) list = list.filter((c) => c.approvalStatus === statusFilter.value)
  if (activeFilter.value) {
    list = list.filter((c) => normalizeStatus(c.status) === activeFilter.value)
  }
  if (search.value) {
    const q = search.value.toLowerCase()
    list = list.filter((c) => c.name?.toLowerCase().includes(q))
  }
  return list
})

function getApprovalType(status) {
  return status === 'APPROVED' ? 'success' : status === 'REJECTED' ? 'danger' : 'warning'
}

function getApprovalText(status) {
  return status === 'APPROVED' ? '已通过' : status === 'REJECTED' ? '已拒绝' : '待审核'
}

function getDifficultyText(diff) {
  const map = { BEGINNER: '入门', EASY: '初级', MEDIUM: '中级', HARD: '高级', EXPERT: '专家' }
  return map[diff] || diff || '未设置'
}

function getEnrollmentTypeText(type) {
  const map = { OPEN: '开放报名', APPROVAL: '需审核', RESTRICTED: '限制报名' }
  return map[type] || type || '开放报名'
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await courseApi.getCourseList()
    const rawData = res.data?.records || res.data?.list || res.data || res
    courseList.value = Array.isArray(rawData) ? rawData : []
  } catch (e) {
    logger.error('Failed to load courses:', e)
    ElMessage.error('加载课程列表失败')
  } finally {
    loading.value = false
  }
})

function viewDetail(row) {
  currentCourse.value = row
  detailDialogVisible.value = true
}

async function handleApprove(row) {
  try {
    await ElMessageBox.confirm('确定通过该课程审核？', '提示')
    await courseApi.approveCourse(row.id)
    row.approvalStatus = 'APPROVED'
    row.status = 'ACTIVE'
    ElMessage.success('审核通过')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

async function handleReject(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝课程')
    await courseApi.rejectCourse(row.id, value)
    row.approvalStatus = 'REJECTED'
    ElMessage.success('已拒绝')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

async function handleToggleStatus(row) {
  const action = isActive(row) ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确定${action}该课程？`, '提示')
    await courseApi.toggleCourseStatus(row.id)
    row.status = isActive(row) ? 'INACTIVE' : 'ACTIVE'
    ElMessage.success(`${action}成功`)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除该课程？此操作不可恢复', '提示', { type: 'warning' })
    await courseApi.deleteCourse(row.id)
    courseList.value = courseList.value.filter((c) => c.id !== row.id)
    ElMessage.success('已删除')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}
</script>

<style scoped>
.course-management {
  padding: 4px;
}
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.head-hint {
  font-size: 13px;
  color: var(--ink-3);
}
.table-area {
  min-height: 200px;
}
.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  flex-wrap: nowrap;
}
.status-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ink-2);
}
.status-dot i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}
.status-dot--active i {
  background: var(--ok);
}
.status-dot--active {
  color: var(--ok);
}
.status-dot--inactive i {
  background: var(--ink-3);
}
.empty-wrap {
  padding: 30px 0;
}
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
</style>
