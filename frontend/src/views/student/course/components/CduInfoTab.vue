<template>
  <div class="tab-content-card">
    <section class="info-section">
      <h3 class="section-title">课程描述</h3>
      <p class="description-text">{{ course.description || '暂无课程简介' }}</p>
    </section>

    <section class="info-section">
      <h3 class="section-title">课程详情</h3>
      <el-descriptions :column="2" border class="detail-desc">
        <el-descriptions-item label="课程分类">{{ course.category || '未分类' }}</el-descriptions-item>
        <el-descriptions-item label="授课教师">{{ course.teacherName || '未知' }}</el-descriptions-item>
        <el-descriptions-item label="学分">{{ course.credits || 0 }}</el-descriptions-item>
        <el-descriptions-item label="难度等级">
          <el-tag size="small" :type="getDifficultyType(course.difficulty)">{{
            course.difficulty || '未设置'
          }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总章节数">{{ chapters.length }} 章</el-descriptions-item>
        <el-descriptions-item label="预计时长">{{ estimatedDuration }} 分钟</el-descriptions-item>
      </el-descriptions>
    </section>

    <div v-if="!enrollment.id" class="action-section">
      <el-button type="primary" size="large" :loading="enrolling" @click="$emit('enroll')">
        <el-icon><Plus /></el-icon>
        加入课程学习
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'

/**
 * 课程简介 Tab
 * <p>课程描述 + 课程详情描述列表 + 未选课时加入课程入口。</p>
 */
defineProps({
  /** 课程信息 */
  course: { type: Object, required: true },
  /** 章节列表（总章节数） */
  chapters: { type: Array, required: true },
  /** 选课状态（id 为空时显示加入入口） */
  enrollment: { type: Object, required: true },
  /** 预计总时长 */
  estimatedDuration: { type: Number, default: 0 },
  /** 加入加载态 */
  enrolling: { type: Boolean, default: false },
  /** 难度 → 标签色映射函数 */
  getDifficultyType: { type: Function, required: true },
})

defineEmits(['enroll'])
</script>

<style scoped>
.tab-content-card {
  padding: var(--ac-spacing-md) 0;
}

.info-section {
  margin-bottom: var(--ac-spacing-xl);
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
  padding-left: 12px;
  border-left: 3px solid var(--ac-primary);
}

.description-text {
  line-height: 1.9;
  color: var(--ac-text-secondary);
  font-size: 14px;
}

.detail-desc {
  margin-top: var(--ac-spacing-sm);
}

.action-section {
  margin-top: var(--ac-spacing-xl);
  text-align: center;
}
</style>
