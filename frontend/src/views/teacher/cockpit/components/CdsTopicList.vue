<template>
  <div v-loading="loading">
    <!-- 筛选 -->
    <div class="toolbar">
      <div class="filters">
        <el-input
          :model-value="keyword"
          placeholder="搜索话题"
          clearable
          class="f-keyword"
          :prefix-icon="Search"
          @update:model-value="$emit('update:keyword', $event)"
          @keyup.enter="emit('search')"
          @clear="emit('search')"
        />
        <el-input
          :model-value="chapterId"
          placeholder="章节 ID（可选）"
          clearable
          class="f-chapter"
          @update:model-value="$emit('update:chapterId', $event)"
          @keyup.enter="emit('search')"
          @clear="emit('search')"
        />
        <el-button :icon="Search" @click="emit('search')">筛选</el-button>
      </div>
    </div>

    <!-- 话题列表 -->
    <div class="l1-card">
      <div class="l1-head">
        <span class="l1-ico"
          ><el-icon><ChatLineSquare /></el-icon
        ></span>
        <div>
          <div class="l1-title">课程讨论话题</div>
          <div class="l1-sub">{{ topics.length }} 个</div>
        </div>
      </div>
      <div class="l1-body">
        <div v-if="topics.length" class="topic-list">
          <div
            v-for="t in topics"
            :key="t.id"
            class="topic-row"
            :class="{ 'is-pinned': t.pinned }"
            @click="emit('open-detail', t)"
          >
            <div class="topic-main">
              <div class="topic-title">
                <el-tag v-if="t.pinned" size="small" type="warning" effect="light" round
                  >置顶</el-tag
                >
                <el-tag v-if="t.closed" size="small" type="info" effect="light" round
                  >已关闭</el-tag
                >
                <span>{{ t.title }}</span>
              </div>
              <div class="topic-content">{{ t.content }}</div>
              <div class="topic-meta">
                <span
                  ><el-icon><User /></el-icon>{{ t.authorName || '匿名' }}</span
                >
                <span
                  ><el-icon><ChatDotRound /></el-icon>{{ t.replyCount || 0 }} 回复</span
                >
                <span v-if="t.chapterId"
                  ><el-icon><Files /></el-icon>章节 {{ t.chapterId }}</span
                >
                <span class="time">{{ t.createdAt || '—' }}</span>
              </div>
            </div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
        </div>
        <el-empty v-else :description="emptyText" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  Search,
  User,
  ChatLineSquare,
  ChatDotRound,
  Files,
  ArrowRight,
} from '@element-plus/icons-vue'

withDefaults(
  defineProps<{
    loading?: boolean
    topics?: DiscussionTopic[]
    keyword?: string
    chapterId?: string
    /** 空态文案（失败时由页面传入「未能加载」文案，默认保留原语义） */
    emptyText?: string
  }>(),
  {
    loading: false,
    topics: () => [],
    keyword: '',
    chapterId: '',
    emptyText: '暂无讨论话题',
  },
)
const emit = defineEmits<{
  'update:keyword': [value: string]
  'update:chapterId': [value: string]
  search: []
  'open-detail': [topic: DiscussionTopic]
}>()
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: var(--ac-spacing-md);
  flex-wrap: wrap;
}
.filters {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.f-keyword {
  width: 220px;
}
.f-chapter {
  width: 180px;
}

.topic-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.topic-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
  cursor: pointer;
  transition:
    border-color var(--ac-transition-fast),
    transform var(--ac-transition-fast);
}
.topic-row:hover {
  border-color: var(--ac-primary-light);
  transform: translateX(2px);
}
.topic-row.is-pinned {
  border-color: var(--ac-warning-bg);
}
.topic-main {
  flex: 1;
  min-width: 0;
}
.topic-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 14px;
}
.topic-content {
  margin: 6px 0;
  font-size: 13px;
  color: var(--ac-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.topic-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--ac-text-muted);
  flex-wrap: wrap;
}
.topic-meta .el-icon {
  margin-right: 3px;
}
.arrow {
  color: var(--ac-text-muted);
}
.time {
  color: var(--ac-text-muted);
}
</style>
