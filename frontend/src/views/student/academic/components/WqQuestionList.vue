<template>
  <div v-loading="loading" class="wrong-questions-list">
    <transition-group name="list-fade" tag="div">
      <div
        v-for="q in questions"
        :key="q.id"
        class="question-card"
        :class="{ expanded: expandedId === q.id }"
      >
        <!-- 题目头部 -->
        <div class="q-header" @click="$emit('toggle', q.id)">
          <div class="q-header-left">
            <el-tag :type="q.mastered ? 'success' : 'warning'" size="small">
              {{ q.mastered ? '已掌握' : '待复习' }}
            </el-tag>
            <span v-if="q.knowledgePointName" class="q-kp-tag">{{
              q.knowledgePointName
            }}</span>
          </div>
          <div class="q-header-right">
            <el-button v-if="!q.mastered" text size="small" @click.stop="$emit('related', q)">
              <el-icon><Link /></el-icon> 相关推荐
            </el-button>
            <el-button v-if="!q.mastered" text size="small" @click.stop="$emit('practice', q)">
              <el-icon><RefreshRight /></el-icon> 再次练习
            </el-button>
            <el-icon class="expand-icon" :class="{ expanded: expandedId === q.id }"
              ><ArrowDown
            /></el-icon>
          </div>
        </div>

        <!-- 题目内容（始终显示） -->
        <div class="q-content">
          <p class="q-text">{{ q.questionContent || q.content || '未知题目内容' }}</p>
          <div class="q-answer-row">
            <span class="answer-label">你的答案：</span>
            <span class="answer-value wrong">{{
              q.wrongAnswer || q.studentAnswer || '未记录'
            }}</span>
          </div>
        </div>

        <!-- 展开区域：正确答案 + 解析 -->
        <transition name="expand-fade">
          <div v-if="expandedId === q.id" class="q-detail">
            <div class="detail-section">
              <span class="detail-label correct">正确答案：</span>
              <span class="detail-value correct">{{
                q.correctAnswer || q.answer || '--'
              }}</span>
            </div>
            <div v-if="q.analysis || q.explanation" class="detail-section">
              <span class="detail-label">解析：</span>
              <p class="analysis-text">{{ q.analysis || q.explanation }}</p>
            </div>
            <div class="detail-actions">
              <el-button v-if="!q.mastered" type="success" size="small" @click="$emit('mark', q)">
                <el-icon><Select /></el-icon> 标记为已掌握
              </el-button>
              <el-button v-else type="info" size="small" plain @click="$emit('unmark', q)">
                取消掌握标记
              </el-button>
            </div>
          </div>
        </transition>
      </div>
    </transition-group>

    <el-empty
      v-if="!loading && questions.length === 0"
      description="暂无错题记录，继续保持！"
      :image-size="100"
    >
      <template #image><div class="empty-deco">📕</div></template>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { Link, RefreshRight, ArrowDown, Select } from '@element-plus/icons-vue'

defineProps({
  loading: { type: Boolean, default: false },
  questions: { type: Array, default: () => [] },
  expandedId: { type: [Number, String], default: null },
})
defineEmits(['toggle', 'mark', 'unmark', 'practice', 'related'])
</script>

<style scoped>
.wrong-questions-list {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-sm);
}
.question-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-md) var(--ac-spacing-lg);
  transition: all var(--ac-transition-normal);
  overflow: hidden;
}
.question-card:hover {
  box-shadow: var(--ac-shadow-sm);
  border-left: 3px solid var(--ac-danger);
}
.question-card.expanded {
  border-left-color: var(--ac-primary);
}
.q-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  margin-bottom: var(--ac-spacing-sm);
}
.q-header-left {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
}
.q-kp-tag {
  font-size: 12px;
  color: var(--ac-text-muted);
  background: var(--ac-bg);
  padding: 2px 10px;
  border-radius: 999px;
}
.q-header-right {
  display: flex;
  align-items: center;
  gap: 2px;
}
.expand-icon {
  color: var(--ac-text-muted);
  font-size: 14px;
  transition: transform var(--ac-transition-fast);
  margin-left: 4px;
}
.expand-icon.expanded {
  transform: rotate(180deg);
}
.q-content {
  margin-bottom: var(--ac-spacing-xs);
}
.q-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--ac-text-primary);
  line-height: 1.7;
  margin: 0 0 var(--ac-spacing-sm);
}
.q-answer-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 13px;
}
.answer-label {
  color: var(--ac-text-muted);
  white-space: nowrap;
}
.answer-value {
  font-weight: 500;
}
.answer-value.wrong {
  color: var(--ac-danger);
  text-decoration: line-through;
  opacity: 0.8;
}
.answer-value.correct {
  color: var(--ac-success);
  font-weight: 600;
}
.q-detail {
  margin-top: var(--ac-spacing-md);
  padding-top: var(--ac-spacing-md);
  border-top: 1px dashed var(--ac-border);
}
.detail-section {
  margin-bottom: var(--ac-spacing-sm);
}
.detail-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ac-text-secondary);
  margin-right: 6px;
}
.detail-label.correct {
  color: var(--ac-success);
}
.detail-value {
  font-size: 14px;
  color: var(--ac-text-primary);
  font-weight: 500;
}
.detail-value.correct {
  color: var(--ac-success);
}
.analysis-text {
  font-size: 13px;
  color: var(--ac-text-secondary);
  line-height: 1.8;
  margin: 6px 0 0;
  padding: 10px 14px;
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  white-space: pre-wrap;
}
.detail-actions {
  display: flex;
  gap: var(--ac-spacing-sm);
  margin-top: var(--ac-spacing-sm);
}

/* 动画 */
.list-fade-enter-active,
.list-fade-leave-active {
  transition: all 0.3s ease;
}
.list-fade-enter-from,
.list-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
.expand-fade-enter-active {
  transition: all 0.25s ease;
}
.expand-fade-leave-active {
  transition: all 0.15s ease;
}
.expand-fade-enter-from,
.expand-fade-leave-to {
  opacity: 0;
  max-height: 0;
  overflow: hidden;
}
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

@media (max-width: 768px) {
  .q-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--ac-spacing-xs);
  }
  .q-header-right {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
