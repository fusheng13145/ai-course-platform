<template>
  <div class="interaction-page fade-in">
    <!-- 顶部课程选择器 -->
    <div class="page-head">
      <div class="page-title">
        <el-icon :size="20" class="ttl-icon"><ChatLineSquare /></el-icon>
        <div>
          <h3>课堂互动</h3>
          <p>发起签到与投票，实时掌握课堂参与情况</p>
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
        <template #default>选择一门课程后，即可发起签到与投票互动。</template>
      </el-alert>

      <template v-else>
        <el-tabs v-model="activeTab" class="inter-tabs">
          <!-- 签到 -->
          <el-tab-pane label="签到" name="sign">
            <InSignPanel
              :sign-list="signList"
              :sign-loading="signLoading"
              @open-sign="openSignInDialog"
              @refresh="loadSignInList"
              @open-records="openRecords"
              @publish="handlePublish"
              @close="handleCloseSignIn"
            />
          </el-tab-pane>

          <!-- 投票 -->
          <el-tab-pane label="投票" name="vote">
            <InVotePanel
              :vote-list="voteList"
              :vote-loading="voteLoading"
              @open-vote="openVoteDialog"
              @refresh="loadVoteList"
              @open-result="openResult"
              @close="handleCloseVote"
            />
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <!-- 发起签到 -->
    <InSignDialog
      v-model="signDialog"
      :submitting="signSubmitting"
      :form="signForm"
      @submit="submitSignIn"
    />

    <!-- 签到记录 -->
    <InRecordsDialog
      v-model="recordsDialog"
      :record-list="recordList"
      :record-loading="recordLoading"
      :current-sign="currentSign"
      :mark-visible="markVisible"
      :mark-form="markForm"
      :mark-submitting="markSubmitting"
      @open-mark="openMarkForm"
      @mark-submit="submitMark"
    />

    <!-- 发起投票 -->
    <InVoteDialog
      v-model="voteDialog"
      :submitting="voteSubmitting"
      :form="voteForm"
      @submit="submitVote"
    />

    <!-- 投票结果 -->
    <InResultDialog v-model="resultDialog" :result="voteResult" />
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, onMounted, toRef } from 'vue'
import { ChatLineSquare } from '@element-plus/icons-vue'
import courseApi from '@/api/course'
import InSignPanel from './components/InSignPanel.vue'
import InVotePanel from './components/InVotePanel.vue'
import InSignDialog from './components/InSignDialog.vue'
import InRecordsDialog from './components/InRecordsDialog.vue'
import InVoteDialog from './components/InVoteDialog.vue'
import InResultDialog from './components/InResultDialog.vue'
import { useInteraction } from '@/composables/useInteraction'

const courseLoading = ref(false)
const courseOptions = ref([])
const courseId = ref('')

const courseIdRef = toRef(courseId)

const {
  loading,
  activeTab,
  signLoading,
  signList,
  signDialog,
  signSubmitting,
  signForm,
  recordsDialog,
  recordLoading,
  recordList,
  currentSign,
  markVisible,
  markSubmitting,
  markForm,
  voteLoading,
  voteList,
  voteDialog,
  voteSubmitting,
  voteForm,
  resultDialog,
  voteResult,
  loadSignInList,
  openSignInDialog,
  submitSignIn,
  handlePublish,
  handleCloseSignIn,
  openRecords,
  openMarkForm,
  submitMark,
  loadVoteList,
  openVoteDialog,
  submitVote,
  handleCloseVote,
  openResult,
} = useInteraction(courseIdRef)

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

.inter-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--ac-spacing-md);
}

@media (max-width: 1100px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
