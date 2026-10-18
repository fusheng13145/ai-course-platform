<template>
  <div class="course-discussion fade-in">
    <PageHeader compact title="课程讨论" subtitle="话题浏览、发起与回复互动" icon="message">
      <el-button type="primary" :icon="Plus" @click="topicVisible = true">发起话题</el-button>
    </PageHeader>

    <!-- 加载失败提示：见 useCourseDiscussion.loadError 注释（失败不得渲染成「暂无讨论话题」） -->
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: var(--ac-spacing-md)"
    />

    <!-- 筛选 + 话题列表 -->
    <CdsTopicList
      v-model:keyword="filter.keyword"
      v-model:chapter-id="filter.chapterId"
      :loading="loading"
      :topics="topics"
      :empty-text="loadError ? '话题列表未能加载，请查看上方提示后重试' : '暂无讨论话题'"
      @search="loadTopics"
      @open-detail="openDetail"
    />

    <!-- 发起话题 -->
    <CdsTopicDialog
      v-if="topicVisible"
      :saving="saving"
      @close="topicVisible = false"
      @submit="createTopic"
    />

    <!-- 话题详情抽屉 -->
    <CdsDetailDrawer
      v-model="detailVisible"
      v-model:reply-text="replyText"
      :detail-loading="detailLoading"
      :detail="detail"
      :posts="posts"
      :reply-loading="replyLoading"
      @closed="onDetailClosed"
      @toggle-pin="togglePin"
      @toggle-close="toggleClose"
      @like="likePost"
      @del-post="delPost"
      @send-reply="sendReply"
    />
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import CdsTopicList from './components/CdsTopicList.vue'
import CdsTopicDialog from './components/CdsTopicDialog.vue'
import CdsDetailDrawer from './components/CdsDetailDrawer.vue'
import { useCourseDiscussion } from '@/composables/useCourseDiscussion'

const {
  loading,
  loadError,
  saving,
  replyLoading,
  topics,
  filter,
  topicVisible,
  detailVisible,
  detailLoading,
  detail,
  posts,
  replyText,
  loadTopics,
  createTopic,
  openDetail,
  onDetailClosed,
  sendReply,
  likePost,
  delPost,
  togglePin,
  toggleClose,
} = useCourseDiscussion()
</script>
