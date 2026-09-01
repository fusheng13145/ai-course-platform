<template>
  <ProfileCard :icon="Reading" title="我的课程">
    <template #action>
      <el-button text type="primary" size="small" @click="emit('view-all')">
        查看全部<el-icon><ArrowRight /></el-icon>
      </el-button>
    </template>

    <div v-loading="loading" class="course-list">
      <div v-for="c in courses" :key="c.courseId" class="course-item" @click="emit('open', c)">
        <div class="course-cover" :style="{ background: c._bg }">
          <span :style="{ color: c._fg }">{{ courseInitial(c.courseName) }}</span>
        </div>
        <div class="course-main">
          <div class="course-name">{{ c.courseName }}</div>
          <el-progress
            :percentage="c.progress"
            :stroke-width="6"
            :show-text="false"
            :color="progressColor(c.progress)"
            class="course-progress"
          />
        </div>
        <div class="course-pct">{{ c.progress }}%</div>
      </div>
      <el-empty
        v-if="!loading && courses.length === 0"
        description="还没有在学课程"
        :image-size="72"
      />
    </div>
  </ProfileCard>
</template>

<script setup lang="ts">
import { Reading, ArrowRight } from '@element-plus/icons-vue'
import ProfileCard from './ProfileCard.vue'

/**
 * 个人中心「我的课程」区块
 * <p>展示在学课程（最多 6 门）的封面字、进度条与百分比；空态与加载态由父组件传入。</p>
 */
defineProps({
  /** 课程列表项（courseId/courseName/progress/_bg/_fg） */
  courses: { type: Array, default: () => [] },
  /** 加载态 */
  loading: { type: Boolean, default: false },
  /** 课程名首字符（用于封面） */
  courseInitial: { type: Function, required: true },
  /** 进度条配色函数 */
  progressColor: { type: Function, required: true },
})

const emit = defineEmits(['view-all', 'open'])
</script>

<style scoped>
.course-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.course-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px;
  border-radius: var(--ac-radius-md);
  cursor: pointer;
  transition:
    background var(--ac-transition-fast),
    transform var(--ac-transition-fast);
}
.course-item:hover {
  background: var(--ac-bg-hover);
  transform: translateX(2px);
}
.course-cover {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
}
.course-main {
  flex: 1;
  min-width: 0;
}
.course-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.course-progress {
  max-width: 100%;
}
.course-pct {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ac-text-secondary);
  width: 42px;
  text-align: right;
}
</style>
