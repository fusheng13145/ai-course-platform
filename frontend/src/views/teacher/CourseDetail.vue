<template>
  <div>
    <el-page-header :title="course.name" @back="$router.push('/teacher/courses')" style="margin-bottom:20px" />
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基本信息" name="info">
        <div class="content-card">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="课程名称">{{ course.name }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ course.category }}</el-descriptions-item>
            <el-descriptions-item label="学生人数">{{ course.studentCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag :type="course.approvalStatus === 'APPROVED' ? 'success' : 'warning'" size="small">{{ course.approvalStatus === 'APPROVED' ? '已通过' : '待审核' }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="简介" :span="2">{{ course.description || '暂无简介' }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-tab-pane>
      <el-tab-pane label="章节管理" name="chapters">
        <div class="table-toolbar"><el-button type="primary"><el-icon><Plus /></el-icon>添加章节</el-button></div>
        <div class="content-card">
          <el-table :data="chapters" stripe style="width:100%">
            <el-table-column prop="id" label="序号" width="60" />
            <el-table-column prop="title" label="章节名称" />
            <el-table-column prop="orderIndex" label="排序" width="80" />
            <el-table-column label="操作" width="200" fixed="right"><template #default><el-button text type="primary">编辑</el-button><el-button text type="danger">删除</el-button></template></el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import courseApi from '@/api/course'

const route = useRoute()
const activeTab = ref('info')
const course = reactive({ id: null, name: '加载中...', category: '', approvalStatus: '', description: '', studentCount: 0 })
const chapters = ref([])

onMounted(async () => {
  try {
    const res = await courseApi.getCourseById(route.params.id)
    if (res.data) Object.assign(course, res.data)
  } catch (e) {
    Object.assign(course, { id: route.params.id, name: 'Python编程入门', category: '编程开发', approvalStatus: 'APPROVED', description: '零基础学习Python编程', studentCount: 45 })
  }
})
</script>