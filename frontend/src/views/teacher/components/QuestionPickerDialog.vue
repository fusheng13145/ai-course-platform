<template>
  <el-dialog
    :model-value="modelValue"
    title="从题库选择题目"
    width="760px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="search-form">
      <el-input
        :model-value="questionSearch"
        placeholder="搜索题目内容..."
        clearable
        class="qk-search"
        :prefix-icon="Search"
        @update:model-value="emit('update:search', $event)"
      />
      <el-select
        :model-value="questionTypeFilter"
        placeholder="题型筛选"
        clearable
        class="qk-type"
        @update:model-value="emit('update:type', $event)"
      >
        <el-option label="单选题" value="SINGLE_CHOICE" />
        <el-option label="多选题" value="MULTIPLE_CHOICE" />
        <el-option label="判断题" value="TRUE_FALSE" />
        <el-option label="填空题" value="FILL_BLANK" />
        <el-option label="简答题" value="ESSAY" />
      </el-select>
      <el-button type="primary" @click="emit('search')">搜索</el-button>
    </div>
    <el-table
      v-loading="questionLoading"
      :data="filteredQuestionBank"
      max-height="360"
      @selection-change="emit('selection-change', $event)"
    >
      <el-table-column type="selection" width="45" :selectable="(row) => !isQuestionSelected(row.id)" />
      <el-table-column prop="content" label="题目内容" min-width="280" show-overflow-tooltip />
      <el-table-column prop="type" label="题型" width="95" align="center">
        <template #default="{ row }"
          ><el-tag size="small" type="info">{{ getQuestionTypeName(row.type) }}</el-tag></template
        >
      </el-table-column>
      <el-table-column prop="difficulty" label="难度" width="75" align="center">
        <template #default="{ row }">
          <span :style="{ color: getDifficultyColor(row.difficulty) }">{{
            getDifficultyText(row.difficulty)
          }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center">
        <template #default="{ row }">
          <el-button
            text
            type="primary"
            size="small"
            :disabled="isQuestionSelected(row.id)"
            @click="emit('add-single', row)"
            >添加</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" @click="emit('confirm')"
        >确认添加 ({{ tempSelectedQuestions.length }})</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

/**
 * 题库选题弹窗
 * <p>搜索/题型筛选 + 多选（排除已添加）+ 单题快捷添加；
 * 选择状态与确认逻辑由父组件（useTeacherExams）承载。</p>
 */
defineProps({
  modelValue: { type: Boolean, default: false },
  questionLoading: { type: Boolean, default: false },
  filteredQuestionBank: { type: Array, default: () => [] },
  questionSearch: { type: String, default: '' },
  questionTypeFilter: { type: String, default: '' },
  tempSelectedQuestions: { type: Array, default: () => [] },
  isQuestionSelected: { type: Function, required: true },
  getQuestionTypeName: { type: Function, required: true },
  getDifficultyColor: { type: Function, required: true },
  getDifficultyText: { type: Function, required: true },
})

const emit = defineEmits([
  'update:modelValue',
  'update:search',
  'update:type',
  'search',
  'selection-change',
  'add-single',
  'confirm',
])
</script>

<style scoped>
.search-form {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.qk-search {
  width: 260px;
}
.qk-type {
  width: 140px;
}
</style>
