<template>
  <div class="course-detail">
    <!-- 页头：返回 + 课程名 + 状态 + 操作 -->
    <div class="detail-header">
      <div class="header-left">
        <el-button text @click="$router.push('/teacher/courses')">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <div class="header-title-group">
          <h1 class="header-title">{{ course.name }}</h1>
          <el-tag
            size="small"
            :type="
              course.approvalStatus === 'APPROVED'
                ? 'success'
                : course.approvalStatus === 'REJECTED'
                  ? 'danger'
                  : 'warning'
            "
            effect="light"
            round
          >
            {{ getStatusText(course.approvalStatus) }}
          </el-tag>
        </div>
      </div>
      <div class="header-actions">
        <el-button plain @click="showEditDialog"
          ><el-icon><Edit /></el-icon>编辑课程</el-button
        >
        <el-button type="primary" plain @click="showNotificationDialog"
          ><el-icon><Bell /></el-icon>发送通知</el-button
        >
      </div>
    </div>

    <!-- 统计卡（容错，不暴露后端错误） -->
    <div class="stat-grid animate-in">
      <StatCard
        label="学生人数"
        :value="stats.totalStudents ?? '-'"
        icon="users"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="资料"
        :value="resourceCount ?? '-'"
        icon="folder"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="作业"
        :value="taskCount ?? '-'"
        icon="edit"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="考试"
        :value="examCount ?? '-'"
        icon="doc"
        tone="var(--violet-soft)"
        tone-color="var(--violet)"
      />
    </div>

    <!-- 分路加载失败持久提示（任一子模块失败即显示，不自动消失） -->
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      class="load-error-alert"
    />

    <!-- 主 Tab 区 -->
    <el-tabs v-model="activeTab" class="main-tabs">
      <el-tab-pane label="课程首页" name="info">
        <CdInfoTab
          :course="course"
          :get-difficulty-name="getDifficultyName"
          :get-enrollment-type-name="getEnrollmentTypeName"
          :format-date="formatDate"
        />
      </el-tab-pane>
      <el-tab-pane label="授课进度" name="progress"
        ><CourseProgressTab :course-id="course.id"
      /></el-tab-pane>
      <el-tab-pane label="学习情况" name="learningStatus"
        ><CourseLearningStatusTab :course-id="course.id"
      /></el-tab-pane>
      <el-tab-pane label="章节管理" name="chapters">
        <CdChaptersTab
          :chapters="chapters"
          :chapters-loading="chaptersLoading"
          :drag-changed="dragChanged"
          :saving-order="savingOrder"
          :empty-text="chaptersError ? '章节列表未能加载，请查看上方提示后重试' : '暂无章节，点击上方添加'"
          @add="showAddChapterDialog"
          @edit="editChapter"
          @delete="deleteChapter"
          @save-order="saveChapterOrder"
          @reset-order="resetChapterOrder"
        />
      </el-tab-pane>
      <el-tab-pane label="课程资料上传" name="resources">
        <CourseResourcePanel
          :course-id="Number(course.id)"
          @resource-updated="updateResourceCount"
        />
      </el-tab-pane>
      <el-tab-pane label="作业管理" name="tasks">
        <CourseTaskPanel :course-id="Number(course.id)" @task-updated="updateTaskCount" />
      </el-tab-pane>
      <el-tab-pane label="作业完成情况" name="completion"
        ><CourseCompletionTab :course-id="course.id"
      /></el-tab-pane>
      <el-tab-pane label="考试管理" name="exams">
        <CourseExamPanel :course-id="Number(course.id)" @exam-updated="updateExamCount" />
      </el-tab-pane>
      <el-tab-pane label="成绩管理" name="grades"
        ><CourseGradesTab :course-id="course.id"
      /></el-tab-pane>
      <el-tab-pane label="学生管理" name="students">
        <CdStudentsTab
          :students="students"
          :students-loading="studentsLoading"
          :empty-text="studentsError ? '学生列表未能加载，请查看上方提示后重试' : '暂无学生'"
          @approve="approveStudent"
          @reject="rejectStudent"
        />
      </el-tab-pane>
      <el-tab-pane label="知识管理" name="knowledge"
        ><CourseKnowledgeTab :course-id="course.id"
      /></el-tab-pane>
      <el-tab-pane label="通知管理" name="notifications">
        <CdNotificationsTab
          :notifications="notifications"
          :notifications-loading="notificationsLoading"
          :format-date-time="formatDateTime"
          :empty-text="notificationsError ? '通知列表未能加载，请查看上方提示后重试' : '暂无通知'"
          @send="showNotificationDialog"
          @delete="deleteNotification"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 三个对话框 -->
    <CdDialogs
      :edit-visible="editDialogVisible"
      :edit-form="editForm"
      :categories="categories"
      :saving-course="savingCourse"
      :chapter-visible="chapterDialogVisible"
      :editing-chapter="editingChapter"
      :chapter-form="chapterForm"
      :saving-chapter="savingChapter"
      :notice-visible="notificationDialogVisible"
      :notification-form="notificationForm"
      :sending-notification="sendingNotification"
      @update:edit-visible="editDialogVisible = $event"
      @update:chapter-visible="chapterDialogVisible = $event"
      @update:notice-visible="notificationDialogVisible = $event"
      @save-course="saveCourse"
      @save-chapter="saveChapter"
      @send-notice="sendNotification"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { ArrowLeft, Edit, Bell } from '@element-plus/icons-vue'
