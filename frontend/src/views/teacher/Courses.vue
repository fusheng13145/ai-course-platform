﻿﻿﻿<template>
  <div>
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="$router.push('/teacher/courses/create')"><el-icon><Plus /></el-icon>创建课程</el-button>
        <el-select v-model="statusFilter" placeholder="审核状态" clearable style="width:130px;margin-left:8px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
        <el-select v-model="activeFilter" placeholder="启用状态" clearable style="width:130px;margin-left:8px">
          <el-option label="已启用" value="ACTIVE" />
          <el-option label="已停用" value="INACTIVE" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-input v-model="search" placeholder="搜索课程" clearable style="width:220px" />
      </div>
    </div>
    <div class="content-card">
      <el-table :data="filteredList" style="width:100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="课程名称" min-width="160" />
        <el-table-column prop="category" label="分类" width="100"><template #default="{row}">{{ row.category || '未分类' }}</template></el-table-column>
        <el-table-column prop="studentCount" label="学生数" width="80"><template #default="{row}">{{ row.studentCount || 0 }}</template></el-table-column>
        <el-table-column prop="approvalStatus" label="审核状态" width="100"><template #default="{row}"><el-tag :type="getStatusType(row.approvalStatus)" size="small">{{ getStatusText(row.approvalStatus) }}</el-tag></template></el-table-column>
        <el-table-column prop="status" label="启用状态" width="100"><template #default="{row}"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? '已启用' : '已停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{row}">
            <el-button text type="primary" @click="$router.push(`/teacher/courses/${row.id}`)">管理</el-button>
            <el-button text type="primary" @click="viewDetail(row)">详情</el-button>
            <el-button text :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="handleToggleStatus(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
            <el-button text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && filteredList.length === 0" description="暂无课程" />
    </div>

    <!-- 课程详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="课程详情" width="700px">
      <el-descriptions :column="2" border v-if="currentCourse">
        <el-descriptions-item label="课程ID">{{ currentCourse.id }}</el-descriptions-item>
        <el-descriptions-item label="课程名称">{{ currentCourse.name }}</el-descriptions-item>
        <el-descriptions-item label="课程编码">{{ currentCourse.code || '无' }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ currentCourse.category || '未分类' }}</el-descriptions-item>
        <el-descriptions-item label="学分">{{ currentCourse.credits || 0 }}</el-descriptions-item>
        <el-descriptions-item label="难度">{{ getDifficultyText(currentCourse.difficulty) }}</el-descriptions-item>
        <el-descriptions-item label="审核状态"><el-tag :type="getStatusType(currentCourse.approvalStatus)" size="small">{{ getStatusText(currentCourse.approvalStatus) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="启用状态"><el-tag :type="currentCourse.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ currentCourse.status === 'ACTIVE' ? '已启用' : '已停用' }}</el-tag></el-descriptions-item>
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
import courseApi from '@/api/course'

const router = useRouter()
const courseList = ref([])
const search = ref('')
const statusFilter = ref('')
const activeFilter = ref('')
const loading = ref(false)
const detailDialogVisible = ref(false)
const currentCourse = ref(null)

const filteredList = computed(() => {
  let list = courseList.value
  if (statusFilter.value) list = list.filter(c => c.approvalStatus === statusFilter.value)
  if (activeFilter.value) list = list.filter(c => c.status === activeFilter.value)
  if (search.value) list = list.filter(c => c.name?.includes(search.value))
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
    courseList.value = courseList.value.filter(c => c.id !== row.id)
    ElMessage.success('删除成功')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}
</script>
