<template>
  <div class="fade-in exam-governance">
    <PageHeader
      title="考试防作弊治理"
      subtitle="监考会话风险看板、违规事件核查与复核工作流"
      icon="shield"
      icon-bg="accent"
    >
      <el-button :loading="loading" @click="loadSessions">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="进行中监考会话"
        :value="sessions.length"
        icon="video"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="高风险会话"
        :value="highRiskCount"
        icon="warn"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="已触发标记(FLAGGED)"
        :value="flaggedCount"
        icon="bell"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="累计违规事件"
        :value="totalEvents"
        icon="megaphone"
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
            <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" />
          </svg>
        </span>
        <div>
          <div class="l1-title">监考治理看板</div>
          <div class="l1-sub">风险会话、违规事件与阈值规则</div>
        </div>
      </div>
      <div class="l1-body">
        <div class="grid-main">
          <!-- 监考会话列表 -->
          <EgSessionList
            :sessions="sessions"
            :selected-id="selectedId"
            :loading="loading"
            :format-time="formatTime"
            :risk-tag="riskTag"
            :level-text="levelText"
            @select="selectSession"
          />

          <!-- 风险详情 -->
          <EgRiskDetail
            :current="current"
            :risk-tag="riskTag"
            :level-text="levelText"
            :type-text="typeText"
            @open-events="openEvents"
          />
        </div>

        <!-- 阈值规则（管理员可调控） -->
        <div style="margin-top: var(--ac-spacing-lg)">
          <EgThresholdCard
            :is-admin="isAdmin"
            :thresholds="thresholds"
            :thr-saving="thrSaving"
            @save="saveThresholds"
          />
        </div>
      </div>
    </section>

    <!-- 事件核查抽屉 -->
    <EgEventsDrawer
      v-model="drawer"
      :events="events"
      :events-loading="eventsLoading"
      :reviewing-id="reviewingId"
      :review-action="reviewAction"
      :can-review="canReview"
      :risk-tag="riskTag"
      :type-text="typeText"
      :status-text="statusText"
      :status-tag="statusTag"
      :format-time="formatTime"
      @review="submitReview"
    />
  </div>
</template>

<script setup>
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useExamGovernance } from '@/composables/useExamGovernance'
import EgSessionList from './components/EgSessionList.vue'
import EgRiskDetail from './components/EgRiskDetail.vue'
import EgThresholdCard from './components/EgThresholdCard.vue'
import EgEventsDrawer from './components/EgEventsDrawer.vue'

/**
 * 考试防作弊治理页（页面壳）
 * <p>统计卡 + 看板双栏（会话列表/风险详情）+ 阈值规则 + 事件核查抽屉装配，业务逻辑委托 useExamGovernance。</p>
 */
const {
  loading,
  sessions,
  selectedId,
  drawer,
  events,
  eventsLoading,
  isAdmin,
  thresholds,
  thrSaving,
  current,
  highRiskCount,
  flaggedCount,
  totalEvents,
  statusText,
  statusTag,
  canReview,
  typeText,
  levelText,
  riskTag,
  formatTime,
  loadSessions,
  selectSession,
  openEvents,
  saveThresholds,
  submitReview,
  reviewingId,
  reviewAction,
} = useExamGovernance()
</script>

<style scoped>
.exam-governance {
  max-width: 1400px;
  margin: 0 auto;
}

.grid-main {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: var(--ac-spacing-lg);
}

@media (max-width: 960px) {
  .grid-main {
    grid-template-columns: 1fr;
  }
}
</style>
