<template>
  <aside class="chat-sidebar" :class="{ collapsed: !showSidebar }">
    <div class="sidebar-header">
      <h3 class="sidebar-title">对话历史</h3>
      <el-button text size="small" @click="emit('new-chat')">
        <el-icon><Plus /></el-icon> 新建
      </el-button>
    </div>
    <div class="conversation-list">
      <div
        v-for="conv in conversations"
        :key="conv.id"
        class="conversation-item"
        :class="{ active: currentConvId === conv.id }"
        @click="emit('switch', conv.id)"
      >
        <el-icon class="conv-icon"><ChatDotRound /></el-icon>
        <span class="conv-title">{{ conv.title || '新对话' }}</span>
        <span class="conv-time">{{ formatTime(conv.time) }}</span>
      </div>
      <el-empty v-if="conversations.length === 0" description="暂无历史对话" :image-size="60" />
    </div>
  </aside>
</template>

<script setup lang="ts">
import { Plus, ChatDotRound } from '@element-plus/icons-vue'

/**
 * AI 助手对话历史侧栏
 * <p>会话列表（标题/相对时间/当前高亮）+ 新建；切换经事件上抛。</p>
 */
defineProps({
  showSidebar: { type: Boolean, default: true },
  conversations: { type: Array, default: () => [] },
  currentConvId: { type: [Number, String], default: null },
  formatTime: { type: Function, required: true },
})

const emit = defineEmits(['new-chat', 'switch'])
</script>

<style scoped>
.chat-sidebar {
  width: 240px;
  flex-shrink: 0;
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
}
.chat-sidebar.collapsed {
  width: 0;
  border: none;
}
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--ac-spacing-md) var(--ac-spacing-md);
  border-bottom: 1px solid var(--ac-border);
}
.sidebar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}
.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--ac-spacing-sm);
}
.conversation-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 10px;
  border-radius: var(--ac-radius-md);
  cursor: pointer;
  transition: background var(--ac-transition-fast);
}
.conversation-item:hover {
  background: var(--ac-bg-hover);
}
.conversation-item.active {
  background: var(--ac-primary-bg);
}
.conv-icon {
  color: var(--ac-text-muted);
  flex-shrink: 0;
}
.conv-title {
  flex: 1;
  font-size: 13px;
  color: var(--ac-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-time {
  font-size: 11px;
  color: var(--ac-text-muted);
  flex-shrink: 0;
}
</style>
