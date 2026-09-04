<template>
  <div>
    <!-- 工具栏 -->
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-select
          :model-value="statusFilter"
          placeholder="状态筛选"
          clearable
          class="filter-select"
          @update:model-value="emit('update:status', $event)"
          @change="emit('search')"
        >
          <el-option label="全部" value="" />
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已结束" value="ENDED" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-input
          :model-value="searchText"
          placeholder="搜索试卷标题..."
          clearable
          class="search-input"
          :prefix-icon="Search"
          @update:model-value="emit('update:search', $event)"
          @clear="emit('search')"
          @keyup.enter="emit('search')"
        />
        <el-button :icon="Search" @click="emit('search')">搜索</el-button>
      </div>
    </div>

    <!-- 试卷列表 -->
    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico l1-ico--warn">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path
              d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2zM14 2v6h6"
            />
          </svg>
        </span>
        <div>
          <div class="l1-title">试卷列表</div>
          <div class="l1-sub">支持按状态筛选、搜索与发布管理</div>
        </div>
      </div>
      <div class="l1-body">
        <div class="content-card">
          <el-table v-loading="loading" :data="examList">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="title" label="试卷标题" min-width="180" show-overflow-tooltip>
              <template #default="{ row }"
                ><span class="exam-title">{{ row.title }}</span></template
              >
            </el-table-column>
            <el-table-column
              prop="courseName"
              label="所属课程"
              min-width="120"
              show-overflow-tooltip
            />
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">{{
                  getStatusText(row.status)
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="questionCount" label="题目数" width="80" align="center" />
            <el-table-column prop="totalScore" label="总分" width="70" align="center" />
            <el-table-column prop="duration" label="时长(分)" width="85" align="center" />
            <el-table-column prop="attemptCount" label="参考人数" width="90" align="center" />
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="emit('detail', row)"
                  >详情</el-button
                >
                <el-button
                  v-if="row.status === 'DRAFT'"
                  text
                  type="primary"
                  size="small"
                  @click="emit('edit', row)"
                  >编辑</el-button
                >
                <el-button
                  v-if="row.status === 'DRAFT'"
                  text
                  type="success"
                  size="small"
                  @click="emit('publish', row)"
                  >发布</el-button
                >
                <el-button text type="warning" size="small" @click="emit('copy', row)"
                  >复制</el-button
                >
                <el-button
                  v-if="row.status === 'DRAFT'"
                  text
                  type="danger"
                  size="small"
                  @click="emit('delete', row)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && examList.length === 0" description="暂无试卷数据">
            <template #image><div class="empty-deco">📝</div></template>
          </el-empty>
          <div v-if="total > pageSize" class="pagination-wrap">
            <el-pagination
              :current-page="currentPage"
              :page-size="pageSize"
              :total="total"
              layout="total, prev, pager, next"
              @update:current-page="emit('page-change', $event)"
              @current-change="emit('search')"
            />
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

/**
 * 试卷管理列表（工具栏 + 表格 + 分页）
 * <p>状态筛选/关键字搜索 + 试卷列表（状态标签与操作按钮），
 * 全部操作经事件上抛父组件（useTeacherExams）。</p>
 */
defineProps({
  examList: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  searchText: { type: String, default: '' },
  statusFilter: { type: String, default: '' },
  currentPage: { type: Number, default: 1 },
  pageSize: { type: Number, default: 15 },
  total: { type: Number, default: 0 },
  getStatusType: { type: Function, required: true },
  getStatusText: { type: Function, required: true },
})

const emit = defineEmits([
  'update:search',
  'update:status',
  'search',
  'page-change',
  'detail',
  'edit',
  'publish',
  'copy',
  'delete',
])
</script>

<style scoped>
.filter-select {
  width: 130px;
  margin-left: 8px;
}
.search-input {
  width: 240px;
}
.exam-title {
  font-weight: 500;
  color: var(--ac-text-primary);
}
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ac-spacing-md);
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.l1-ico--warn {
  background: var(--warn-soft);
  color: var(--warn);
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  padding-top: var(--ac-spacing-md);
}
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
</style>
