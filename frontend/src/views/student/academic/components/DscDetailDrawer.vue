<template>
  <el-drawer
    :model-value="modelValue"
    :title="currentTopic?.title || '话题详情'"
    size="560px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="currentTopic" class="detail-head">
      <div class="dh-tags">
        <el-tag v-if="currentTopic.isEssence" size="small" class="ti-essence" effect="light">
          <el-icon><Star /></el-icon>精华
        </el-tag>
        <el-tag v-if="currentTopic.pinned" size="small" type="danger" effect="light">置顶</el-tag>
        <el-tag
          size="small"
          :type="currentTopic.status === 'CLOSED' ? 'info' : 'success'"
          effect="light"
        >
          {{ currentTopic.status === 'CLOSED' ? '已关闭' : '进行中' }}
        </el-tag>
      </div>
      <div class="dh-content">{{ currentTopic.content }}</div>
      <div class="dh-meta">
        <span
          ><el-icon><User /></el-icon>{{ currentTopic.authorName || '同学' }}</span
        >
        <span class="ti-time">{{ currentTopic.createdAt }}</span>
      </div>
    </div>

    <el-divider>回复（{{ posts.length }}）</el-divider>

    <div v-loading="postLoading" class="post-list">
      <div v-for="p in posts" :key="p.id" class="post-item">
        <div class="pi-head">
          <span v-if="p.floor" class="pi-floor">#{{ p.floor }}</span>
          <span class="pi-author">{{ p.authorName || '匿名' }}</span>
          <span class="pi-time">{{ p.createdAt }}</span>
          <el-tag v-if="p.mentions" size="small" type="warning" effect="light" class="pi-mention">
            <el-icon><Bell /></el-icon>已@通知
          </el-tag>
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
          <el-button v-if="canDelete(p)" text size="small" type="danger" @click="$emit('remove', p)"
            >删除</el-button
          >
        </div>
      </div>
      <EmptyState
        v-if="!postLoading && posts.length === 0"
        title="暂无回复，来抢沙发"
        icon="message"
        size="compact"
      />
    </div>

    <!-- 回复框（话题关闭后禁用） -->
    <div v-if="currentTopic && currentTopic.status !== 'CLOSED'" class="reply-box">
      <div v-if="participantOptions.length" class="reply-mention">
        <span class="rm-label">@提及：</span>
        <el-select
          :model-value="mentionIds"
          multiple
          collapse-tags
          placeholder="选择要通知的同学/老师"
          style="flex: 1"
          size="small"
          @update:model-value="$emit('update:mentionIds', $event)"
        >
          <el-option
            v-for="u in participantOptions"
            :key="u.id"
            :label="u.name"
            :value="u.id"
            :disabled="u.id === myUserId"
          />
        </el-select>
      </div>
      <div class="reply-input">
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
    </div>
    <el-alert
      v-else-if="currentTopic"
      title="该话题已关闭，无法继续回复"
      type="info"
      :closable="false"
      show-icon
    />
  </el-drawer>
</template>

<script setup lang="ts">
import { Star, User, Bell, Pointer } from '@element-plus/icons-vue'
import EmptyState from '@/components/ui/EmptyState.vue'

/**
 * 话题详情抽屉
 * <p>话题头（精华/置顶/状态）+ 回复列表（点赞/删除/楼层）+ 回复框（@提及）。</p>
 */
withDefaults(
  defineProps<{
    /** 抽屉显隐（v-model） */
    modelValue?: boolean
    /** 当前话题 */
    currentTopic?: DiscussionTopic | null
    /** 回复列表 */
    posts: DiscussionPost[]
    /** 回复加载态 */
    postLoading?: boolean
    /** @提及选中（v-model 数组） */
    mentionIds?: Array<number | string>
    /** 可@提及的参与者选项 */
    participantOptions?: Array<{ id: number | string; name: string }>
    /** 回复文本（v-model） */
    replyText?: string
    /** 回复提交加载态 */
    replySubmitting?: boolean
    /** 当前用户 id（禁选自己） */
    myUserId?: number | string | null
    /** 是否可删除该回复 */
    canDelete: (post: DiscussionPost) => boolean
  }>(),
  {
    modelValue: false,
    currentTopic: null,
    postLoading: false,
    mentionIds: () => [],
    participantOptions: () => [],
    replyText: '',
    replySubmitting: false,
    myUserId: null,
  },
)

defineEmits<{
  'update:modelValue': [value: boolean]
  'update:mentionIds': [value: Array<number | string>]
  'update:replyText': [value: string]
  like: [post: DiscussionPost]
  remove: [post: DiscussionPost]
  'submit-reply': []
}>()
</script>

<style scoped>
.detail-head {
  margin-bottom: 6px;
}
.dh-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}
.dh-content {
  font-size: 13.5px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  white-space: pre-wrap;
}
.dh-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 8px;
}
.ti-essence {
  color: var(--ac-warning);
  background: var(--ac-warning-bg);
  border-color: transparent;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 360px;
  overflow-y: auto;
}
.post-item {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-bg);
}
.pi-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pi-floor {
  font-size: 12px;
  font-weight: 700;
  color: var(--ac-primary);
  background: var(--ac-primary-bg);
  padding: 1px 8px;
  border-radius: 999px;
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
.pi-mention {
  display: inline-flex;
  align-items: center;
  gap: 2px;
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
  margin-top: 14px;
  border-top: 1px solid var(--ac-border);
  padding-top: 14px;
}
.reply-mention {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.rm-label {
  font-size: 12px;
  color: var(--ac-text-muted);
  white-space: nowrap;
}
.reply-input {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.reply-input .el-input {
  flex: 1;
}
</style>
