<template>
  <div>
    <el-page-header :title="course.name" @back="$router.push('/student/courses')" style="margin-bottom:20px" />
    <el-tabs v-model="activeTab">
      <el-tab-pane label="课程简介" name="info">
        <div class="content-card">
          <h3>{{ course.name }}</h3>
          <p style="color:#606266;line-height:1.8;margin:16px 0">{{ course.description || '暂无简介' }}</p>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="分类">{{ course.category || '未分类' }}</el-descriptions-item>
            <el-descriptions-item label="教师">{{ course.teacherName || '未知' }}</el-descriptions-item>
            <el-descriptions-item label="进度"><el-progress :percentage="enrollment.progress || 0" :stroke-width="8" style="width:200px" /></el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="enrollment.isActive ? 'success' : 'info'" size="small">{{ enrollment.isActive ? '学习中' : '未开始' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="学分">{{ course.credits || 0 }}</el-descriptions-item>
            <el-descriptions-item label="难度">{{ course.difficulty || '未设置' }}</el-descriptions-item>
          </el-descriptions>
          <div style="margin-top:20px" v-if="!enrollment.id">
            <el-button type="primary" @click="handleEnroll" :loading="enrolling">加入课程</el-button>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="章节学习" name="chapters">
        <div class="content-card">
          <div v-if="chapters.length > 0">
            <div v-for="(ch, i) in chapters" :key="ch.id" style="padding:12px 0;border-bottom:1px solid #f0f0f0;display:flex;align-items:center;justify-content:space-between;cursor:pointer" @click="startLearning(ch)">
              <div style="display:flex;align-items:center">
                <el-tag size="small" style="margin-right:8px">第{{ i+1 }}章</el-tag>
                <span>{{ ch.title }}</span>
                <span v-if="ch.duration" style="color:#909399;font-size:12px;margin-left:8px">({{ ch.duration }}分钟)</span>
              </div>
              <div>
                <el-tag :type="ch.completed ? 'success' : ch.current ? 'warning' : 'info'" size="small">
                  {{ ch.completed ? '已完成' : ch.current ? '学习中' : '未开始' }}
                </el-tag>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无章节内容" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import courseApi from '@/api/course'
import chapterApi from '@/api/chapter'
import enrollmentApi from '@/api/enrollment'

const route = useRoute()
const activeTab = ref('info')
const course = reactive({ id: null, name: '加载中...', description: '', category: '', teacherName: '', credits: 0, difficulty: '' })
const chapters = ref([])
const enrollment = reactive({ id: null, progress: 0, isActive: false })
const enrolling = ref(false)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  const courseId = route.params.id
  try {
    // Load course info
    const courseRes = await courseApi.getCourseById(courseId)
    if (courseRes.data) Object.assign(course, courseRes.data)

    // Load chapters
    const chapterRes = await chapterApi.getChapters(courseId)
    const chapterList = chapterRes.data || chapterRes || []
    chapters.value = chapterList.map(ch => ({
      ...ch,
      completed: false,
      current: false
    }))

    // Check enrollment status
    const enrollRes = await enrollmentApi.getMyEnrollments()
    const enrollments = enrollRes.data?.list || enrollRes.data || enrollRes || []
    const myEnrollment = enrollments.find(e => e.courseId === Number(courseId) && e.isActive)
    if (myEnrollment) {
      enrollment.id = myEnrollment.id
      enrollment.progress = Math.round(Number(myEnrollment.progress || 0) * 100)
      enrollment.isActive = myEnrollment.isActive
    }
  } catch (e) {
    console.error('Failed to load course:', e)
    ElMessage.error('加载课程失败')
  } finally {
    loading.value = false
  }
})

async function handleEnroll() {
  enrolling.value = true
  try {
    await enrollmentApi.enrollCourse({ courseId: Number(route.params.id) })
    ElMessage.success('加入课程成功')
    enrollment.isActive = true
    enrollment.progress = 0
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '加入课程失败')
  } finally {
    enrolling.value = false
  }
}

function startLearning(ch) {
  ElMessage.info(`开始学习：${ch.title}`)
  // TODO: Navigate to learning page or open chapter content
}
</script>