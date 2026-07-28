<template>
  <div class="course-cockpit fade-in">
    <!-- 课程驾驶舱页头 -->
    <div class="cockpit-header">
      <div class="cockpit-header__left">
        <el-button text class="back-btn" @click="$router.push('/teacher/courses')">
          <el-icon><ArrowLeft /></el-icon> 返回课程列表
        </el-button>
        <div class="cockpit-title">
          <h2>{{ courseName || '课程驾驶舱' }}</h2>
          <span v-if="courseCode" class="cockpit-sub">课程代码 · {{ courseCode }}</span>
        </div>
      </div>
      <div class="cockpit-header__right">
        <el-button type="primary" plain @click="handleClone">
          <el-icon><CopyDocument /></el-icon> 克隆课程
        </el-button>
        <el-button @click="$router.push('/teacher/courses')">
          <el-icon><Plus /></el-icon> 新建课程
        </el-button>
      </div>
    </div>

    <!-- 二级导航 -->
    <div class="cockpit-tabs">
      <router-link
        v-for="tab in tabs"
        :key="tab.path"
        :to="`/teacher/courses/${courseId}/${tab.path}`"
        class="cockpit-tab"
        :class="{ 'is-active': activeTab === tab.path }"
      >
        <el-icon v-if="tab.icon"><component :is="tab.icon" /></el-icon>
        <span>{{ tab.label }}</span>
      </router-link>
    </div>

    <!-- 嵌套视图 -->
    <div class="cockpit-body">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { unwrap } from '@/utils/response'
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import courseApi from '@/api/course'
import teachingApi from '@/api/teacher'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  CopyDocument,
  Plus,
  DataLine,
  Files,
  List,
  DocumentChecked,
  Collection,
  Medal,
  Trophy,
  TrendCharts,
  Pointer,
  ChatDotRound,
} from '@element-plus/icons-vue'

const route = useRoute()
const courseId = computed(() => route.params.id)

const courseName = ref('')
const courseCode = ref('')

const tabs = [
  { path: 'overview', label: '课程概览', icon: DataLine },
  { path: 'chapters', label: '章节与任务点', icon: Files },
  { path: 'tasks', label: '作业任务', icon: List },
  { path: 'submissions', label: '作业批改', icon: DocumentChecked },
  { path: 'bank', label: '课程题库', icon: Collection },
  { path: 'exams', label: '试卷与考试', icon: Medal },
  { path: 'proctor', label: '在线监考', icon: Trophy },
  { path: 'grades', label: '过程性成绩', icon: TrendCharts },
  { path: 'analytics', label: '学情统计', icon: TrendCharts },
  { path: 'interaction', label: '课堂互动', icon: Pointer },
  { path: 'discussion', label: '课程讨论', icon: ChatDotRound },
]

const activeTab = computed(() => {
  const seg = route.path.split('/').filter(Boolean)
  return seg[seg.length - 1] || 'overview'
})

async function loadCourse() {
  try {
    const res = await courseApi.getCourseById(courseId.value)
    const c = unwrap(res)
    courseName.value = c?.name || ''
    courseCode.value = c?.code || ''
  } catch (e) {
    courseName.value = ''
  }
}

async function handleClone() {
  try {
    const newName = await ElMessageBox.prompt('请输入克隆后的课程名称', '克隆课程', {
      inputValue: courseName.value ? `${courseName.value}（副本）` : '',
      confirmButtonText: '克隆',
      cancelButtonText: '取消',
    })
      .then((r) => r.value)
      .catch(() => null)
    if (!newName) return
    await teachingApi.cloneCourse(courseId.value, newName)
    ElMessage.success('课程已克隆，状态为草稿')
    $router.push('/teacher/courses')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('克隆失败：' + (e.message || e))
  }
}

onMounted(loadCourse)
</script>

<style scoped>
.course-cockpit {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-md);
}

.cockpit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.cockpit-header__left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.back-btn {
  color: var(--ac-text-secondary);
}
.cockpit-title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  color: var(--ac-text-primary);
}
.cockpit-sub {
  font-size: 12.5px;
  color: var(--ac-text-muted);
}

/* 二级导航 */
.cockpit-tabs {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding: 6px;
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
  scrollbar-width: thin;
}
.cockpit-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--ac-radius-md);
  font-size: 13.5px;
  font-weight: 500;
  color: var(--ac-text-secondary);
  white-space: nowrap;
  text-decoration: none;
  transition: all var(--ac-transition-fast);
}
.cockpit-tab:hover {
  background: var(--ac-bg-hover);
  color: var(--ac-text-primary);
}
.cockpit-tab.is-active {
  background: var(--ac-sidebar-active-bg);
  color: var(--ac-sidebar-active-text);
  font-weight: 600;
}
.cockpit-body {
  min-height: 320px;
}
</style>