import { useCourseDetail } from '@/composables/useCourseDetail'
import StatCard from '@/components/ui/StatCard.vue'
import CourseResourcePanel from '@/components/CourseResourcePanel.vue'
import CourseTaskPanel from '@/components/CourseTaskPanel.vue'
import CourseExamPanel from '@/components/CourseExamPanel.vue'
import CourseGradesTab from '@/components/CourseGradesTab.vue'
import CourseKnowledgeTab from '@/components/CourseKnowledgeTab.vue'
import CourseProgressTab from '@/components/CourseProgressTab.vue'
import CourseLearningStatusTab from '@/components/CourseLearningStatusTab.vue'
import CourseCompletionTab from '@/components/CourseCompletionTab.vue'
import CdInfoTab from './components/CdInfoTab.vue'
import CdChaptersTab from './components/CdChaptersTab.vue'
import CdStudentsTab from './components/CdStudentsTab.vue'
import CdNotificationsTab from './components/CdNotificationsTab.vue'
import CdDialogs from './components/CdDialogs.vue'

/**
 * 课程详情页面（教师）
 * <p>页面壳：页头 + 统计卡 + 十二 Tab 编排（组件化子模块 + 本地内联 Tab 下沉为
 * Cd 系列子组件）+ 编辑课程/章节/通知三个对话框；数据与业务逻辑由 useCourseDetail 承载。</p>
 */
const {
  route,
  activeTab,
  course,
  stats,
  resourceCount,
  taskCount,
  examCount,
  chapters,
  students,
  notifications,
  chaptersLoading,
  studentsLoading,
  notificationsLoading,
  chaptersError,
  studentsError,
  notificationsError,
  loadError,
  categories,
  editDialogVisible,
  editForm,
  savingCourse,
  chapterDialogVisible,
  editingChapter,
  chapterForm,
  savingChapter,
  dragChanged,
  savingOrder,
  notificationDialogVisible,
  notificationForm,
  sendingNotification,
  initData,
  updateResourceCount,
  updateTaskCount,
  updateExamCount,
  getStatusText,
  getDifficultyName,
  getEnrollmentTypeName,
  formatDate,
  formatDateTime,
  showEditDialog,
  saveCourse,
  showAddChapterDialog,
  editChapter,
  saveChapter,
  deleteChapter,
  saveChapterOrder,
  resetChapterOrder,
  approveStudent,
  rejectStudent,
  showNotificationDialog,
  sendNotification,
  deleteNotification,
} = useCourseDetail()

onMounted(() => initData())
watch(
  () => route.params.id,
  (newId, oldId) => {
    if (newId && newId !== oldId) initData()
  },
)
</script>

<style scoped>
.course-detail {
  padding: 0;
}
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line);
  position: relative;
}
.detail-header::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -1px;
  width: 64px;
  height: 3px;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--accent), rgba(193, 59, 48, 0.15));
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-title-group {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.header-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--ink);
  margin: 0;
  line-height: 1.3;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.load-error-alert {
  margin-bottom: 16px;
}
.main-tabs {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg, 12px);
  border: 1px solid var(--ac-border);
  padding: 6px 20px 16px;
}
:deep(.main-tabs .el-tabs__header) {
  margin-bottom: 16px;
}
:deep(.main-tabs .el-tabs__item) {
  height: 42px;
  line-height: 42px;
  padding: 0 18px;
  font-size: 14px;
  font-weight: 500;
  color: var(--ac-text-secondary);
  transition: color 0.15s;
}
:deep(.main-tabs .el-tabs__item.is-active) {
  color: var(--ac-primary);
  font-weight: 600;
}
:deep(.main-tabs .el-tabs__active-bar) {
  height: 3px;
  border-radius: 2px;
}
@media (max-width: 1024px) {
  .detail-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }
  .header-actions {
    width: 100%;
  }
  .header-actions .el-button {
    flex: 1;
  }
}
</style>
