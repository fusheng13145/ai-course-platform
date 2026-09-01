<template>
  <div class="table-toolbar">
    <div class="toolbar-left">
      <el-select
        :model-value="status"
        placeholder="状态筛选"
        clearable
        style="width: 130px"
        @update:model-value="$emit('update:status', $event)"
        @change="$emit('search')"
      >
        <el-option label="全部" value="" />
        <el-option label="草稿" value="DRAFT" />
        <el-option label="待审核" value="PENDING" />
        <el-option label="已发布" value="PUBLISHED" />
        <el-option label="已结束" value="ENDED" />
      </el-select>
      <el-input
        :model-value="keyword"
        placeholder="搜索试卷名称/课程"
        clearable
        style="width: 220px"
        @update:model-value="$emit('update:keyword', $event)"
        @keyup.enter="$emit('search')"
      />
      <el-button type="primary" @click="$emit('search')"
        ><el-icon><Search /></el-icon>搜索</el-button
      >
    </div>
  </div>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

/**
 * 全校考试管理 — 筛选工具栏
 * <p>状态筛选 + 关键字搜索。</p>
 */
defineProps({
  /** 状态筛选（v-model） */
  status: { type: String, default: '' },
  /** 搜索关键字（v-model） */
  keyword: { type: String, default: '' },
})

defineEmits(['update:status', 'update:keyword', 'search'])
</script>
