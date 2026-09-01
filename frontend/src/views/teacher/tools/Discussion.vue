<template>
  <div class="discussion-page fade-in">
    <!-- 顶部课程选择器 -->
    <div class="page-head">
      <div class="page-title">
        <el-icon :size="20" class="ttl-icon"><ChatDotRound /></el-icon>
        <div>
          <h3>课程讨论</h3>
          <p>发布话题、引导学生讨论并参与互动</p>
        </div>
      </div>
      <el-select
        v-model="courseId"
        placeholder="请选择课程"
        filterable
        clearable
        style="width: 240px"
        :loading="courseLoading"
      >
        <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
    </div>

    <div v-loading="loading">
      <el-alert v-if="!courseId" title="请选择课程" type="info" :closable="false" show-icon>
        <template #default>选择一门课程后，即可查看并管理课程讨论话题。</template>
      </el-alert>

      <template v-else>
        <!-- 工具栏 -->
        <div class="toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索话题"
            clearable
            style="width: 240px"
            :prefix-icon="Search"
            @keyup.enter="loadTopics"
            @clear="loadTopics"
          />
          <el-button :disabled="topicLoading" @click="loadTopics">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>发布话题
          </el-button>
        </div>

        <DscTopicList :topics="topics" :topic-loading="topicLoading" @open-detail="openDetail" />
      </template>
    </div>

    <!-- 发布话题 -->
    <DscCreateDialog
      v-model="createDialog"
      :submitting="createSubmitting"
      :form="topicForm"
      @submit="submitTopic"
    />

    <!-- 话题详情 -->
    <DscDetailDialog
      v-model="detailDialog"
      v-model:reply-text="replyText"
      :current-topic="currentTopic"
      :posts="posts"
      :post-loading="postLoading"
      :reply-submitting="replySubmitting"
      @toggle-pin="togglePin"
      @close-topic="closeTopic"
      @like="likePost"
      @remove-post="removePost"
      @submit-reply="submitReply"
    />
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, onMounted, toRef } from 'vue'
import { ChatDotRound, Plus, Refresh, Search } from '@element-plus/icons-vue'
import courseApi from '@/api/course'
import DscTopicList from './components/DscTopicList.vue'
import DscCreateDialog from './components/DscCreateDialog.vue'
import DscDetailDialog from './components/DscDetailDialog.vue'
import { useTeacherDiscussion } from '@/composables/useTeacherDiscussion'

const courseLoading = ref(false)
const courseOptions = ref([])
const courseId = ref('')

const courseIdRef = toRef(courseId)

const {
  loading,
  topicLoading,
  topics,
  keyword,
  createDialog,
  createSubmitting,
  topicForm,
  detailDialog,
  currentTopic,
  postLoading,
  posts,
  replyText,
  replySubmitting,
  loadTopics,
  openCreateDialog,
  submitTopic,
  openDetail,
  submitReply,
  likePost,
  removePost,
  togglePin,
  closeTopic,
} = useTeacherDiscussion(courseIdRef)

/* ---------- 课程选择器 ---------- */
async function loadCourses() {
  courseLoading.value = true
  try {
    const r = await courseApi.getMyCourses()
    const body = unwrap(r)
    const list = body?.records || body?.list || body || []
    courseOptions.value = Array.isArray(list) ? list : []
    if (courseOptions.value.length && !courseId.value) courseId.value = courseOptions.value[0].id
  } catch (e) {
    logger.error('加载课程列表失败', e)
  } finally {
    courseLoading.value = false
  }
}

onMounted(loadCourses)
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: var(--ac-spacing-lg);
  flex-wrap: wrap;
}
.page-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ttl-icon {
  color: var(--ac-primary);
}
.page-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 640;
  color: var(--ac-text-primary);
}
.page-title p {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--ac-text-muted);
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: var(--ac-spacing-md);
  flex-wrap: wrap;
}

@media (max-width: 1100px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
