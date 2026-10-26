<template>
  <Modal

    :model-value="modelValue"
    title="试卷详情"
    width="700px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="currentExam">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="试卷标题">{{ currentExam.title }}</el-descriptions-item>
        <el-descriptions-item label="所属课程">{{
          currentExam.courseName || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentExam.status)" size="small">{{
            getStatusText(currentExam.status)
          }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总分/时长"
          >{{ currentExam.totalScore }}分 / {{ currentExam.duration }}分钟</el-descriptions-item
        >
        <el-descriptions-item label="参考人数"
          >{{ currentExam.attemptCount || 0 }}人</el-descriptions-item
        >
        <el-descriptions-item label="平均分">{{
          currentExam.avgScore != null ? currentExam.avgScore : '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="最高分">{{
          currentExam.maxScore ?? '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="最低分">{{
          currentExam.minScore ?? '-'
        }}</el-descriptions-item>
      </el-descriptions>

      <!-- 题目预览 -->
      <div class="detail-questions">
        <h4 class="dq-title">题目预览</h4>
        <div v-if="examDetailQuestions && examDetailQuestions.length > 0">
          <div v-for="(q, i) in examDetailQuestions" :key="i" class="detail-question-item">
            <span class="q-num">{{ i + 1 }}.</span>
            <span class="q-type-badge">{{ getQuestionTypeName(q.type) }}</span>
            <span class="q-stem">{{ q.content || q.stem || q.title }}</span>
            <span class="q-score-val">{{ q.score }}分</span>
          </div>
        </div>
        <EmptyState v-else title="暂无题目数据" icon="list" size="compact" />
      </div>
    </template>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button
        v-if="currentExam?.status === 'DRAFT'"
        type="success"
        @click="emit('publish', currentExam)"
        >发布试卷</el-button
      >
      <el-button type="warning" @click="emit('copy', currentExam)">复制试卷</el-button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Modal from '@/components/ui/Modal.vue'

/**
 * 试卷详情对话框
 * <p>基本信息（状态/分数/人数统计）+ 题目预览列表；发布/复制操作经事件上抛。</p>
 */
defineProps({
  modelValue: { type: Boolean, default: false },
  currentExam: { type: Object, default: null },
  examDetailQuestions: {
    type: Array as PropType<
      {
        id?: number | string
        type?: string
        content?: string
        stem?: string
        title?: string
        score?: number
      }[]
    >,
    default: () => [],
  },
  getStatusType: { type: Function, required: true },
  getStatusText: { type: Function, required: true },
  getQuestionTypeName: { type: Function, required: true },
})

// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）

const emit = defineEmits(['update:modelValue', 'publish', 'copy'])
</script>

<style scoped>
.detail-questions {
  margin-top: 20px;
}
.dq-title {
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--ac-text-primary);
}
.detail-question-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ac-border);
  font-size: 13px;
}
.detail-question-item:last-child {
  border-bottom: none;
}
.q-num {
  font-weight: 600;
  color: var(--ac-primary);
  flex-shrink: 0;
}
.q-type-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--info-soft);
  color: var(--ac-primary);
  flex-shrink: 0;
}
.q-stem {
  flex: 1;
  color: var(--ac-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.q-score-val {
  color: var(--ac-warning);
  font-weight: 500;
  flex-shrink: 0;
}
</style>
