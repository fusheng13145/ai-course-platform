<template>
  <div>
    <el-alert
      v-if="enableCourseGate && !courseId"
      title="请选择课程"
      type="info"
      :closable="false"
      show-icon
    >
      <template #default>选择一门课程后，即可查看该课程的讨论话题。</template>
    </el-alert>

    <template v-else>
      <!-- 工具栏（学生端）：精华筛选 + 搜索 + 刷新 -->
      <div v-if="showToolbar" class="toolbar">
        <el-radio-group
          :model-value="essenceFilter"
          @update:model-value="$emit('update:essenceFilter', $event)"
          @change="$emit('load')"
        >
          <el-radio-button :value="''">全部</el-radio-button>
          <el-radio-button :value="true">精华</el-radio-button>
        </el-radio-group>
        <el-input
          :model-value="keyword"
          placeholder="搜索话题"
          clearable
          style="width: 220px"
          :prefix-icon="Search"
          @update:model-value="$emit('update:keyword', $event)"
          @keyup.enter="$emit('load')"
          @clear="$emit('load')"
        />
        <el-button :disabled="topicLoading" @click="$emit('refresh')">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>

      <!-- 话题列表 -->
      <div v-loading="topicLoading" class="chart-card">
        <div class="chart-title">
          <el-icon><Comment /></el-icon>话题列表
          <el-tag size="small" type="info" effect="light" round>{{ topics.length }}</el-tag>
        </div>

        <div v-if="topics.length" class="topic-list">
          <div v-for="t in topics" :key="t.id" class="topic-item" @click="$emit('open-detail', t)">
            <div class="ti-main">
              <div class="ti-top">
                <el-tag
                  v-if="showEssence && t.isEssence"
                  size="small"
                  class="ti-essence"
                  effect="light"
                >
                  <el-icon><Star /></el-icon>精华
                </el-tag>
                <el-tag v-if="t.pinned" size="small" type="danger" effect="light">置顶</el-tag>
                <span class="ti-title">{{ t.title }}</span>
                <el-tag
                  size="small"
                  :type="t.status === 'CLOSED' ? 'info' : 'success'"
                  effect="light"
                >
                  {{ t.status === 'CLOSED' ? '已关闭' : '进行中' }}
                </el-tag>
              </div>
              <p class="ti-content">{{ t.content }}</p>
              <div class="ti-meta">
                <span
                  ><el-icon><User /></el-icon>{{ t.authorName || authorFallback }}</span
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
        <el-empty v-else :description="emptyText" :image-size="70">
          <template v-if="showToolbar" #image><div class="empty-deco">💬</div></template>
        </el-empty>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import {
  Search,
  Refresh,
  Comment,
  User,
  ChatLineRound,
  Collection,
  ArrowRight,
  Star,
} from '@element-plus/icons-vue'

/**
 * 讨论话题列表（讨论域共享实现）
 * <p>学生端为超集（精华筛选/搜索/刷新工具栏 + 课程门控 + 精华标识），
 * 教师端为其子集；角色差异以 props 表达：
 * `showToolbar` / `showEssence` / `enableCourseGate` / `authorFallback` / `emptyText`。</p>
 */
defineProps({
  /** 话题列表 */
  topics: { type: Array, default: () => [] },
  /** 话题加载态 */
  topicLoading: { type: Boolean, default: false },
  /** 当前课程（课程门控开启且为空时提示选择） */
  courseId: { type: [String, Number], default: '' },
  /** 精华筛选（v-model：'' 或 true） */
  essenceFilter: { type: [String, Boolean], default: '' },
  /** 搜索关键字（v-model） */
  keyword: { type: String, default: '' },
  /** 是否显示工具栏（学生端为 true） */
  showToolbar: { type: Boolean, default: false },
  /** 是否显示精华标识（学生端为 true） */
  showEssence: { type: Boolean, default: false },
  /** 是否启用课程门控（学生端为 true） */
  enableCourseGate: { type: Boolean, default: false },
  /** 作者名缺省文案（学生端「同学」/ 教师端「教师」） */
  authorFallback: { type: String, default: '同学' },
  /** 空态文案 */
  emptyText: { type: String, default: '暂无话题，点击「发起讨论」开始吧' },
})

defineEmits(['update:essenceFilter', 'update:keyword', 'load', 'refresh', 'open-detail'])
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: var(--ac-spacing-md);
  flex-wrap: wrap;
  align-items: center;
}

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
.ti-essence {
  color: var(--ac-warning);
  background: var(--ac-warning-bg);
  border-color: transparent;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
</style>
