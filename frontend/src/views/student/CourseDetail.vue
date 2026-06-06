<template>
  <div>
    <el-page-header :title="course.name" @back="$router.push('/student/courses')" style="margin-bottom:20px" />
    <el-tabs v-model="activeTab">
      <el-tab-pane label="课程简介" name="info">
        <div class="content-card">
          <h3>{{ course.name }}</h3>
          <p style="color:#606266;line-height:1.8;margin:16px 0">{{ course.description || '暂无简介' }}</p>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="分类">{{ course.category }}</el-descriptions-item>
            <el-descriptions-item label="教师">{{ course.teacherName || '未知' }}</el-descriptions-item>
            <el-descriptions-item label="进度"><el-progress :percentage="60" :stroke-width="8" style="width:200px" /></el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag type="success" size="small">学习中</el-tag></el-descriptions-item>
          </el-descriptions>
        </div>
      </el-tab-pane>
      <el-tab-pane label="章节学习" name="chapters">
        <div class="content-card">
          <div v-for="(ch, i) in chapters" :key="ch.id" style="padding:12px 0;border-bottom:1px solid #f0f0f0;display:flex;align-items:center;justify-content:space-between">
            <div><el-tag size="small" style="margin-right:8px">第{{ i+1 }}章</el-tag><span>{{ ch.title }}</span></div>
            <div><el-tag :type="ch.completed ? 'success' : 'info'" size="small">{{ ch.completed ? '已完成' : '未开始' }}</el-tag></div>
          </div>
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
const course = reactive({ name: '加载中...', description: '', category: '', teacherName: '' })
const chapters = ref([{ id: 1, title: "Python环境搭建", completed: true }, { id: 2, title: "变量与数据类型", completed: true }, { id: 3, title: "条件与循环", completed: false }])

onMounted(async () => {
  try {
    const res = await courseApi.getCourseById(route.params.id)
    if (res.data) Object.assign(course, res.data)
  } catch (e) { Object.assign(course, { name: 'Python编程入门', description: '零基础学习Python编程', category: '编程开发', teacherName: '李老师' }) }
})
</script>