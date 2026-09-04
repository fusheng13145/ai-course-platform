<template>
  <div v-loading="topicLoading" class="chart-card">
    <div class="chart-title">
      <el-icon><Comment /></el-icon>话题列表
      <el-tag size="small" type="info" effect="light" round>{{ topics.length }}</el-tag>
    </div>

    <div v-if="topics.length" class="topic-list">
      <div v-for="t in topics" :key="t.id" class="topic-item" @click="$emit('open-detail', t)">
        <div class="ti-main">
          <div class="ti-top">
            <el-tag v-if="t.pinned" size="small" type="danger" effect="light">置顶</el-tag>
            <span class="ti-title">{{ t.title }}</span>
            <el-tag size="small" :type="t.status === 'CLOSED' ? 'info' : 'success'" effect="light">
              {{ t.status === 'CLOSED' ? '已关闭' : '进行中' }}
            </el-tag>
          </div>
          <p class="ti-content">{{ t.content }}</p>
          <div class="ti-meta">
            <span
              ><el-icon><User /></el-icon>{{ t.authorName || '教师' }}</span
            >
            <span
              ><el-icon><ChatLineRound /></el-icon>{{ t.postCount || 0 }} 条回复</span
            >
            <span v-if="t.chapterName"
              ><el-icon><Collection /></el-icon>{{ t.chapterName }}</span
            >
            <span class="ti-time">{{ t.createdAt }}</span>
          </div>
        </div>
        <el-icon class="ti-arrow"><ArrowRight /></el-icon>
      </div>
    </div>
    <el-empty v-else description="暂无话题，点击「发布话题」" :image-size="70" />
  </div>
</template>

<script setup lang="ts">
import { Comment, User, ChatLineRound, Collection, ArrowRight } from '@element-plus/icons-vue'

defineProps({
  topics: { type: Array, default: () => [] },
  topicLoading: { type: Boolean, default: false },
})
defineEmits(['open-detail'])
</script>

<style scoped>
.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
}
.chart-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}
.topic-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.topic-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    transform 0.2s ease;
}
.topic-item:hover {
  border-color: var(--ac-primary-light);
  transform: translateY(-2px);
}
.ti-main {
  flex: 1;
  min-width: 0;
}
.ti-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ti-title {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 14.5px;
}
.ti-content {
  margin: 6px 0 8px;
  font-size: 13px;
  color: var(--ac-text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.ti-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: var(--ac-text-muted);
  flex-wrap: wrap;
}
.ti-meta .el-icon {
  margin-right: 3px;
}
.ti-time {
  margin-left: auto;
}
.ti-arrow {
  color: var(--ac-text-muted);
}
</style>
