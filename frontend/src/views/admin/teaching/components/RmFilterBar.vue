<template>
  <div class="content-card filter-card">
    <div class="filter-row">
      <el-select
        :model-value="selectedCourseId"
        placeholder="选择课程"
        filterable
        style="width: 240px"
        @update:model-value="$emit('update:selectedCourseId', $event)"
        @change="emit('search')"
      >
        <el-option
          v-for="c in courseList"
          :key="c.id"
          :label="c.name || c.title"
          :value="c.id"
        />
      </el-select>
      <el-input
        :model-value="keyword"
        placeholder="搜索资源名称"
        clearable
        style="width: 200px"
        @update:model-value="$emit('update:keyword', $event)"
        @keyup.enter="emit('search')"
      />
      <el-select
        :model-value="typeFilter"
        placeholder="类型"
        clearable
        style="width: 130px"
        @update:model-value="$emit('update:typeFilter', $event)"
      >
        <el-option label="文档" value="doc" />
        <el-option label="图片" value="image" />
        <el-option label="音视频" value="media" />
        <el-option label="其他" value="other" />
      </el-select>
      <el-button type="primary" @click="emit('search')"
        ><el-icon><Search /></el-icon>查询</el-button
      >
      <div class="spacer"></div>
      <el-button type="primary" :disabled="!selectedCourseId" @click="emit('upload')">
        <el-icon><Upload /></el-icon>上传资源
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Search, Upload } from '@element-plus/icons-vue'

defineProps({
  courseList: { type: Array, default: () => [] },
  selectedCourseId: { type: [Number, String], default: null },
  keyword: { type: String, default: '' },
  typeFilter: { type: String, default: '' },
})
const emit = defineEmits([
  'update:selectedCourseId',
  'update:keyword',
  'update:typeFilter',
  'search',
  'upload',
])
</script>

<style scoped>
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px;
}
.filter-card {
  margin-bottom: var(--ac-spacing-md);
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.spacer {
  flex: 1;
}
</style>
