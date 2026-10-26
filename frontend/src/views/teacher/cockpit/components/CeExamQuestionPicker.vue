<template>
  <div class="picker">
    <!-- 工具栏：题型筛选 + 题库总量 -->
    <div class="picker-toolbar">
      <el-select
        :model-value="type"
        size="small"
        clearable
        placeholder="全部题型"
        class="type-filter"
        @change="onTypeChange"
      >
        <el-option
          v-for="opt in typeOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <span class="bank-total">共 {{ total }} 题</span>
    </div>

    <!-- 题库列表：题干截断 / 题型 tag / 分值 / 勾选 -->
    <div v-loading="loading" class="picker-list">
      <div v-if="!questions.length && !loading" class="picker-empty">题库中暂无匹配题目</div>
      <div
        v-for="q in questions"
        :key="q.id"
        class="picker-row"
        :class="{ picked: isRowSelected(q) }"
      >
        <el-checkbox
          class="row-check"
          :model-value="isRowSelected(q)"
          @change="emit('toggle', q)"
        />
        <div class="row-main">
          <div class="row-content" :title="contentOf(q)">{{ contentOf(q) || '（无题干）' }}</div>
          <div class="row-meta">
            <el-tag size="small" :type="questionTypeTag(q.type)" effect="light">{{
              questionTypeLabel(q.type)
            }}</el-tag>
            <span v-if="q.difficulty" class="row-diff">{{ difficultyLabel(q.difficulty) }}</span>
          </div>
        </div>
        <div class="row-points">
          <!-- 已选题：分值可改（实时联动合计）；未选题：只读展示题库默认分 -->
          <template v-if="isRowSelected(q)">
            <el-input-number
              class="pts-input"
              :model-value="rowPoints(q)"
              size="small"
              :min="0.5"
              :max="100"
              :step="0.5"
              controls-position="right"
              @change="(value) => onPointsChange(q, value)"
            />
            <span class="pts-unit">分</span>
          </template>
          <span v-else class="pts-hint">{{ rowPoints(q) }} 分</span>
        </div>
      </div>
    </div>

    <!-- 合计行 + 分页 -->
    <div class="picker-foot">
      <span class="picked-summary">
        已选 <b>{{ selected.length }}</b> 题，合计 <b class="picked-total">{{ selectedTotal }}</b> 分
      </span>
      <el-pagination
        v-if="total > pageSize"
        small
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="(p) => emit('page-change', Number(p))"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PropType } from 'vue'
import {
  QUESTION_TYPE_OPTIONS,
  questionTypeLabel,
  questionTypeTag,
  questionDifficultyLabel,
  type QuestionTypeOption,
} from '@/constants/questionTypes'
import {
  questionContentOf,
  type BankQuestion,
  type SelectedQuestion,
} from '@/composables/useExamCompose'

/**
 * 课程试卷 — 题库选题区。
 * <p>纯展示 + 交互 emit：题库列表（题干截断/题型 tag/分值/勾选）+ 题型筛选 + 分页 +
 * 已选分值实时合计行。数据与勾选状态由宿主（CeExamFormDialog → useExamCompose）持有。</p>
 */
const props = defineProps({
  /** 题库当前页列表 */
  questions: { type: Array as PropType<BankQuestion[]>, default: () => [] },
  loading: { type: Boolean, default: false },
  /** 题库匹配总数（分页用） */
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  /** 当前题型筛选（'' = 全部） */
  type: { type: String, default: '' },
  /** 已选题目（宿主持有，勾选态与分值以它为准） */
  selected: { type: Array as PropType<SelectedQuestion[]>, default: () => [] },
  /** 题型选项（缺省用全局题型契约常量） */
  typeOptions: {
    type: Array as PropType<readonly QuestionTypeOption[]>,
    default: () => QUESTION_TYPE_OPTIONS,
  },
  pageSize: { type: Number, default: 10 },
})

const emit = defineEmits<{
  (e: 'toggle', question: BankQuestion): void
  (e: 'update-points', questionId: number, points: number): void
  (e: 'page-change', page: number): void
  (e: 'type-change', type: string): void
}>()

// 难度标签走共享契约（实库 difficulty 为小写，查找大小写不敏感）；空值由模板 v-if 隐藏
function difficultyLabel(difficulty: string): string {
  return questionDifficultyLabel(difficulty)
}

function isRowSelected(question: BankQuestion): boolean {
  return props.selected.some((s) => s.questionId === question.id)
}

/** 已选题读已选分值（宿主可改过），未选题读题库默认分 */
function rowPoints(question: BankQuestion): number {
  const picked = props.selected.find((s) => s.questionId === question.id)
  if (picked) return Number(picked.score) || 0
  const value = Number(question.score)
  return Number.isFinite(value) && value > 0 ? value : 1
}

function contentOf(question: BankQuestion): string {
  return questionContentOf(question)
}

/** 已选分值实时合计（展示口径与 useExamCompose.selectedTotalScore 一致） */
const selectedTotal = computed(() =>
  props.selected.reduce((sum, q) => sum + (Number(q.score) || 0), 0),
)

function onTypeChange(value: string | undefined): void {
  emit('type-change', String(value || ''))
}

function onPointsChange(question: BankQuestion, value: unknown): void {
  emit('update-points', Number(question.id), Number(value))
}
</script>

<style scoped>
.picker {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.picker-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.type-filter {
  width: 160px;
}
.bank-total {
  font-size: 12px;
  color: var(--ac-text-muted);
}
.picker-list {
  min-height: 120px;
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface);
}
.picker-empty {
  padding: 28px 0;
  text-align: center;
  font-size: 12.5px;
  color: var(--ac-text-muted);
}
.picker-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--ac-border);
}
.picker-row:last-child {
  border-bottom: none;
}
.picker-row.picked {
  background: var(--ac-surface-2);
}
.row-main {
  flex: 1;
  min-width: 0;
}
.row-content {
  font-size: 13px;
  color: var(--ac-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.row-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 3px;
}
.row-diff {
  font-size: 11.5px;
  color: var(--ac-text-muted);
}
.row-points {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
}
.pts-input {
  width: 96px;
}
.pts-unit,
.pts-hint {
  font-size: 12px;
  color: var(--ac-text-muted);
}
.picker-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.picked-summary {
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}
.picked-total {
  color: var(--ac-primary);
}
</style>
