<template>
  <div>
    <PageHeader
      title="我的考试"
      subtitle="查看并参与课程考试，跟踪学习成果"
      icon="doc"
      icon-bg="accent"
    />

    <div class="stat-grid animate-in d1">
      <StatCard
        label="考试总数"
        :value="totalExams"
        icon="doc"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="可参加"
        :value="availableExams"
        icon="play"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="已完成"
        :value="completedExams"
        icon="checks"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
    </div>

    <section class="l1-card animate-in d2">
      <div class="l1-body">
        <!-- 搜索工具栏 -->
        <div class="search-toolbar">
          <el-input
            :model-value="keyword"
            placeholder="搜索考试名称..."
            clearable
            :prefix-icon="Search"
            class="search-input"
            @update:model-value="emit('update:keyword', $event)"
          />
          <el-select
            :model-value="statusFilter"
            placeholder="状态筛选"
            clearable
            class="status-select"
            @update:model-value="emit('update:status-filter', $event)"
          >
            <el-option label="可参加" value="available" />
            <el-option label="已完成" value="completed" />
            <el-option label="已过期" value="expired" />
          </el-select>
        </div>

        <!-- 考试卡片网格 -->
        <div class="exam-grid">
          <div v-for="exam in exams" :key="exam.id" class="exam-card">
            <div class="exam-card-header">
              <span class="exam-type-tag">{{ exam.type || '综合测试' }}</span>
              <el-tag :type="getExamStatusType(exam)" size="small" effect="light">
                {{ getExamStatusLabel(exam) }}
              </el-tag>
            </div>

            <h3 class="exam-card-title">{{ exam.title }}</h3>

            <div class="exam-meta-grid">
              <div class="meta-item">
                <el-icon><Collection /></el-icon>
                <span>{{ exam.courseName || '未知课程' }}</span>
              </div>
              <div class="meta-item">
                <el-icon><Document /></el-icon>
                <span>{{ exam.totalScore || 100 }} 分</span>
              </div>
              <div class="meta-item">
                <el-icon><Timer /></el-icon>
                <span>{{ exam.duration || exam.timeLimit || 60 }} 分钟</span>
              </div>
              <div v-if="exam.questionCount" class="meta-item">
                <el-icon><List /></el-icon>
                <span>{{ exam.questionCount }} 题</span>
              </div>
            </div>

            <p class="exam-desc">{{ exam.description || '暂无描述' }}</p>

            <div class="exam-card-footer">
              <span v-if="exam.startTime" class="exam-time">
                {{ formatTime(exam.startTime, '') }} ~ {{ formatTime(exam.endTime, '') }}
              </span>
              <el-button
                type="primary"
                :disabled="exam.completed || exam.status === 'EXPIRED'"
                @click="emit('enter', exam)"
              >
                {{ exam.completed ? '已完成' : exam.status === 'EXPIRED' ? '已过期' : '开始考试' }}
              </el-button>
            </div>
          </div>
        </div>

        <el-empty v-if="!loading && exams.length === 0" :description="emptyText" :image-size="120">
          <template #image><div class="empty-deco">📝</div></template>
        </el-empty>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import { Search, Collection, Document, Timer, List } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

/** 考试卡片行（duration/timeLimit 等字段名存在历史分叉，故均可选） */
interface ExamCardRow {
  id?: number | string
  type?: string
  title?: string
  courseName?: string
  totalScore?: number
  duration?: number
  timeLimit?: number
  questionCount?: number
  description?: string
  startTime?: string
  endTime?: string
  completed?: boolean
  status?: string
}

/**
 * 学生考试列表视图
 * <p>头部统计（总数/可参加/已完成）+ 搜索与状态筛选工具栏 + 考试卡片网格。
 * 全部状态经 props 注入，交互经事件上抛父组件（useStudentExams）。</p>
 */
defineProps({
  exams: { type: Array as PropType<ExamCardRow[]>, default: () => [] },
  loading: { type: Boolean, default: false },
  keyword: { type: String, default: '' },
  statusFilter: { type: String, default: '' },
  totalExams: { type: Number, default: 0 },
  availableExams: { type: Number, default: 0 },
  completedExams: { type: Number, default: 0 },
  getExamStatusType: { type: Function, required: true },
  getExamStatusLabel: { type: Function, required: true },
  formatTime: { type: Function, required: true },
  /**
   * 空态文案。默认值是"确实没有考试"的语义；调用方在**加载失败**时应传入
   * 失败文案，避免把"取不到数据"渲染成"暂无考试"。
   */
  emptyText: { type: String, default: '暂无考试' },
})

const emit = defineEmits(['update:keyword', 'update:status-filter', 'enter'])
</script>

<style scoped>
/* ---- 搜索工具栏 ---- */
.search-toolbar {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}

.search-input {
  width: 260px;
}
.status-select {
  width: 140px;
}

/* ---- 考试卡片网格 ---- */
.exam-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: var(--ac-spacing-md);
}

.exam-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  transition: all var(--ac-transition-normal);
  display: flex;
  flex-direction: column;
}

.exam-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--ac-shadow-md);
  border-left: 4px solid var(--ac-primary);
}

.exam-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-sm);
}

.exam-type-tag {
  font-size: 12px;
  color: var(--ac-text-muted);
  background: var(--ac-bg);
  padding: 2px 10px;
  border-radius: 999px;
}

.exam-card-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0 0 var(--ac-spacing-sm);
}

.exam-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
  margin-bottom: var(--ac-spacing-sm);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ac-text-secondary);
}

.meta-item .el-icon {
  font-size: 14px;
  color: var(--ac-text-muted);
}

.exam-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
  line-height: 1.6;
  margin: 0 0 var(--ac-spacing-md);
  flex: 1;
}

.exam-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--ac-spacing-sm);
  border-top: 1px solid var(--ac-border);
}

.exam-time {
  font-size: 12px;
  color: var(--ac-text-muted);
}

.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
</style>
