<template>
  <div class="fade-in">
    <PageHeader
      title="我的错题"
      subtitle="集中收录答错题目，针对性复习巩固"
      icon="warn"
      icon-bg="warn"
    >
      <el-button
        type="primary"
        size="small"
        :disabled="filteredQuestions.length === 0"
        @click="generatePracticePaper"
      >
        <el-icon><EditPen /></el-icon> 生成练习卷
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="错题总数"
        :value="stats.total"
        icon="warn"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="待复习"
        :value="stats.total - stats.mastered"
        icon="clock"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="已掌握"
        :value="stats.mastered"
        icon="check"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background: var(--warn-soft); color: var(--warn)">
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
            <path
              d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0zM12 9v4M12 17h.01"
            />
          </svg>
        </span>
        <div>
          <div class="l1-title">错题列表</div>
          <div class="l1-sub">按知识点筛选，点击卡片展开查看解析</div>
        </div>
      </div>
      <div class="l1-body">
        <WqToolbar
          v-model:selected-kp-id="selectedKpId"
          :knowledge-point-tags="knowledgePointTags"
        />
        <WqQuestionList
          :loading="loading"
          :questions="filteredQuestions"
          :expanded-id="expandedId"
          @toggle="toggleExpand"
          @mark="markMastered"
          @unmark="unmarkMastered"
          @practice="handlePractice"
          @related="handleRelated"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { EditPen } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import WqToolbar from './components/WqToolbar.vue'
import WqQuestionList from './components/WqQuestionList.vue'
import { useWrongQuestions } from '@/composables/useWrongQuestions'

const {
  loading,
  stats,
  expandedId,
  selectedKpId,
  filteredQuestions,
  knowledgePointTags,
  toggleExpand,
  markMastered,
  unmarkMastered,
  handlePractice,
  handleRelated,
  generatePracticePaper,
} = useWrongQuestions()
</script>
