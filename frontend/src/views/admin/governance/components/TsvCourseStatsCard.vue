<template>
  <div class="content-card">
    <div class="card-title">
      <el-icon><DataLine /></el-icon>开课统计
    </div>
    <div class="sub-label">课程分类分布</div>
    <div class="cat-list">
      <div v-for="(cnt, name) in categoryDistribution" :key="name" class="cat-row">
        <span class="cat-name">{{ name || '未分类' }}</span>
        <div class="cat-bar">
          <div class="cat-bar-fill" :style="{ width: barWidth(cnt) }"></div>
        </div>
        <span class="cat-cnt">{{ cnt }}</span>
      </div>
      <el-empty v-if="!categoryKeys.length" description="暂无课程分类数据" :image-size="60" />
    </div>
    <div class="sub-label" style="margin-top: 18px">热门课程 Top5</div>
    <div class="top-list">
      <div v-for="(c, i) in topCourses" :key="i" class="top-row">
        <span class="rank">{{ i + 1 }}</span>
        <span class="top-name">{{
          c.courseName || c.name || '课程 #' + (c.courseId || c.id)
        }}</span>
        <span class="top-cnt">{{ c.enrollmentCount ?? c.count ?? '—' }} 人选课</span>
      </div>
      <el-empty v-if="!topCourses.length" description="暂无热门课程" :image-size="60" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { DataLine } from '@element-plus/icons-vue'

/**
 * 教学监管 — 开课统计卡
 * <p>课程分类分布（条形占比）+ 热门课程 Top5 排行。</p>
 */
defineProps({
  /** 分类 → 数量映射 */
  categoryDistribution: { type: Object, required: true },
  /** 分类键数组（空态判断） */
  categoryKeys: { type: Array, required: true },
  /** 热门课程列表 */
  topCourses: { type: Array, required: true },
  /** 数量 → 条宽百分比函数 */
  barWidth: { type: Function, required: true },
})
</script>

<style scoped>
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 18px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 14px;
}
.card-title .el-icon {
  color: var(--ac-primary, #3b82f6);
}
.sub-label {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-bottom: 10px;
}

.cat-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.cat-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.cat-name {
  width: 80px;
  color: var(--ac-text-secondary, #475569);
  flex-shrink: 0;
}
.cat-bar {
  flex: 1;
  height: 8px;
  background: rgba(127, 127, 127, 0.1);
  border-radius: 4px;
  overflow: hidden;
}
.cat-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #60a5fa, #2563eb);
  border-radius: 4px;
  transition: width 0.4s ease;
}
.cat-cnt {
  width: 36px;
  text-align: right;
  color: var(--ac-text-primary);
  font-weight: 600;
}

.top-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.top-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--ac-border);
}
.top-row:last-child {
  border-bottom: none;
}
.rank {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--ac-primary, #3b82f6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
.top-name {
  flex: 1;
  color: var(--ac-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.top-cnt {
  color: var(--ac-text-muted);
  flex-shrink: 0;
}
</style>
