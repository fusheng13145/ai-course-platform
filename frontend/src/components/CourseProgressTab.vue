<template>
  <div class="progress-tab">
    <!-- 总览统计卡 -->
    <CptStatsRow
      :overall-progress="overallProgress"
      :completed-chapters="completedChapters"
      :total-chapters="totalChapters"
      :total-tasks="totalTasks"
      :avg-submission-rate="avgSubmissionRate"
    />

    <!-- 总体进度环形图 -->
    <CptRingChart
      :overall-progress="overallProgress"
      :completed-chapters="completedChapters"
      :total-chapters="totalChapters"
      :circumference="circumference"
      :dash-offset="dashOffset"
    />

    <!-- 计划进度 vs 实际进度 -->
    <CptCompareCards
      :planned-chapters="plannedChapters"
      :total-chapters="totalChapters"
      :planned-remaining="plannedRemaining"
      :overall-progress="overallProgress"
      :completed-chapters="completedChapters"
      :behind-count="behindCount"
      :planned-progress="plannedProgress"
    />

    <!-- 章节进度明细 -->
    <div class="chapters-section">
      <div class="section-header">
        <h3 class="section-title">章节进度明细</h3>
      </div>
      <CptChapterTable :loading="loading" :chapter-list="chapterList" />
    </div>
  </div>
</template>

<script setup>
import { toRef } from 'vue'
import CptChapterTable from './course/CptChapterTable.vue'
import CptStatsRow from './course/CptStatsRow.vue'
import CptRingChart from './course/CptRingChart.vue'
import CptCompareCards from './course/CptCompareCards.vue'
import { useCourseProgressTab } from '@/composables/useCourseProgressTab'

const props = defineProps({
  courseId: { type: [Number, String], required: true },
})
const courseIdRef = toRef(props, 'courseId')

const {
  loading,
  chapterList,
  plannedChapters,
  circumference,
  totalChapters,
  completedChapters,
  totalTasks,
  overallProgress,
  dashOffset,
  avgSubmissionRate,
  plannedProgress,
  plannedRemaining,
  behindCount,
} = useCourseProgressTab(courseIdRef)
</script>

<style scoped>
.progress-tab {
  padding: 4px 0;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}
.chapters-section {
  margin-bottom: 10px;
}
</style>
