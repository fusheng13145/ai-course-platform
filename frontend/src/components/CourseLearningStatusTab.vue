<template>
  <div class="learning-status-tab">
    <!-- 加载失败持久提示（不自动消失） -->
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      class="load-error-alert"
    />

    <!-- 统计卡 -->
    <ClsStatsRow :overview="overview" :load-failed="!!classError" />

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
      :empty-text="studentsError ? '学生名单未能加载，请查看上方提示后重试' : '暂无学生数据'"
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
  loadError,
  classError,
  studentsError,
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
.load-error-alert {
  margin-bottom: 16px;
}
</style>
