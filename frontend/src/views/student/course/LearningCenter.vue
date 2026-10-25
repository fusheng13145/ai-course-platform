<template>
  <div v-loading="loading" class="fade-in">
    <!-- 头部：课程信息 + 整体进度 -->
    <div class="lc-header">
      <el-page-header :title="'返回课程'" @back="$router.push(`/student/courses/${courseId}`)">
        <template #content>
          <div class="lc-title">
            <h1>{{ center.courseName || '学习中心' }}</h1>
            <el-tag size="small" type="primary" effect="light">章节任务点学习中心</el-tag>
          </div>
        </template>
      </el-page-header>

      <div class="lc-progress">
        <div class="lc-progress-num">
          <span class="done">{{ loadError ? '--' : center.completedTasks }}</span>
          <span class="sep">/</span>
          <span class="total">{{ loadError ? '--' : center.totalTasks }}</span>
          <span class="unit">个任务点</span>
        </div>
        <el-progress
          :percentage="loadError ? 0 : center.completionPercent || 0"
          :stroke-width="12"
          :color="center.completionPercent >= 100 ? 'var(--ac-success)' : 'var(--ac-primary)'"
          style="flex: 1; max-width: 420px"
        />
        <span class="lc-percent">{{
          loadError ? '--' : (center.completionPercent || 0) + '%'
        }}</span>
      </div>
    </div>

    <!-- 加载失败持久提示（不自动消失） -->
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      class="load-error-alert"
    />

    <!-- 章节任务点树 -->
    <div class="lc-tree-card">
      <div class="lc-tree-head">
        <span class="lc-tree-title">课程章节</span>
        <span class="lc-tree-hint">按顺序解锁 · 完成前置任务点方可进入后续内容</span>
      </div>
      <EmptyState
        v-if="!loading && center.chapters.length === 0"
        :title="loadError ? '学习中心内容未能加载，请查看上方提示后重试' : '本课程暂无章节任务点'"
        icon="layers"
      />
      <chapter-tree v-else :nodes="center.chapters" @open-task="openTask" />
    </div>

    <!-- 任务点抽屉（视频/文档/作业/测验交互已下沉至 TaskDrawer 子组件） -->
    <TaskDrawer
      v-model="drawerVisible"
      :task="activeTask"
      :course-id="courseId"
      @task-completed="load"
    />
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import EmptyState from '@/components/ui/EmptyState.vue'
import { unwrap } from '@/utils/response'
import studentApi from '@/api/student'
import ChapterTree from '../components/ChapterTree.vue'
import TaskDrawer from './components/TaskDrawer.vue'

/**
 * 学生章节任务点学习中心
 * <p>页面壳：课程信息与整体进度 + 章节任务点树；任务点的展开与交互（视频续播/心跳、
 * 文档标记、作业跳转、测验作答）已下沉至 {@link TaskDrawer} 子组件，
 * 本组件仅负责数据加载与事件编排（@task-completed 触发进度刷新）。</p>
 */
const route = useRoute()
const courseId = route.params.id as string

/* ---- 页面状态 ---- */
const loading = ref(false)
const loadError = ref('')
const center = reactive({
  courseId: null,
  courseName: '',
  chapters: [],
  totalTasks: 0,
  completedTasks: 0,
  completionPercent: 0,
})

/* ---- 任务点抽屉 ---- */
const drawerVisible = ref(false)
const activeTask = ref(null)

/** 加载学习中心总览（课程信息 + 章节树 + 进度） */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await studentApi.getLearningCenter(courseId)
    const d = (unwrap(res) || {}) as {
      courseId?: number | string
      courseName?: string
      chapters?: unknown[]
      totalTasks?: number
      completedTasks?: number
      completionPercent?: number
    }
    center.courseId = d.courseId
    center.courseName = d.courseName
    center.chapters = d.chapters || []
    center.totalTasks = d.totalTasks || 0
    center.completedTasks = d.completedTasks || 0
    center.completionPercent = d.completionPercent || 0
  } catch (e) {
    logger.error('加载学习中心失败:', e)
    loadError.value = '学习中心加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

/** 打开任务点抽屉（数据加载由 TaskDrawer 内部按任务类型触发） */
function openTask(task) {
  activeTask.value = task
  drawerVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.lc-header {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  margin-bottom: var(--ac-spacing-md);
  box-shadow: var(--ac-shadow-sm);
}
.lc-title {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-left: var(--ac-spacing-md);
}
.lc-title h1 {
  font-size: 20px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0;
}
.lc-progress {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-md);
  margin-top: var(--ac-spacing-md);
  padding-top: var(--ac-spacing-md);
  border-top: 1px solid var(--ac-border);
}
.lc-progress-num {
  display: flex;
  align-items: baseline;
  gap: 4px;
  white-space: nowrap;
}
.lc-progress-num .done {
  font-size: 24px;
  font-weight: 700;
  color: var(--ac-primary);
}
.lc-progress-num .sep {
  color: var(--ac-text-muted);
}
.lc-progress-num .total {
  font-size: 18px;
  font-weight: 600;
  color: var(--ac-text-secondary);
}
.lc-progress-num .unit {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-left: 2px;
}
.lc-percent {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-primary);
  min-width: 44px;
  text-align: right;
}
.load-error-alert {
  margin-bottom: var(--ac-spacing-md);
}
.lc-tree-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  box-shadow: var(--ac-shadow-sm);
}
.lc-tree-head {
  display: flex;
  align-items: baseline;
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}
.lc-tree-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
  padding-left: 12px;
  border-left: 3px solid var(--ac-primary);
}
.lc-tree-hint {
  font-size: 12px;
  color: var(--ac-text-muted);
}
</style>
