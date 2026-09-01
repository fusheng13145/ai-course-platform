<template>
  <ProfileCard :icon="Bell" title="学习动态">
    <template #action>
      <el-button text type="primary" size="small" @click="emit('view-todos')">
        全部待办<el-icon><ArrowRight /></el-icon>
      </el-button>
    </template>

    <div v-loading="loading" class="activity-list">
      <div
        v-for="(a, i) in activities"
        :key="i"
        class="activity-item"
        @click="a.courseId && emit('open', { courseId: a.courseId })"
      >
        <div class="activity-dot" :class="'dot--' + a.color">
          <el-icon><component :is="a.icon" /></el-icon>
        </div>
        <div class="activity-body">
          <div class="activity-title">
            <el-tag size="small" :type="a.color" effect="light">{{ a.type }}</el-tag>
            <span class="activity-name">{{ a.title }}</span>
          </div>
          <div v-if="a.due" class="activity-due">截止 {{ formatTime(a.due, '—') }}</div>
        </div>
        <el-icon class="activity-go"><ArrowRight /></el-icon>
      </div>
      <el-empty
        v-if="!loading && activities.length === 0"
        description="暂无学习动态"
        :image-size="72"
      />
    </div>
  </ProfileCard>
</template>

<script setup lang="ts">
import { Bell, ArrowRight } from '@element-plus/icons-vue'
import { formatTime } from '@/utils/datetime'
import ProfileCard from './ProfileCard.vue'

/**
 * 个人中心「学习动态」区块
 * <p>聚合展示待办作业、即将考试与系统通知（按类型着色）；
 * 每项点击后按 courseId 跳转对应课程。</p>
 */
defineProps({
  /** 动态项（type/title/due/courseId/color/icon） */
  activities: { type: Array, default: () => [] },
  /** 加载态 */
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['view-todos', 'open'])
</script>

<style scoped>
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.activity-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 12px;
  border-radius: var(--ac-radius-md);
  cursor: pointer;
  transition: background var(--ac-transition-fast);
}
.activity-item:hover {
  background: var(--ac-bg-hover);
}
.activity-dot {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.activity-dot .el-icon {
  font-size: 17px;
}
.dot--primary {
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
}
.dot--warning {
  background: var(--ac-warning-bg);
  color: var(--ac-warning);
}
.dot--danger {
  background: var(--ac-danger-bg);
  color: var(--ac-danger);
}
.activity-body {
  flex: 1;
  min-width: 0;
}
.activity-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  color: var(--ac-text-primary);
}
.activity-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.activity-due {
  margin-top: 3px;
  font-size: 12px;
  color: var(--ac-text-muted);
}
.activity-go {
  flex-shrink: 0;
  color: var(--ac-text-muted);
  font-size: 16px;
}
</style>
