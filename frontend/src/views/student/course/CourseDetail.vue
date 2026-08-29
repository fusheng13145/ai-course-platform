<template>
  <div v-loading="pageLoading" class="fade-in">
    <!-- 页面头部：课程信息 -->
    <CduHeader
      :course="course"
      :enrollment="enrollment"
      :get-progress-color="getProgressColor"
      @back="$router.push('/student/courses')"
    />

    <!-- Tabs 主内容区 -->
    <el-tabs v-model="activeTab" class="main-tabs">
      <!-- 课程简介 Tab -->
      <el-tab-pane label="课程简介" name="info">
        <CduInfoTab
          :course="course"
          :chapters="chapters"
          :enrollment="enrollment"
          :estimated-duration="estimatedDuration"
          :enrolling="enrolling"
          :get-difficulty-type="getDifficultyType"
          @enroll="handleEnroll"
        />
      </el-tab-pane>

      <!-- 章节学习 Tab -->
      <el-tab-pane label="章节学习" name="chapters">
        <div class="tab-content-card">
          <!-- 章节列表视图 -->
          <CduChapterList v-if="!learningMode" :chapters="chapters" @enter="enterLearning" />

          <!-- 学习模式视图 -->
          <CduLearningMode
            v-else
            :current-chapter="currentChapter"
            :current-chapter-video="currentChapterVideo"
            :learning-time-display="learningTimeDisplay"
            :completing="completing"
            :video-src="videoSrc"
            @exit="exitLearning"
            @complete="markChapterComplete"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { useStudentCourseDetail } from '@/composables/useStudentCourseDetail'
import CduHeader from './components/CduHeader.vue'
import CduInfoTab from './components/CduInfoTab.vue'
import CduChapterList from './components/CduChapterList.vue'
import CduLearningMode from './components/CduLearningMode.vue'

/**
 * 学生课程详情页（页面壳）
 * <p>头部卡 + Tabs（简介/章节学习）编排，学习模式双视图切换，业务逻辑委托 useStudentCourseDetail。</p>
 */

const {
  pageLoading,
  activeTab,
  enrolling,
  completing,
  course,
  enrollment,
  chapters,
  learningMode,
  currentChapter,
  currentChapterVideo,
  learningTimeDisplay,
  estimatedDuration,
  videoSrc,
  getProgressColor,
  getDifficultyType,
  handleEnroll,
  enterLearning,
  exitLearning,
  markChapterComplete,
} = useStudentCourseDetail()
</script>

<style scoped>
.main-tabs {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: 0 var(--ac-spacing-lg);
}

.tab-content-card {
  padding: var(--ac-spacing-md) 0;
}
</style>
