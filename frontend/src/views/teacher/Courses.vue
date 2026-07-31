<template>
  <div class="courses-page fade-in">
    <PageHeader title="我的课程" subtitle="管理你创建的课程，支持审核、启停与进入教学" icon="book" icon-bg="accent">
      <el-button type="primary" @click="$router.push('/teacher/courses/create')">
        <el-icon><Plus /></el-icon>创建课程
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard label="课程总数" :value="courseList.length" icon="book" tone="var(--accent-soft)" tone-color="var(--accent)" />
      <StatCard label="待审核" :value="pendingCount" icon="warn" tone="var(--warn-soft)" tone-color="var(--warn)" />
      <StatCard label="已启用" :value="activeCount" icon="check" tone="var(--ok-soft)" tone-color="var(--ok)" />
      <StatCard label="已停用" :value="inactiveCount" icon="clock" tone="var(--info-soft)" tone-color="var(--info)" />
    </div>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-select v-model="statusFilter" placeholder="审核状态" clearable style="width:130px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
        <el-select v-model="activeFilter" placeholder="启用状态" clearable style="width:130px">
          <el-option label="已启用" value="ACTIVE" />
          <el-option label="已停用" value="INACTIVE" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-input v-model="search" placeholder="搜索课程名称或编号" clearable style="width:260px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
    </div>

    <!-- 课程卡片网格 -->
    <div v-loading="loading" class="courses-grid">
      <template v-if="filteredList.length > 0">
        <div
          v-for="course in filteredList"
          :key="course.id"
          class="course-card"
          @click="goToCourseDetail(course)"
        >
          <div class="card-cover" :style="{ background: getCoverGradient(course) }">
            <div class="cover-overlay">
              <el-tag size="small" :type="getStatusType(course.approvalStatus)" effect="dark" round class="status-tag">
                {{ getStatusText(course.approvalStatus) }}
              </el-tag>
              <el-tag size="small" :type="course.status === 'ACTIVE' ? 'success' : 'info'" effect="dark" round class="status-tag">
                {{ course.status === 'ACTIVE' ? '启用' : '停用' }}
              </el-tag>
            </div>
            <div class="cover-category">
              <span class="category-badge">{{ course.category || '未分类' }}</span>
            </div>
          </div>

          <div class="card-body">
            <h3 class="card-title" :title="course.name">{{ course.name }}</h3>
            <div class="card-meta">
              <div class="meta-item">
                <el-icon :size="14"><CollectionTag /></el-icon>
                <span>{{ course.code || '暂无编号' }}</span>
              </div>
              <div class="meta-item">
                <el-icon :size="14"><User /></el-icon>
                <span>{{ course.studentCount || 0 }} 人选修</span>
              </div>
              <div v-if="course.semester" class="meta-item">
                <el-icon :size="14"><Calendar /></el-icon>
                <span>{{ course.semester }}</span>
              </div>
            </div>
            <div v-if="course.description" class="card-desc">{{ course.description }}</div>
          </div>

          <div class="card-footer" @click.stop>
            <el-button text size="small" type="primary" @click.stop="goToCourseDetail(course)">进入课程</el-button>
            <el-button text size="small" type="primary" @click.stop="viewDetail(course)">详情</el-button>
            <el-button text size="small" :type="course.status === 'ACTIVE' ? 'warning' : 'success'" @click.stop="handleToggleStatus(course)">
              {{ course.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
            <el-button text size="small" type="danger" @click.stop="handleDelete(course)">删除</el-button>
          </div>
        </div>
      </template>
      <el-empty v-if="!loading && filteredList.length === 0" description="暂无课程，点击右上角「创建课程」开始">
        <template #image><div class="empty-deco">📚</div></template>
      </el-empty>
    </div>

    <!-- 课程详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="课程详情" width="700px">
      <el-descriptions v-if="currentCourse" :column="2" border>
        <el-descriptions-item label="课程ID">{{ currentCourse.id }}</el-descriptions-item>
        <el-descriptions-item label="课程名称">{{ currentCourse.name }}</el-descriptions-item>
        <el-descriptions-item label="课程编码">{{ currentCourse.code || '无' }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ currentCourse.category || '未分类' }}</el-descriptions-item>
        <el-descriptions-item label="学分">{{ currentCourse.credits || 0 }}</el-descriptions-item>
        <el-descriptions-item label="难度">{{ getDifficultyText(currentCourse.difficulty) }}</el-descriptions-item>
        <el-descriptions-item label="审核状态">
          <el-tag :type="getStatusType(currentCourse.approvalStatus)" size="small">{{ getStatusText(currentCourse.approvalStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="启用状态">
          <el-tag :type="currentCourse.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ currentCourse.status === 'ACTIVE' ? '已启用' : '已停用' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="报名方式">{{ getEnrollmentTypeText(currentCourse.enrollmentType) }}</el-descriptions-item>
        <el-descriptions-item label="最大人数">{{ currentCourse.maxStudents || '不限' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ currentCourse.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="课程简介" :span="2">{{ currentCourse.description || '暂无简介' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="goToManage">管理课程</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, User, CollectionTag, Calendar, Search } from '@element-plus/icons-vue'
import courseApi from '@/api/course'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

const router = useRouter()
const courseList = ref([])
const search = ref('')
const statusFilter = ref('')
const activeFilter = ref('')
const loading = ref(false)
const detailDialogVisible = ref(false)
const currentCourse = ref(null)

const pendingCount = computed(() => courseList.value.filter((c) => c.approvalStatus === 'PENDING').length)
const activeCount = computed(() => courseList.value.filter((c) => c.status === 'ACTIVE').length)
const inactiveCount = computed(() => courseList.value.filter((c) => c.status === 'INACTIVE').length)

// 预定义渐变色数组，按课程ID分配不同颜色
const coverGradients = [
  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
  'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
  'linear-gradient(135deg, #fccb90 0%, #d57eeb 100%)',
  'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)',
  'linear-gradient(135deg, #48c6ef 0%, #6f86d6 100%)',
  'linear-gradient(135deg, #0ba360 0%, #3cba92 100%)',
]

function getCoverGradient(course) {
  const idx = (course.id || 0) % coverGradients.length
  return coverGradients[idx]
}

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

function getStatusType(status) {
  return status === 'APPROVED' ? 'success' : status === 'REJECTED' ? 'danger' : 'warning'
}

function getStatusText(status) {
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
.courses-page { padding: 4px; }

/* 工具栏 */
.table-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; flex-wrap: wrap; gap: 10px; }
.toolbar-left, .toolbar-right { display: flex; align-items: center; gap: 8px; }

/* 卡片网格 */
.courses-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; min-height: 200px; }

/* 卡片主体 */
.course-card { background: var(--ac-bg-elevated, #fff); border: 1px solid var(--ac-border, #e5e7eb); border-radius: var(--ac-radius-md, 10px); overflow: hidden; cursor: pointer; transition: transform 0.2s ease, box-shadow 0.2s ease; display: flex; flex-direction: column; }
.course-card:hover { transform: translateY(-3px); box-shadow: 0 8px 25px rgba(0, 0, 0, 0.08); }

.card-cover { position: relative; height: 110px; display: flex; align-items: flex-end; padding: 12px 16px; }
.cover-overlay { position: absolute; top: 10px; right: 12px; display: flex; gap: 6px; }
.status-tag { font-size: 11px; }
.cover-category { position: relative; z-index: 1; }
.category-badge { display: inline-block; background: rgba(255, 255, 255, 0.25); backdrop-filter: blur(4px); color: #fff; font-size: 11px; padding: 3px 10px; border-radius: 12px; font-weight: 500; }

.card-body { padding: 16px 16px 0; flex: 1; }
.card-title { font-size: 16px; font-weight: 600; color: var(--ac-text-primary, #1e293b); margin: 0 0 10px; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-meta { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 8px; }
.meta-item { display: inline-flex; align-items: center; gap: 4px; font-size: 12.5px; color: var(--ac-text-secondary, #64748b); }
.card-desc { font-size: 12.5px; color: var(--ac-text-secondary, #94a3b8); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; margin-bottom: 8px; min-height: 20px; }

.card-footer { display: flex; justify-content: flex-end; align-items: center; padding: 8px 12px; border-top: 1px solid var(--ac-border, #f1f5f9); gap: 2px; }

.empty-deco { font-size: 38px; filter: grayscale(.2); }

@media (max-width: 768px) {
  .courses-grid { grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 14px; }
  .table-toolbar { flex-direction: column; align-items: stretch; }
  .toolbar-left { flex-wrap: wrap; }
}
</style>
