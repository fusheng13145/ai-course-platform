<template>
  <div v-loading="loading" class="table-area">
    <el-table :data="questionList" style="width: 100%">
      <el-table-column label="题干" min-width="320" show-overflow-tooltip>
        <template #default="{ row }"
          ><span class="stem-cell">{{ row.content || row.stem || '—' }}</span></template
        >
      </el-table-column>
      <el-table-column label="类型" width="96" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="light" :type="getTypeTag(row.type)">{{
            getTypeText(row.type)
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="难度" width="100" align="center">
        <template #default="{ row }"
          ><span :style="{ color: getDifficultyColor(row.difficulty), fontWeight: 600 }">{{
            getDifficultyText(row.difficulty)
          }}</span></template
        >
      </el-table-column>
      <el-table-column prop="courseName" label="所属课程" min-width="140" show-overflow-tooltip />
      <el-table-column label="审核" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="getReviewTag(resolveReviewStatus(row))" effect="light">{{
            getReviewText(resolveReviewStatus(row))
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right" align="center">
        <template #default="{ row }">
          <div class="action-row">
            <el-button link type="primary" size="small" @click="$emit('edit', row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="$emit('review', row)"
              >审核</el-button
            >
            <el-button link type="success" size="small" @click="$emit('copy', row)">复制</el-button>
            <el-button link type="danger" size="small" @click="$emit('delete', row)"
              >删除</el-button
            >
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="!loading && !questionList.length" class="empty-wrap">
      <EmptyState title="题库暂无题目，点击右上角「新建题目」或「AI 生成」" icon="book" />
    </div>
    <div v-if="total > pageSize" class="pagination-wrap">
      <el-pagination
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @update:current-page="$emit('update:page', $event)"
        @update:page-size="$emit('update:pageSize', $event)"
        @size-change="$emit('search')"
        @current-change="$emit('search')"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { resolveReviewStatus } from '@/composables/useQuestionBank'
import EmptyState from '@/components/ui/EmptyState.vue'

/**
 * 题库管理 — 题目列表
 * <p>题干/类型/难度/课程/审核状态表格 + 行操作（编辑/审核/复制/删除）+ 分页。</p>
 *
 * 审核列修复（2026-09-21，P1-1）：原先传 `row.reviewStatus`，但后端无该字段
 * （审核态存于 `is_active`），导致恒显示「待审核」。现统一走 resolveReviewStatus 推导。
 */
defineProps({
  /** 题目列表 */
  questionList: { type: Array, required: true },
  /** 加载态 */
  loading: { type: Boolean, default: false },
  /** 总条数 */
  total: { type: Number, default: 0 },
  /** 当前页（v-model） */
  page: { type: Number, default: 1 },
  /** 每页条数（v-model） */
  pageSize: { type: Number, default: 10 },
  /** 题型 → 文本 */
  getTypeText: { type: Function, required: true },
  /** 题型 → 标签色 */
  getTypeTag: { type: Function, required: true },
  /** 难度 → 文本 */
  getDifficultyText: { type: Function, required: true },
  /** 难度 → 颜色 */
  getDifficultyColor: { type: Function, required: true },
  /** 审核状态 → 文本 */
  getReviewText: { type: Function, required: true },
  /** 审核状态 → 标签色 */
  getReviewTag: { type: Function, required: true },
})

defineEmits(['edit', 'review', 'copy', 'delete', 'update:page', 'update:pageSize', 'search'])
</script>

<style scoped>
.table-area {
  min-height: 200px;
}
.stem-cell {
  color: var(--ac-text-primary);
  font-size: 13px;
}
.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  flex-wrap: nowrap;
}
.empty-wrap {
  padding: 30px 0;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}
</style>
