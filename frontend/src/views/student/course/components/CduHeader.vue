<template>
  <div class="course-header-card">
    <el-page-header :title="'返回课程列表'" @back="$emit('back')">
      <template #content>
        <div class="header-content">
          <h1 class="course-title">{{ course.name }}</h1>
          <div class="course-meta-row">
            <el-tag size="small" :type="enrollment.isActive ? 'success' : 'info'">
              {{ enrollment.isActive ? '学习中' : '未开始' }}
            </el-tag>
            <span class="meta-text">{{ course.category || '未分类' }}</span>
            <span class="meta-text">{{ course.teacherName || '未知教师' }}</span>
            <span class="meta-text">{{ course.credits || 0 }} 学分</span>
          </div>
        </div>
      </template>
    </el-page-header>

    <!-- 进度概览 -->
    <div class="progress-overview">
      <span class="progress-label">学习进度</span>
      <el-progress
        :percentage="enrollment.progress"
        :stroke-width="10"
        :color="getProgressColor(enrollment.progress)"
        style="flex: 1; max-width: 300px"
      />
      <span class="progress-value">{{ enrollment.progress }}%</span>
    </div>
  </div>
</template>

<script setup>
/**
 * 学生课程头部卡
 * <p>返回/标题/元信息（分类/教师/学分）+ 学习进度条。</p>
 */
defineProps({
  /** 课程信息 */
  course: { type: Object, required: true },
  /** 选课状态（progress/isActive） */
  enrollment: { type: Object, required: true },
  /** 进度 → 颜色映射函数 */
  getProgressColor: { type: Function, required: true },
})

defineEmits(['back'])
</script>

<style scoped>
.course-header-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  margin-bottom: var(--ac-spacing-md);
  box-shadow: var(--ac-shadow-sm);
}

.header-content {
  padding-left: var(--ac-spacing-md);
}

.course-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0 0 var(--ac-spacing-xs);
}

.course-meta-row {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}

.meta-text {
  font-size: 13px;
  color: var(--ac-text-muted);
}

.progress-overview {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-md);
  margin-top: var(--ac-spacing-md);
  padding-top: var(--ac-spacing-md);
  border-top: 1px solid var(--ac-border);
}

.progress-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--ac-text-secondary);
  white-space: nowrap;
}

.progress-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-primary);
  min-width: 48px;
}
</style>
