<template>
  <div v-if="dailyTasks.length > 0" class="daily-tasks-panel">
    <h3 class="section-label">
      <el-icon><Calendar /></el-icon> 今日任务
    </h3>
    <div class="task-list">
      <div
        v-for="task in dailyTasks"
        :key="task.id"
        class="task-item"
        :class="{ done: task.completed }"
      >
        <div class="task-check" @click="$emit('toggle', task)">
          <el-icon v-if="task.completed"><Select /></el-icon>
        </div>
        <div class="task-content">
          <span class="task-title">{{ task.title }}</span>
          <span v-if="task.knowledgePointName" class="task-hint">{{
            task.knowledgePointName
          }}</span>
        </div>
        <el-tag
          size="small"
          :type="
            task.priority === 'HIGH' ? 'danger' : task.priority === 'MEDIUM' ? 'warning' : 'info'
          "
        >
          {{ task.priority === 'HIGH' ? '重要' : task.priority === 'MEDIUM' ? '一般' : '可选' }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import { Calendar, Select } from '@element-plus/icons-vue'

/** 今日任务（useLearningPath.loadDailyTasks 映射的元素） */
type DailyTask = {
  id: number | string
  title?: string
  completed?: boolean
  priority?: string
  knowledgePointName?: string
}

/**
 * 学习路径 — 今日任务面板
 * <p>优先级标识 + 勾选完成交互。</p>
 */
defineProps({
  /** 今日任务列表（id/title/completed/priority/knowledgePointName） */
  dailyTasks: { type: Array as PropType<DailyTask[]>, required: true },
})

defineEmits(['toggle'])
</script>

<style scoped>
.daily-tasks-panel {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
}

.section-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0 0 var(--ac-spacing-md);
  display: flex;
  align-items: center;
  gap: 6px;
}

.task-list {
  margin-top: var(--ac-spacing-sm);
}

.task-item {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  padding: 10px 12px;
  border-radius: var(--ac-radius-sm);
  border: 1px solid var(--ac-border);
  margin-bottom: 6px;
  transition: all var(--ac-transition-fast);
  background: var(--ac-bg);
}

.task-item:hover {
  background: var(--ac-bg-hover);
}

.task-item.done {
  opacity: 0.6;
}

.task-item.done .task-title {
  text-decoration: line-through;
  color: var(--ac-text-muted);
}

.task-check {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid var(--ac-border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: all var(--ac-transition-fast);
  font-size: 12px;
  color: var(--ac-success);
}

.task-check:hover {
  border-color: var(--ac-success);
  background: rgba(139, 168, 136, 0.1);
}

.task-content {
  flex: 1;
  min-width: 0;
}

.task-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--ac-text-primary);
  display: block;
}

.task-hint {
  font-size: 11px;
  color: var(--ac-text-muted);
  display: block;
  margin-top: 2px;
}
</style>
