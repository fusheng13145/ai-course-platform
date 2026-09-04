<template>
  <div>
    <div class="toolbar">
      <div class="toolbar-left">
        <el-select
          :model-value="filters.type"
          placeholder="题目类型"
          clearable
          style="width: 150px"
          @update:model-value="$emit('update:type', $event)"
          @change="$emit('query')"
        >
          <el-option v-for="o in typeOpts" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select
          :model-value="filters.difficulty"
          placeholder="难度"
          clearable
          style="width: 140px"
          @update:model-value="$emit('update:difficulty', $event)"
          @change="$emit('query')"
        >
          <el-option v-for="o in diffOpts" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input
          :model-value="filters.keyword"
          placeholder="搜索题干关键词"
          clearable
          style="width: 240px"
          @update:model-value="$emit('update:keyword', $event)"
          @keyup.enter="$emit('query')"
          @clear="$emit('query')"
        />
        <el-button type="primary" :icon="Search" @click="$emit('query')">查询</el-button>
      </div>
    </div>

    <div v-loading="loading" class="table-area">
      <el-table
        :data="list"
        size="small"
        empty-text="暂无题目"
        @row-dblclick="$emit('edit', $event)"
      >
        <el-table-column label="题干" min-width="280">
          <template #default="{ row }"
            ><span class="stem-cell">{{ row.stem || row.title || '（无题干）' }}</span></template
          >
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }"
            ><el-tag size="small" :type="typeTag(row.type)" effect="light">{{
              typeLabel(row.type)
            }}</el-tag></template
          >
        </el-table-column>
        <el-table-column label="难度" width="90">
          <template #default="{ row }"
            ><el-tag size="small" :type="diffTag(row.difficulty)" effect="light">{{
              diffLabel(row.difficulty)
            }}</el-tag></template
          >
        </el-table-column>
        <el-table-column label="审核" width="82">
          <template #default="{ row }">
            <el-tag size="small" :type="row.reviewed ? 'success' : 'warning'" effect="light">{{
              row.reviewed ? '已审核' : '待审核'
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="action-row">
              <el-button link type="primary" :icon="Edit" @click="$emit('edit', row)"
                >编辑</el-button
              >
              <el-button
                v-if="!row.reviewed"
                link
                type="success"
                :icon="Select"
                @click="$emit('review', row, true)"
                >通过</el-button
              >
              <el-button
                v-if="!row.reviewed"
                link
                type="warning"
                :icon="Close"
                @click="$emit('review', row, false)"
                >驳回</el-button
              >
              <el-button link type="info" :icon="CopyDocument" @click="$emit('copy', row)"
                >复制</el-button
              >
              <el-button link type="danger" :icon="Delete" @click="$emit('remove', row)"
                >删除</el-button
              >
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && !list.length" class="empty-wrap">
        <el-empty description="该课程暂无题目，点击右上角「新建题目」或「AI 生成」">
          <template #image><div class="empty-deco">📚</div></template>
        </el-empty>
      </div>
      <div v-if="total > size" class="pagination-wrap">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @update:current-page="$emit('update:page', $event)"
          @update:page-size="$emit('update:size', $event)"
          @current-change="$emit('query')"
          @size-change="$emit('query')"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Search, Edit, Select, Close, CopyDocument, Delete } from '@element-plus/icons-vue'

defineProps({
  loading: { type: Boolean, default: false },
  list: { type: Array, default: () => [] },
  filters: { type: Object, default: () => ({}) },
  typeOpts: { type: Array, default: () => [] },
  diffOpts: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
})
defineEmits([
  'update:type',
  'update:difficulty',
  'update:keyword',
  'update:page',
  'update:size',
  'query',
  'edit',
  'review',
  'copy',
  'remove',
])

function typeLabel(t) {
  const TYPE_OPTS = [
    { value: 'SINGLE', label: '单选题' },
    { value: 'MULTIPLE', label: '多选题' },
    { value: 'JUDGE', label: '判断题' },
    { value: 'FILL', label: '填空题' },
    { value: 'ESSAY', label: '简答题' },
    { value: 'CODE', label: '编程题' },
  ]
  return TYPE_OPTS.find((o) => o.value === t)?.label || t || '未知'
}
function diffLabel(d) {
  const DIFF_OPTS = [
    { value: 'EASY', label: '简单' },
    { value: 'MEDIUM', label: '中等' },
    { value: 'HARD', label: '困难' },
  ]
  return DIFF_OPTS.find((o) => o.value === d)?.label || d || '未知'
}
function typeTag(t) {
  return (
    {
      SINGLE: 'primary',
      MULTIPLE: 'success',
      JUDGE: 'warning',
      FILL: 'info',
      ESSAY: '',
      CODE: 'danger',
    }[t] || 'info'
  )
}
function diffTag(d) {
  return { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }[d] || 'info'
}
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding-bottom: 14px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--line);
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.table-area {
  min-height: 200px;
}
.stem-cell {
  color: var(--ac-text-primary);
  font-size: 13.5px;
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
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}
</style>
