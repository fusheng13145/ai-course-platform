<template>
  <div class="chapter-list">
    <div
      v-for="(ch, index) in chapters"
      :key="ch.id"
      class="chapter-item"
      @click="$emit('enter', ch)"
    >
      <div class="chapter-left">
        <div class="chapter-index" :class="{ completed: ch.completed, current: ch.current }">
          <el-icon v-if="ch.completed"><Select /></el-icon>
          <span v-else>{{ index + 1 }}</span>
        </div>
        <div class="chapter-info">
          <h4 class="chapter-title">{{ ch.title }}</h4>
          <p class="chapter-desc">{{ ch.description || '暂无描述' }}</p>
          <div class="chapter-meta">
            <span v-if="ch.duration" class="meta-tag">
              <el-icon><Timer /></el-icon> {{ ch.duration }}分钟
            </span>
            <el-tag v-if="ch.completed" type="success" size="small">已完成</el-tag>
            <el-tag v-else-if="ch.current" type="warning" size="small">当前章节</el-tag>
            <el-tag v-else type="info" size="small">未开始</el-tag>
          </div>
        </div>
      </div>
      <div class="chapter-right">
        <el-progress
          v-if="ch.progress !== undefined && ch.progress > 0"
          :percentage="ch.progress"
          :stroke-width="6"
          :show-text="false"
          style="width: 80px"
        />
        <el-button type="primary" text @click.stop="$emit('enter', ch)">
          {{ ch.completed ? '重新学习' : ch.current ? '继续学习' : '开始学习' }}
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
    </div>
    <EmptyState v-if="chapters.length === 0" :title="emptyText" icon="layers" />
  </div>
</template>

<script setup lang="ts">
import { Select, Timer, ArrowRight } from '@element-plus/icons-vue'
import EmptyState from '@/components/ui/EmptyState.vue'

/**
 * 章节学习 Tab — 章节列表视图
 * <p>章节进度/状态（已完成/当前/未开始）展示与进入学习入口。</p>
 */
defineProps({
  /** 章节列表（含 completed/current/progress 派生字段） */
  chapters: { type: Array, required: true },
  /** 空态文案（父页面按加载失败态透传诚实提示，默认保留原语义） */
  emptyText: { type: String, default: '暂无章节内容' },
})

defineEmits(['enter'])
</script>

<style scoped>
.chapter-list {
  max-height: 600px;
  overflow-y: auto;
}

.chapter-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--ac-spacing-md);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  margin-bottom: var(--ac-spacing-sm);
  cursor: pointer;
  transition: all var(--ac-transition-normal);
  background: var(--ac-bg-elevated);
}

.chapter-item:hover {
  transform: translateY(-1px);
  box-shadow: var(--ac-shadow-md);
  border-color: var(--ac-primary-light);
}

.chapter-left {
  display: flex;
  align-items: flex-start;
  gap: var(--ac-spacing-md);
  flex: 1;
}

.chapter-index {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
  background: var(--ac-bg);
  color: var(--ac-text-muted);
  border: 2px solid var(--ac-border);
  transition: all var(--ac-transition-normal);
}

.chapter-index.completed {
  background: rgba(139, 168, 136, 0.15);
  color: var(--ac-success);
  border-color: var(--ac-success);
}

.chapter-index.current {
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
  border-color: var(--ac-primary);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(122, 139, 165, 0.3);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(122, 139, 165, 0);
  }
}

.chapter-info {
  flex: 1;
}

.chapter-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0 0 4px;
}

.chapter-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin: 0 0 var(--ac-spacing-xs);
}

.chapter-meta {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  flex-wrap: wrap;
}

.meta-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--ac-text-muted);
}

.chapter-right {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  flex-shrink: 0;
  margin-left: var(--ac-spacing-md);
}
</style>
