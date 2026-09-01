<template>
  <div class="course-card" @click="emit('go-detail', course)">
    <div class="card-cover" :style="{ background: getCoverGradient(course) }">
      <div class="cover-overlay">
        <el-tag size="small" :type="getStatusType(course.approvalStatus)" effect="dark" round class="status-tag">
          {{ getStatusText(course.approvalStatus) }}
        </el-tag>
        <el-tag
          size="small"
          :type="course.status === 'ACTIVE' ? 'success' : 'info'"
          effect="dark"
          round
          class="status-tag"
        >
          {{ course.status === 'ACTIVE' ? '启用' : '停用' }}
        </el-tag>
      </div>
      <div class="cover-category">
        <span class="category-badge">{{ course.category || '未分类' }}</span>
      </div>
    </div>

    <div class="card-body">
      <h3 class="card-title" :title="course.name">{{ course.name }}</h3>
      <div class="card-meta">
        <div class="meta-item">
          <el-icon :size="14"><CollectionTag /></el-icon>
          <span>{{ course.code || '暂无编号' }}</span>
        </div>
        <div class="meta-item">
          <el-icon :size="14"><User /></el-icon>
          <span>{{ course.studentCount || 0 }} 人选修</span>
        </div>
        <div v-if="course.semester" class="meta-item">
          <el-icon :size="14"><Calendar /></el-icon>
          <span>{{ course.semester }}</span>
        </div>
      </div>
      <div v-if="course.description" class="card-desc">{{ course.description }}</div>
    </div>

    <div class="card-footer" @click.stop>
      <el-button text size="small" type="primary" @click.stop="emit('go-detail', course)"
        >进入课程</el-button
      >
      <el-button text size="small" type="primary" @click.stop="emit('view-detail', course)"
        >详情</el-button
      >
      <el-button
        text
        size="small"
        :type="course.status === 'ACTIVE' ? 'warning' : 'success'"
        @click.stop="emit('toggle-status', course)"
      >
        {{ course.status === 'ACTIVE' ? '停用' : '启用' }}
      </el-button>
      <el-button text size="small" type="danger" @click.stop="emit('delete', course)"
        >删除</el-button
      >
    </div>
  </div>
</template>

<script setup lang="ts">
import { CollectionTag, User, Calendar } from '@element-plus/icons-vue'

/**
 * 教师课程管理 — 单个课程卡片。
 * <p>拆分子组件：自包含封面渐变分配与状态文案映射，操作通过 emit 交还页面壳处理。</p>
 */
defineProps({
  course: { type: Object, required: true },
})

const emit = defineEmits(['view-detail', 'go-detail', 'toggle-status', 'delete'])

// 预定义渐变色数组，按课程ID分配不同颜色
const coverGradients = [
  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
  'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
  'linear-gradient(135deg, #fccb90 0%, #d57eeb 100%)',
  'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)',
  'linear-gradient(135deg, #48c6ef 0%, #6f86d6 100%)',
  'linear-gradient(135deg, #0ba360 0%, #3cba92 100%)',
]

function getCoverGradient(course) {
  const idx = (course.id || 0) % coverGradients.length
  return coverGradients[idx]
}

function getStatusType(status) {
  return status === 'APPROVED' ? 'success' : status === 'REJECTED' ? 'danger' : 'warning'
}

function getStatusText(status) {
  return status === 'APPROVED' ? '已通过' : status === 'REJECTED' ? '已拒绝' : '待审核'
}
</script>

<style scoped>
/* 卡片主体 */
.course-card {
  background: var(--ac-bg-elevated, #fff);
  border: 1px solid var(--ac-border, #e5e7eb);
  border-radius: var(--ac-radius-md, 10px);
  overflow: hidden;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
  display: flex;
  flex-direction: column;
}
.course-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.08);
}

.card-cover {
  position: relative;
  height: 110px;
  display: flex;
  align-items: flex-end;
  padding: 12px 16px;
}
.cover-overlay {
  position: absolute;
  top: 10px;
  right: 12px;
  display: flex;
  gap: 6px;
}
.status-tag {
  font-size: 11px;
}
.cover-category {
  position: relative;
  z-index: 1;
}
.category-badge {
  display: inline-block;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(4px);
  color: #fff;
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 12px;
  font-weight: 500;
}

.card-body {
  padding: 16px 16px 0;
  flex: 1;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary, #1e293b);
  margin: 0 0 10px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 8px;
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12.5px;
  color: var(--ac-text-secondary, #64748b);
}
.card-desc {
  font-size: 12.5px;
  color: var(--ac-text-secondary, #94a3b8);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 8px;
  min-height: 20px;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 8px 12px;
  border-top: 1px solid var(--ac-border, #f1f5f9);
  gap: 2px;
}
</style>
