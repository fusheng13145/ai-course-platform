<template>
  <div class="filter-bar">
    <div class="filter-left">
      <el-select
        v-model="filters.courseId"
        placeholder="选择课程"
        clearable
        class="w-180"
        @change="emit('search')"
      >
        <el-option
          v-for="course in courseList"
          :key="course.id"
          :label="course.title || course.name"
          :value="course.id"
        />
      </el-select>
      <el-select
        v-model="filters.semester"
        placeholder="学期"
        clearable
        class="w-140"
        @change="emit('search')"
      >
        <el-option label="2026春季" value="2026-SPRING" />
        <el-option label="2025秋季" value="2025-FALL" />
        <el-option label="2025春季" value="2025-SPRING" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        placeholder="搜索学生姓名/学号"
        clearable
        class="w-200"
        @keyup.enter="emit('search')"
      />
      <el-button type="primary" @click="emit('search')"
        ><el-icon><Search /></el-icon>搜索</el-button
      >
      <el-button @click="emit('reset')">重置</el-button>
    </div>
    <div class="filter-right">
      <el-checkbox
        :model-value="showAbnormalOnly"
        @update:model-value="emit('update:showAbnormalOnly', $event)"
        @change="emit('search')"
        >仅显示异常</el-checkbox
      >
    </div>
  </div>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'

/**
 * 成绩管理筛选工具栏
 * <p>课程/学期/关键字筛选 + 仅显示异常开关；任一条件变化经 search 事件上抛，
 * 重置经 reset 事件交由父组件（useGrades）处理。</p>
 */
defineProps({
  /** 筛选条件对象（courseId/semester/keyword） */
  filters: { type: Object, required: true },
  /** 课程下拉选项 */
  courseList: { type: Array, default: () => [] },
  /** 仅显示异常开关 */
  showAbnormalOnly: { type: Boolean, default: false },
})

const emit = defineEmits(['search', 'reset', 'update:showAbnormalOnly'])
</script>

<style scoped>
.w-180 {
  width: 180px;
}
.w-140 {
  width: 140px;
}
.w-200 {
  width: 200px;
}
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-md);
  padding: 16px 20px;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
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
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .filter-left {
    justify-content: center;
  }
}
</style>
