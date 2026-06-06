<template>
  <div>
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-select v-model="statusFilter" placeholder="审核状态" clearable style="width:130px">
          <el-option label="待审核" value="PENDING" /><el-option label="已通过" value="APPROVED" /><el-option label="已拒绝" value="REJECTED" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-input v-model="search" placeholder="搜索课程" clearable style="width:220px" />
      </div>
    </div>
    <div class="content-card">
      <el-table :data="filteredList" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="课程名称" min-width="160" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column label="教师" width="120"><template #default="{row}">{{ row.teacherName || row.teacherId }}</template></el-table-column>
        <el-table-column prop="approvalStatus" label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="row.approvalStatus === 'APPROVED' ? 'success' : row.approvalStatus === 'REJECTED' ? 'danger' : 'warning'" size="small">
              {{ row.approvalStatus === 'APPROVED' ? '已通过' : row.approvalStatus === 'REJECTED' ? '已拒绝' : '待审核' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{row}">
            <el-button text type="success" @click="handleApprove(row)" v-if="row.approvalStatus === 'PENDING'">通过</el-button>
            <el-button text type="danger" @click="handleReject(row)" v-if="row.approvalStatus === 'PENDING'">拒绝</el-button>
            <el-button text type="primary" @click="viewDetail(row)">查看</el-button>
            <el-button text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import courseApi from '@/api/course'

const courseList = ref([])
const search = ref('')
const statusFilter = ref('')

const filteredList = computed(() => {
  let list = courseList.value
  if (statusFilter.value) list = list.filter(c => c.approvalStatus === statusFilter.value)
  if (search.value) { const q = search.value.toLowerCase(); list = list.filter(c => c.name?.toLowerCase().includes(q)) }
  return list
})

onMounted(async () => {
  try {
    const res = await courseApi.getCourseList()
    courseList.value = res.data || res || []
  } catch (e) {
    courseList.value = [
      { id: 1, name: 'Python编程入门', category: '编程开发', teacherName: '李老师', approvalStatus: 'APPROVED', isActive: true },
      { id: 2, name: '机器学习基础', category: '人工智能', teacherName: '王老师', approvalStatus: 'PENDING', isActive: true },
      { id: 3, name: '数据分析实战', category: '数据分析', teacherName: '张老师', approvalStatus: 'APPROVED', isActive: true }
    ]
  }
})

function handleApprove(row) {
  ElMessageBox.confirm('确定通过该课程？', '提示').then(async () => {
    try { await courseApi.approveCourse(row.id); row.approvalStatus = 'APPROVED'; ElMessage.success('已通过') }
    catch (e) { ElMessage.error('操作失败') }
  }).catch(() => {})
}

function handleReject(row) {
  ElMessageBox.prompt('请输入拒绝原因', '拒绝课程').then(async ({ value }) => {
    try { await courseApi.rejectCourse(row.id, value); row.approvalStatus = 'REJECTED'; ElMessage.success('已拒绝') }
    catch (e) { ElMessage.error('操作失败') }
  }).catch(() => {})
}

function handleDelete(row) {
  ElMessageBox.confirm('确定删除该课程？', '提示', { type: 'warning' }).then(async () => {
    try { await courseApi.deleteCourse(row.id); courseList.value = courseList.value.filter(c => c.id !== row.id); ElMessage.success('已删除') }
    catch (e) { ElMessage.error('操作失败') }
  }).catch(() => {})
}

function viewDetail(row) { ElMessage.info('查看课程详情：' + row.name) }
</script>