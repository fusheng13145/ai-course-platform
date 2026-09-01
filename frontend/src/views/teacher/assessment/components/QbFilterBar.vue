<template>
  <div class="toolbar">
    <div class="toolbar-left">
      <el-select
        :model-value="courseId"
        placeholder="全部课程"
        clearable
        filterable
        style="width: 200px"
        @update:model-value="$emit('update:courseId', $event)"
        @change="$emit('search')"
      >
        <el-option v-for="c in courseList" :key="c.id" :label="c.name || c.title" :value="c.id" />
      </el-select>
      <el-select
        :model-value="type"
        placeholder="题型"
        clearable
        style="width: 140px"
        @update:model-value="$emit('update:type', $event)"
        @change="$emit('search')"
      >
        <el-option label="全部" value="" />
        <el-option label="单选题" value="SINGLE_CHOICE" />
        <el-option label="多选题" value="MULTIPLE_CHOICE" />
        <el-option label="判断题" value="TRUE_FALSE" />
        <el-option label="填空题" value="FILL_BLANK" />
        <el-option label="简答题" value="SHORT_ANSWER" />
        <el-option label="编程题" value="CODING" />
      </el-select>
      <el-select
        :model-value="difficulty"
        placeholder="难度"
        clearable
        style="width: 130px"
        @update:model-value="$emit('update:difficulty', $event)"
        @change="$emit('search')"
      >
        <el-option label="全部" value="" />
        <el-option label="简单" value="EASY" />
        <el-option label="中等" value="MEDIUM" />
        <el-option label="困难" value="HARD" />
      </el-select>
      <el-input
        :model-value="keyword"
        placeholder="搜索题干..."
        clearable
        style="width: 240px"
        @update:model-value="$emit('update:keyword', $event)"
        @keyup.enter="$emit('search')"
        @clear="$emit('search')"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" @click="$emit('search')"
        ><el-icon><Search /></el-icon>查询</el-button
      >
    </div>
  </div>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

/**
 * 题库管理 — 筛选工具栏
 * <p>课程/题型/难度/关键词多维筛选。</p>
 */
defineProps({
  /** 课程列表 */
  courseList: { type: Array, required: true },
  /** 课程筛选（v-model） */
  courseId: { type: [String, Number], default: '' },
  /** 题型筛选（v-model） */
  type: { type: String, default: '' },
  /** 难度筛选（v-model） */
  difficulty: { type: String, default: '' },
  /** 关键词（v-model） */
  keyword: { type: String, default: '' },
})

defineEmits(['update:courseId', 'update:type', 'update:difficulty', 'update:keyword', 'search'])
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
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
</style>
