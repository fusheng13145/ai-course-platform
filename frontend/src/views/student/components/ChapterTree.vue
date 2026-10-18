<template>
  <div class="chapter-tree" :style="{ paddingLeft: depth > 0 ? '16px' : '0' }">
    <div
      v-for="chapter in nodes"
      :key="chapter.id"
      class="chapter-node"
      :class="{ 'is-locked': !chapter.unlocked }"
    >
      <!-- 章节头部 -->
      <div class="chapter-head" @click="toggle(chapter)">
        <el-icon class="caret" :class="{ open: isOpen(chapter.id) }"><ArrowRight /></el-icon>
        <span v-if="!chapter.unlocked" class="lock-badge" title="完成前置章节后解锁">
          <el-icon><Lock /></el-icon>
        </span>
        <span class="chapter-title">{{ chapter.title }}</span>
        <span class="chapter-count"> {{ chapter.completedTasks }}/{{ chapter.totalTasks }} </span>
        <el-progress
          class="chapter-progress"
          :percentage="
            chapter.totalTasks
              ? Math.round((chapter.completedTasks * 100) / chapter.totalTasks)
              : 100
          "
          :stroke-width="6"
          :show-text="false"
          :color="chapter.allCompleted ? 'var(--ac-success)' : 'var(--ac-primary)'"
        />
      </div>

      <!-- 任务点列表 + 子章节 -->
      <div v-show="isOpen(chapter.id)" class="chapter-body">
        <div
          v-for="task in chapter.tasks"
          :key="task.id"
          class="task-row"
          :class="{ 'is-locked': !task.unlocked, 'is-done': task.completed }"
          @click="onTaskClick(task)"
        >
          <span class="task-icon" :style="iconStyle(task)">
            <el-icon><component :is="taskIcon(task.taskType)" /></el-icon>
          </span>
          <span class="task-title">{{ task.title }}</span>
          <span class="task-type-tag">{{ taskTypeLabel(task.taskType) }}</span>
          <span v-if="task.completed" class="task-done">
            <el-icon><Select /></el-icon> 已完成
          </span>
          <span v-else-if="!task.unlocked" class="task-locked">
            <el-icon><Lock /></el-icon> 未解锁
          </span>
          <el-icon v-else class="task-go"><ArrowRight /></el-icon>
        </div>

        <EmptyState v-if="chapter.tasks.length === 0" title="本章暂无任务点" icon="list" size="compact" />

        <!-- 递归子章节 -->
        <chapter-tree
          v-if="chapter.children && chapter.children.length"
          :nodes="chapter.children"
          :depth="depth + 1"
          @open-task="$emit('open-task', $event)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import EmptyState from '@/components/ui/EmptyState.vue'
import {
  ArrowRight,
  Lock,
  Select,
  VideoPlay,
  Document,
  Reading,
  EditPen,
  Files,
  Notebook,
} from '@element-plus/icons-vue'

/** 章节节点（children 为递归同型；tasks 仅整体迭代与随 open-task 事件上抛） */
interface ChapterNode {
  id?: number | string
  unlocked?: boolean
  title?: string
  completedTasks?: number
  totalTasks?: number
  allCompleted?: boolean
  tasks?: unknown[]
  children?: ChapterNode[]
}

const props = defineProps({
  nodes: { type: Array as PropType<ChapterNode[]>, default: () => [] },
  depth: { type: Number, default: 0 },
})
const emit = defineEmits(['open-task'])

/* 展开/折叠状态（默认展开顶层，折叠子层） */
const openSet = ref(new Set(props.depth === 0 ? props.nodes.map((n) => n.id) : []))
function isOpen(id) {
  return openSet.value.has(id)
}
function toggle(chapter) {
  const s = new Set(openSet.value)
  s.has(chapter.id) ? s.delete(chapter.id) : s.add(chapter.id)
  openSet.value = s
}

