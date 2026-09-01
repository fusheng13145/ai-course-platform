<template>
  <div class="filter-bar">
    <div class="filter-left">
      <el-input
        :model-value="keyword"
        placeholder="搜索题目内容"
        clearable
        style="width: 240px"
        @update:model-value="emit('update:keyword', $event)"
        @keyup.enter="emit('search')"
      >
        <template #prefix
          ><el-icon><Search /></el-icon
        ></template>
      </el-input>
      <el-select
        :model-value="type"
        placeholder="题型筛选"
        clearable
        style="width: 130px"
        @update:model-value="emit('update:type', $event)"
        @change="emit('search')"
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
        placeholder="难度筛选"
        clearable
        style="width: 120px"
        @update:model-value="emit('update:difficulty', $event)"
        @change="emit('search')"
      >
        <el-option label="全部" value="" />
        <el-option label="简单" value="EASY" />
        <el-option label="中等" value="MEDIUM" />
        <el-option label="困难" value="HARD" />
      </el-select>
      <el-select
        :model-value="courseId"
        placeholder="课程筛选"
        clearable
        style="width: 160px"
        @update:model-value="emit('update:course-id', $event)"
        @change="emit('search')"
      >
        <el-option
          v-for="course in courseList"
          :key="course.id"
          :label="course.title || course.name"
          :value="course.id"
        />
      </el-select>
      <el-button type="primary" @click="emit('search')"
        ><el-icon><Search /></el-icon
      ></el-button>
    </div>
    <div class="filter-right">
      <el-button text type="primary" size="small" @click="emit('error-prone')">易错题榜</el-button>
      <el-button text type="primary" size="small" @click="emit('export')">批量导出</el-button>
      <el-button text type="primary" size="small" @click="emit('import')">批量导入</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

/**
 * 题库筛选工具栏
 * <p>关键字搜索 + 题型/难度/课程下拉筛选 + 易错题榜/批量导出/批量导入入口。
 * 全部状态经 props 注入，事件上抛父组件（useQuestions）。</p>
 */
defineProps({
  keyword: { type: String, default: '' },
  type: { type: String, default: '' },
  difficulty: { type: String, default: '' },
  courseId: { type: [String, Number], default: '' },
  courseList: { type: Array, default: () => [] },
})

const emit = defineEmits([
  'update:keyword',
  'update:type',
  'update:difficulty',
  'update:course-id',
  'search',
  'error-prone',
  'export',
  'import',
])
</script>

<style scoped>
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  margin-bottom: var(--ac-spacing-md);
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}

.filter-left {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  flex-wrap: wrap;
}

.filter-right {
  display: flex;
  gap: var(--ac-spacing-xs);
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .filter-left {
    flex-direction: column;
  }
}
</style>
