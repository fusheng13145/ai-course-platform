<template>
  <div class="learning-status-tab">
    <!-- 统计卡 -->
    <ClsStatsRow :overview="overview" />

    <!-- 学习趋势图 -->
    <ClsChartsPanel
      :activity-trend-option="activityTrendOption"
      :engagement-dist-option="engagementDistOption"
    />

    <!-- 学生学习详情表 -->
    <ClsStudentTable
      v-model:search-keyword="searchKeyword"
      :loading="loading"
      :filtered-students="filteredStudents"
    />
  </div>
</template>

<script setup>
import ClsStatsRow from './course/ClsStatsRow.vue'
import ClsChartsPanel from './course/ClsChartsPanel.vue'
import ClsStudentTable from './course/ClsStudentTable.vue'
import { useCourseLearningStatusTab } from '@/composables/useCourseLearningStatusTab'

const props = defineProps({
  courseId: { type: [Number, String], required: true },
})

const {
  loading,
  searchKeyword,
  overview,
  activityTrendOption,
  engagementDistOption,
  filteredStudents,
} = useCourseLearningStatusTab(() => props.courseId)
</script>

<style scoped>
.learning-status-tab {
  padding: 4px 0;
}
</style>
