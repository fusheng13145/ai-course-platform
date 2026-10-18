<template>
  <!-- 话题详情 -->
  <el-dialog
    ref="dialogRef"
    :model-value="modelValue"
    :title="currentTopic?.title || '话题详情'"
    width="680px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="currentTopic" class="detail-head">
      <div class="dh-content">{{ currentTopic.content }}</div>
      <div class="dh-actions">
        <el-button
          text
          size="small"
          :type="currentTopic.pinned ? 'danger' : 'primary'"
          @click="$emit('toggle-pin')"
        >
          <el-icon><Top /></el-icon>{{ currentTopic.pinned ? '取消置顶' : '置顶' }}
        </el-button>
        <el-button
          text
          size="small"
          type="warning"
          :disabled="currentTopic.status === 'CLOSED'"
          @click="$emit('close-topic')"
        >
          <el-icon><SwitchButton /></el-icon>关闭话题
        </el-button>
      </div>
    </div>

    <el-divider>回复（{{ posts.length }}）</el-divider>

    <div v-loading="postLoading" class="post-list">
      <div v-for="p in posts" :key="p.id" class="post-item">
        <div class="pi-head">
          <span class="pi-author">{{ p.authorName || '匿名' }}</span>
          <span class="pi-time">{{ p.createdAt }}</span>
        </div>
        <p class="pi-content">{{ p.content }}</p>
        <div class="pi-foot">
          <el-button
            text
            size="small"
            :type="p.liked ? 'primary' : 'default'"
            @click="$emit('like', p)"
          >
            <el-icon><Pointer /></el-icon>{{ p.liked ? '已赞' : '赞' }} {{ p.likeCount || 0 }}
          </el-button>
          <el-button text size="small" type="danger" @click="$emit('remove-post', p)"
            >删除</el-button
          >
        </div>
      </div>
      <EmptyState
        v-if="!postLoading && posts.length === 0"
        title="暂无回复"
        icon="message"
        size="compact"
      />
    </div>

    <div class="reply-box">
      <el-input
        :model-value="replyText"
        type="textarea"
        :rows="2"
        placeholder="输入回复内容…"
        maxlength="1000"
        @update:model-value="$emit('update:replyText', $event)"
      />
      <el-button type="primary" :loading="replySubmitting" @click="$emit('submit-reply')"
        >回复</el-button
      >
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Top, SwitchButton, Pointer } from '@element-plus/icons-vue'
import { useDialogFocus } from '@/composables/useDialogFocus'
import EmptyState from '@/components/ui/EmptyState.vue'

const props = withDefaults(
  defineProps<{
    modelValue?: boolean
    currentTopic?: DiscussionTopic | null
    posts?: DiscussionPost[]
    postLoading?: boolean
    replyText?: string
    replySubmitting?: boolean
  }>(),
  {
    modelValue: false,
    currentTopic: null,
    posts: () => [],
    postLoading: false,
    replyText: '',
    replySubmitting: false,
  },
)
const dialogRef = ref(null)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
useDialogFocus(dialogRef, computed(() => props.modelValue))
defineEmits<{
  'update:modelValue': [value: boolean]
  'update:replyText': [value: string]
  'toggle-pin': []
  'close-topic': []
  like: [post: DiscussionPost]
  'remove-post': [post: DiscussionPost]
  'submit-reply': []
}>()
</script>

<style scoped>
.detail-head {
  margin-bottom: 6px;
}
.dh-content {
  font-size: 13.5px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  white-space: pre-wrap;
}
.dh-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
.post-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 320px;
  overflow-y: auto;
}
.post-item {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
}
.pi-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pi-author {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 13.5px;
}
.pi-time {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-left: auto;
}
.pi-content {
  margin: 8px 0 10px;
  font-size: 13.5px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  white-space: pre-wrap;
}
.pi-foot {
  display: flex;
  gap: 12px;
}
.reply-box {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  margin-top: 14px;
}
.reply-box .el-input {
  flex: 1;
}
</style>
