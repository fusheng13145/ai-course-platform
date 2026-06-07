<template>
  <div>
    <div class="table-toolbar"><div class="toolbar-left"><el-button type="primary" @click="$router.push('/teacher/courses/create')"><el-icon><Plus /></el-icon>创建课程</el-button></div><div class="toolbar-right"><el-input v-model="search" placeholder="搜索课程" clearable style="width:220px" /></div></div>
    <div class="content-card">
      <el-table :data="filteredList" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="课程名称" min-width="160" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="studentCount" label="学生数" width="80" />
        <el-table-column prop="approvalStatus" label="状态" width="100"><template #default="{row}"><el-tag :type="row.approvalStatus === 'APPROVED' ? 'success' : 'warning'" size="small">{{ row.approvalStatus === 'APPROVED' ? '已通过' : '待审核' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{row}">
            <el-button text type="primary" @click="$router.push(`/teacher/courses/${row.id}`)">管理</el-button>
            <el-button text type="primary" @click="$router.push(`/teacher/courses/${row.id}`)">详情</el-button>
            <el-button text type="danger">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import courseApi from '@/api/course'

const courseList = ref([])
const search = ref('')

const filteredList = computed(() => search.value ? courseList.value.filter(c => c.name.includes(search.value)) : courseList.value)

onMounted(async () => {
  try {
    const res = await courseApi.getCourseList()
    courseList.value = res.data?.list || res.data || res || []
  } catch (e) {
    console.error('Failed to load courses:', e)
  }
})
</script>
