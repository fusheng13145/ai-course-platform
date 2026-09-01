<template>
  <div class="tab-card">
    <div class="info-grid">
      <div class="info-item"><span class="info-label">课程编号</span><span class="info-val">{{ course.code || '–' }}</span></div>
      <div class="info-item"><span class="info-label">分类</span><span class="info-val">{{ course.category || '未分类' }}</span></div>
      <div class="info-item"><span class="info-label">难度</span><span class="info-val"><el-tag size="small" effect="light" round>{{ getDifficultyName(course.difficulty) }}</el-tag></span></div>
      <div class="info-item"><span class="info-label">学分</span><span class="info-val">{{ course.credits ?? '–' }}</span></div>
      <div class="info-item"><span class="info-label">学期</span><span class="info-val">{{ course.semester || '–' }} / {{ course.year || '' }}</span></div>
      <div class="info-item"><span class="info-label">报名方式</span><span class="info-val">{{ getEnrollmentTypeName(course.enrollmentType) }}</span></div>
      <div class="info-item"><span class="info-label">最大人数</span><span class="info-val">{{ course.maxStudents ? `${course.maxStudents} 人` : '不限' }}</span></div>
      <div class="info-item"><span class="info-label">开课时间</span><span class="info-val">{{ formatDate(course.startDate) }}</span></div>
    </div>
    <div v-if="course.description" class="info-desc-section">
      <h4 class="section-subtitle">课程简介</h4>
      <p class="desc-text">{{ course.description }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 课程首页 Tab（基本信息 + 简介）
 * <p>展示课程核心信息网格（编号/分类/难度/学分/学期/报名方式/人数/开课时间）与课程简介。</p>
 */
defineProps({
  course: { type: Object, required: true },
  getDifficultyName: { type: Function, required: true },
  getEnrollmentTypeName: { type: Function, required: true },
  formatDate: { type: Function, required: true },
})
</script>

<style scoped>
.tab-card {
  padding: 8px 4px;
}
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 28px;
  margin-bottom: 22px;
}
.info-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--line);
}
.info-label {
  font-size: 13px;
  color: var(--ink-2);
  white-space: nowrap;
  min-width: 72px;
}
.info-val {
  font-size: 14px;
  color: var(--ac-text-primary);
  font-weight: 500;
}
.info-desc-section {
  margin-top: 4px;
}
.section-subtitle {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0 0 8px;
}
.desc-text {
  font-size: 13.5px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
@media (max-width: 1024px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