/* 任务点点击：仅解锁态可进入 */
function onTaskClick(task) {
  if (!task.unlocked) {
    ElMessage.warning('请先完成前面的任务点')
    return
  }
  emit('open-task', task)
}

/* 任务类型 → 图标 / 文案 / 颜色 */
function taskIcon(type) {
  switch (type) {
    case 'VIDEO':
      return VideoPlay
    case 'DOC':
    case 'DOCUMENT':
      return Document
    case 'READING':
      return Reading
    case 'QUIZ':
      return EditPen
    case 'ASSIGNMENT':
      return Files
    default:
      return Notebook
  }
}
function taskTypeLabel(type) {
  switch (type) {
    case 'VIDEO':
      return '视频'
    case 'DOC':
    case 'DOCUMENT':
      return '文档'
    case 'READING':
      return '阅读'
    case 'QUIZ':
      return '测验'
    case 'ASSIGNMENT':
      return '作业'
    default:
      return type || '任务'
  }
}
function iconStyle(task) {
  const map = {
    VIDEO: { bg: 'var(--ac-primary-bg)', color: 'var(--ac-primary)' },
    DOC: { bg: 'var(--ac-info-bg)', color: 'var(--ac-info)' },
    DOCUMENT: { bg: 'var(--ac-info-bg)', color: 'var(--ac-info)' },
    READING: { bg: 'var(--ac-success-bg)', color: 'var(--ac-success)' },
    QUIZ: { bg: 'var(--ac-warning-bg)', color: 'var(--ac-warning)' },
    ASSIGNMENT: { bg: 'var(--ac-danger-bg)', color: 'var(--ac-danger)' },
  }
  return map[task.taskType] || { bg: 'var(--ac-bg-hover)', color: 'var(--ac-text-muted)' }
}
</script>

<style scoped>
.chapter-tree {
  font-size: 14px;
}

.chapter-node {
  border-radius: var(--ac-radius-md);
  margin-bottom: var(--ac-spacing-xs);
  transition: background var(--ac-transition-fast);
}
.chapter-node.is-locked {
  opacity: 0.72;
}

.chapter-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--ac-radius-md);
  cursor: pointer;
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  transition: all var(--ac-transition-normal);
}
.chapter-head:hover {
  border-color: var(--ac-primary-light);
  box-shadow: var(--ac-shadow-sm);
}

.caret {
  transition: transform var(--ac-transition-fast);
  color: var(--ac-text-muted);
  flex-shrink: 0;
}
.caret.open {
  transform: rotate(90deg);
}

.lock-badge {
  color: var(--ac-text-muted);
  display: inline-flex;
  flex-shrink: 0;
}

.chapter-title {
  font-weight: 600;
  color: var(--ac-text-primary);
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chapter-count {
  font-size: 12px;
  color: var(--ac-text-muted);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.chapter-progress {
  width: 90px;
  flex-shrink: 0;
}

.chapter-body {
  padding: 6px 0 6px 22px;
}

.task-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: var(--ac-radius-sm);
  cursor: pointer;
  transition: all var(--ac-transition-fast);
  border: 1px solid transparent;
}
.task-row:hover:not(.is-locked) {
  background: var(--ac-bg-hover);
  border-color: var(--ac-primary-light);
}
.task-row.is-locked {
  cursor: not-allowed;
  opacity: 0.6;
}
.task-row.is-done {
  background: var(--ac-success-bg);
}

.task-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.task-title {
  flex: 1;
  color: var(--ac-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.task-type-tag {
  font-size: 11px;
  color: var(--ac-text-muted);
  background: var(--ac-bg);
  border: 1px solid var(--ac-border);
  border-radius: 999px;
  padding: 1px 8px;
  flex-shrink: 0;
}

.task-done {
  color: var(--ac-success);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
.task-locked {
  color: var(--ac-text-muted);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
.task-go {
  color: var(--ac-primary);
  flex-shrink: 0;
}
</style>
