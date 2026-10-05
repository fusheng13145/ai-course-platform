<template>
  <div class="course-shell fade-in">
    <!-- 课程信息页头 -->
    <div class="shell-header">
      <div class="shell-header__left">
        <el-button text class="back-btn" @click="$router.push('/student/courses')">
          <el-icon><ArrowLeft /></el-icon>返回课程列表
        </el-button>
        <div class="shell-title">
          <h2>{{ courseName || '课程详情' }}</h2>
          <span v-if="courseTeacher" class="shell-sub">{{ courseTeacher }}</span>
        </div>
      </div>
      <div class="shell-header__right">
        <el-tag v-if="courseProgress > 0" type="success" effect="light"
          >{{ courseProgress }}% 已完成</el-tag
        >
      </div>
    </div>

    <!-- 二级 Tab 导航 -->
    <div class="shell-tabs">
      <router-link
        v-for="tab in tabs"
        :key="tab.path"
        :to="`/student/courses/${courseId}/${tab.path}`"
        class="shell-tab"
        :class="{ 'is-active': activeTab === tab.path }"
      >
        <el-icon v-if="tab.icon"><component :is="tab.icon" /></el-icon>
        <span>{{ tab.label }}</span>
      </router-link>
    </div>

    <!-- 嵌套子路由视图 -->
    <div class="shell-body">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { unwrap } from '@/utils/response'
import courseApi from '@/api/course'
import { ArrowLeft, DataLine, Reading, Collection, EditPen, Medal } from '@element-plus/icons-vue'

const route = useRoute()
const courseId = computed(() => route.params.id)

const courseName = ref('')
const courseTeacher = ref('')
const courseProgress = ref(0)

const tabs = [
  { path: 'overview', label: '课程概览', icon: DataLine },
  { path: 'learning-center', label: '作业 · 学习中心', icon: Reading },
  { path: 'exams', label: '考试练习', icon: EditPen },
  { path: 'grades', label: '课程成绩', icon: Medal },
  { path: 'knowledge', label: '知识图谱', icon: Collection },
]

const activeTab = computed(() => {
  const seg = route.path.split('/').filter(Boolean)
  const last = seg[seg.length - 1]
  // student/courses/:id → overview; student/courses/:id/learning-center → learning-center
  return ['overview', 'learning-center', 'knowledge'].includes(last) ? last : 'overview'
})

async function loadCourse() {
  try {
    const res = await courseApi.getCourseById(courseId.value)
    const c = unwrap(res)
    if (c) {
      courseName.value = c.name || ''
      courseTeacher.value = c.teacherName || ''
      courseProgress.value = c.progress || c.enrollment?.progress || 0
    }
  } catch {
    courseName.value = ''
  }
}

onMounted(() => {
  loadCourse()
})
</script>

<style scoped>
.course-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.shell-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--ac-border);
}

.shell-header__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  color: var(--ac-text-secondary);
}

.shell-title h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--ac-text-primary);
}

.shell-sub {
  font-size: 12px;
  color: var(--ac-text-muted);
}

.shell-header__right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.shell-tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--ac-border);
  margin-top: 8px;
  overflow-x: auto;
}

.shell-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  font-size: 14px;
  color: var(--ac-text-secondary);
  text-decoration: none;
  border-bottom: 2px solid transparent;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.shell-tab:hover {
  color: var(--ac-primary);
  background: var(--ac-bg-hover);
}

.shell-tab.is-active {
  color: var(--ac-primary);
  border-bottom-color: var(--ac-primary);
  font-weight: 600;
}

.shell-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
}
</style>
