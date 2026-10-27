<template>
  <div class="course-interaction fade-in">
    <PageHeader
      title="课堂互动"
      subtitle="发起签到与投票，实时掌握课堂参与情况"
      icon="megaphone"
      icon-bg="accent"
    />

    <div class="stat-grid">
      <StatCard
        label="签到活动"
        :value="signIns.length"
        icon="check"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="投票活动"
        :value="votes.length"
        icon="megaphone"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="进行中"
        :value="activeCount"
        icon="play"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="已结束"
        :value="endedCount"
        icon="clock"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
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
            <path d="m3 11 18-5v12L3 13v-2zM11.6 16.8a3 3 0 1 1-5.8-1.6" />
          </svg>
        </span>
        <div>
          <div class="l1-title">互动管理</div>
          <div class="l1-sub">签到与投票活动一览，支持发布、结束与结果查看</div>
        </div>
      </div>
      <div class="l1-body">
        <!-- 加载失败提示：见 useCourseInteraction.loadError 注释（失败不得渲染成「暂无签到/暂无投票」） -->
        <el-alert
          v-if="loadError"
          :title="loadError"
          type="error"
          :closable="false"
          show-icon
          style="margin-bottom: var(--ac-spacing-md)"
        />
        <!-- P4-MIG-07：el-tabs→TabGroup；pane 内容 v-show（等价 el-tabs 全挂载隐藏语义） -->
        <TabGroup
          :tabs="IT_TABS"
          :active="activeTab"
          variant="line"
          class="it-tabs"
          @change="activeTab = $event"
        />
        <!-- 签到 -->
        <div v-show="activeTab === 'signin'">
          <CiSignTable
            :data="signIns"
            :empty-text="signError ? '签到记录未能加载，请查看上方提示后重试' : '暂无签到'"
            @open-sign="openSignInDialog"
            @publish="publish"
            @close="closeSignIn"
            @open-records="openRecords"
          />
        </div>

        <!-- 投票 -->
        <div v-show="activeTab === 'vote'">
          <CiVoteTable
            :data="votes"
            :empty-text="voteError ? '投票记录未能加载，请查看上方提示后重试' : '暂无投票'"
            @open-vote="openVoteDialog"
            @close="closeVote"
            @open-result="openVoteResult"
          />
        </div>
      </div>
    </section>

    <!-- 发起签到 -->
    <CiSignDialog
      v-model="signInVisible"
      :saving="saving"
      :form="signInForm"
      @create="createSignIn"
    />

    <!-- 签到记录 -->
    <CiRecordsDialog v-model="recordsVisible" :records="records" @mark="mark" />

    <!-- 发起投票 -->
    <InVoteDialog
      v-model="voteVisible"
      :submitting="saving"
      :form="voteForm"
      @submit="createVote"
    />

    <!-- 投票结果 -->
    <InResultDialog v-model="resultVisible" :result="voteResult" />
  </div>
</template>

<script setup lang="ts">
import { toRef } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import CiSignTable from './components/CiSignTable.vue'
import CiVoteTable from './components/CiVoteTable.vue'
import CiSignDialog from './components/CiSignDialog.vue'
import CiRecordsDialog from './components/CiRecordsDialog.vue'
import InVoteDialog from '@/components/interaction/InVoteDialog.vue'
import InResultDialog from '@/components/interaction/InResultDialog.vue'
import { useCourseInteraction } from '@/composables/useCourseInteraction'
import TabGroup from '@/components/ui/TabGroup.vue'
const IT_TABS = [
  { key: 'signin', label: '签到' },
  { key: 'vote', label: '投票' },
]

const route = useRoute()
const courseIdRef = toRef(route.params, 'id')

const {
  loadError,
  signError,
  voteError,
  saving,
  activeTab,
  signIns,
  votes,
  activeCount,
  endedCount,
  signInVisible,
  signInForm,
  recordsVisible,
  records,
  voteVisible,
  voteForm,
  resultVisible,
  voteResult,
  openSignInDialog,
  createSignIn,
  publish,
  closeSignIn,
  openRecords,
  mark,
  openVoteDialog,
  createVote,
  closeVote,
  openVoteResult,
} = useCourseInteraction(courseIdRef)
</script>

<style scoped>
.course-interaction {
  padding: 4px;
}
.it-tabs {
  background: transparent;
  border: none;
  padding: 0;
  box-shadow: none;
}
</style>
