<template>
  <div class="courses-page fade-in">
    <PageHeader title="我的课程" subtitle="管理课程信息、审核状态与启用状态" icon="books">
      <el-button type="primary" @click="$router.push('/teacher/courses/create')">
        <el-icon><Plus /></el-icon>创建课程
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard label="课程总数" :value="courseList.length" icon="collection" />
      <StatCard
        label="待审核"
        :value="pendingCount"
        icon="clock"
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
        icon="lock"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
    </div>

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
        <el-input v-model="search" placeholder="搜索课程名称或编号" clearable style="width: 260px">
          <template #prefix
            ><el-icon><Search /></el-icon
          ></template>
        </el-input>
      </div>
    </div>

    <!-- 课程卡片网格 -->
    <div v-loading="loading" class="courses-grid">
      <template v-if="filteredList.length > 0">
        <TcCourseCard
          v-for="course in filteredList"
          :key="course.id"
          :course="course"
          @go-detail="goToCourseDetail"
          @view-detail="viewDetail"
          @toggle-status="handleToggleStatus"
          @delete="handleDelete"
        />
      </template>
      <el-empty
        v-if="!loading && filteredList.length === 0"
        description="暂无课程，点击右上角「创建课程」开始"
      >
        <template #image><div class="empty-deco">📚</div></template>
      </el-empty>
    </div>

    <!-- 课程详情对话框 -->
    <TcCourseDetailDialog
      v-model="detailDialogVisible"
      :course="currentCourse"
      @manage="goToManage"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import courseApi from '@/api/course'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import TcCourseCard from './components/TcCourseCard.vue'
import TcCourseDetailDialog from './components/TcCourseDetailDialog.vue'

/**
 * 教师课程管理页（拆分子组件后页面壳）。
 * <p>职责收敛：课程数据加载/筛选/增删改操作；卡片渲染（TcCourseCard）
 * 与详情弹窗（TcCourseDetailDialog）分别下沉。</p>
 */
const router = useRouter()
const courseList = ref([])
const search = ref('')
const statusFilter = ref('')
const activeFilter = ref('')
const loading = ref(false)
const detailDialogVisible = ref(false)
const currentCourse = ref(null)

const pendingCount = computed(
  () => courseList.value.filter((c) => c.approvalStatus === 'PENDING').length,
)
const activeCount = computed(() => courseList.value.filter((c) => c.status === 'ACTIVE').length)
const inactiveCount = computed(() => courseList.value.filter((c) => c.status === 'INACTIVE').length)

const filteredList = computed(() => {
  let list = courseList.value
  if (statusFilter.value) list = list.filter((c) => c.approvalStatus === statusFilter.value)
  if (activeFilter.value) list = list.filter((c) => c.status === activeFilter.value)
  if (search.value) {
    const kw = search.value.toLowerCase()
    list = list.filter(
      (c) =>
        (c.name && c.name.toLowerCase().includes(kw)) ||
        (c.code && c.code.toLowerCase().includes(kw)),
    )
  }
  return list
})

onMounted(async () => {
  loading.value = true
  try {
    const res = await courseApi.getMyCourses()
    const rawData = res.data?.records || res.data?.list || res.data || res
    courseList.value = Array.isArray(rawData) ? rawData : []
  } catch (e) {
    console.error('Failed to load courses:', e)
    courseList.value = []
  } finally {
    loading.value = false
  }
})

function goToCourseDetail(row) {
  router.push(`/teacher/courses/${row.id}/detail`)
}

function viewDetail(row) {
  currentCourse.value = row
  detailDialogVisible.value = true
}

function goToManage() {
  if (currentCourse.value) {
    detailDialogVisible.value = false
    router.push(`/teacher/courses/${currentCourse.value.id}`)
  }
}

async function handleToggleStatus(row) {
  const action = row.status === 'ACTIVE' ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确定${action}该课程？`, '提示')
    await courseApi.toggleCourseStatus(row.id)
    row.status = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
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
    ElMessage.success('删除成功')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}
</script>

<style scoped>
.courses-page {
  padding: 4px;
}

/* 工具栏 */
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 10px;
}
.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 卡片网格 */
.courses-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  min-height: 200px;
}

.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

@media (max-width: 768px) {
  .courses-grid {
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    gap: 14px;
  }
  .table-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .toolbar-left {
    flex-wrap: wrap;
  }
}
</style>
