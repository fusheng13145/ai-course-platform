<template>
  <div class="discussion-page fade-in">
    <!-- 顶部：标题 + 课程选择 + 发起 -->
    <PageHeader
      title="课程讨论"
      subtitle="参与话题讨论、向老师和同学提问，精华内容由老师标记"
      icon="message"
      icon-bg="accent"
    >
      <el-select
        v-model="courseId"
        placeholder="选择课程"
        filterable
        clearable
        style="width: 240px"
        :loading="courseLoading"
        @change="onCourseChange"
      >
        <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>发起讨论
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="话题总数"
        :value="topics.length"
        icon="message"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="精华话题"
        :value="essenceCount"
        icon="star"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="总回复数"
        :value="totalReplies"
        icon="comment"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background: var(--accent-soft); color: var(--accent)">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
        </span>
        <div>
          <div class="l1-title">话题列表</div>
          <div class="l1-sub">按精华筛选或搜索，点击进入话题详情参与讨论</div>
        </div>
      </div>
      <div class="l1-body">
        <div v-loading="loading">
          <DscTopicList
            v-model:essence-filter="essenceFilter"
            v-model:keyword="keyword"
            :course-id="courseId"
            :topic-loading="topicLoading"
            :topics="topics"
            @load="loadTopics"
            @refresh="loadTopics"
            @open-detail="openDetail"
          />
        </div>
      </div>
    </section>

    <!-- 发起话题 -->
    <DscCreateDialog
      v-model="createDialog"
      :topic-form="topicForm"
      :create-submitting="createSubmitting"
      @submit="submitTopic"
    />

    <!-- 话题详情 -->
    <DscDetailDrawer
      v-model="detailVisible"
      v-model:mention-ids="mentionIds"
      v-model:reply-text="replyText"
      :current-topic="currentTopic"
      :posts="posts"
      :post-loading="postLoading"
      :participant-options="participantOptions"
      :reply-submitting="replySubmitting"
      :my-user-id="myUserId"
      :can-delete="canDelete"
      @like="likePost"
      @remove="removePost"
      @submit-reply="submitReply"
    />
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useDiscussion } from '@/composables/useDiscussion'
import DscTopicList from './components/DscTopicList.vue'
import DscCreateDialog from './components/DscCreateDialog.vue'
import DscDetailDrawer from './components/DscDetailDrawer.vue'

/**
 * 课程讨论页（页面壳）
 * <p>课程选择 + 统计卡 + 话题列表编排 + 发起对话框/详情抽屉装配，业务逻辑委托 useDiscussion。</p>
 */
const {
  myUserId,
  courseLoading,
  courseOptions,
  courseId,
  loading,
  topicLoading,
  topics,
  keyword,
  essenceFilter,
  essenceCount,
  totalReplies,
  createDialog,
  createSubmitting,
  topicForm,
  detailVisible,
  currentTopic,
  postLoading,
  posts,
  replyText,
  replySubmitting,
  mentionIds,
  participantOptions,
  onCourseChange,
  loadTopics,
  openCreateDialog,
  submitTopic,
  openDetail,
  canDelete,
  submitReply,
  likePost,
  removePost,
} = useDiscussion()
</script>

<style scoped>
.discussion-page {
  max-width: 1400px;
  margin: 0 auto;
}
</style>
