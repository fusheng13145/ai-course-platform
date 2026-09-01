<template>
  <el-drawer
    :model-value="modelValue"
    :title="detail.title || '话题详情'"
    size="460px"
    @update:model-value="$emit('update:modelValue', $event)"
    @closed="emit('closed')"
  >
    <div v-loading="detailLoading">
      <div class="detail-head">
        <div class="detail-content">{{ detail.content }}</div>
        <div class="detail-meta">
          <span
            ><el-icon><User /></el-icon>{{ detail.authorName || '匿名' }}</span
          >
          <span class="time">{{ detail.createdAt || '—' }}</span>
        </div>
        <div class="detail-actions">
          <el-button
            size="small"
            :type="detail.pinned ? 'warning' : 'default'"
            :icon="Top"
            @click="emit('toggle-pin')"
            >{{ detail.pinned ? '取消置顶' : '置顶' }}</el-button
          >
          <el-button
            size="small"
            :type="detail.closed ? 'success' : 'danger'"
            :icon="detail.closed ? Check : CircleClose"
            @click="emit('toggle-close')"
            >{{ detail.closed ? '重新打开' : '关闭话题' }}</el-button
          >
        </div>
      </div>

      <el-divider>帖子 ({{ posts.length }})</el-divider>

      <div v-if="posts.length" class="post-list">
        <div v-for="p in posts" :key="p.id" class="post-item">
          <div class="post-head">
            <span class="post-author"
              ><el-icon><User /></el-icon>{{ p.authorName || '匿名' }}</span
            >
            <span class="time">{{ p.createdAt || '—' }}</span>
          </div>
          <div class="post-content">{{ p.content }}</div>
          <div class="post-foot">
            <el-button link type="primary" :icon="Pointer" @click="emit('like', p)"
              >赞 {{ p.likes || 0 }}</el-button
            >
            <el-popconfirm title="确定删除该帖子？" @confirm="emit('del-post', p)">
              <template #reference>
                <el-button link type="danger" :icon="Delete">删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无回复" :image-size="60" />

      <div class="reply-box">
        <el-input
          :model-value="replyText"
          type="textarea"
          :rows="3"
          placeholder="输入回复内容…"
          @update:model-value="$emit('update:replyText', $event)"
        />
        <el-button
          type="primary"
          :icon="Promotion"
          :loading="replyLoading"
          class="reply-send"
          @click="emit('send-reply')"
          >发送回复</el-button
        >
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { User, Top, Pointer, Delete, Promotion, Check, CircleClose } from '@element-plus/icons-vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  detailLoading: { type: Boolean, default: false },
  detail: { type: Object, default: () => ({}) },
  posts: { type: Array, default: () => [] },
  replyText: { type: String, default: '' },
  replyLoading: { type: Boolean, default: false },
})
const emit = defineEmits([
  'update:modelValue',
  'closed',
  'toggle-pin',
  'toggle-close',
  'like',
  'del-post',
  'send-reply',
  'update:replyText',
])
</script>

<style scoped>
.detail-head {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--ac-border);
}
.detail-content {
  color: var(--ac-text-primary);
  font-size: 14px;
  line-height: 1.7;
}
.detail-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 8px;
}
.detail-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.post-item {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
}
.post-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--ac-text-muted);
}
.post-author {
  color: var(--ac-text-secondary);
  font-weight: 600;
}
.post-content {
  margin: 8px 0;
  font-size: 13.5px;
  color: var(--ac-text-primary);
  line-height: 1.6;
}
.post-foot {
  display: flex;
  gap: 14px;
}

.reply-box {
  margin-top: 16px;
}
.reply-send {
  margin-top: 10px;
}
.time {
  color: var(--ac-text-muted);
}
</style>
