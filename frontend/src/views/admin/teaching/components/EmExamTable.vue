<template>
  <div class="content-card">
    <el-table v-loading="loading" :data="examList" style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="试卷名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="courseName" label="所属课程" min-width="150" show-overflow-tooltip />
      <el-table-column prop="teacherName" label="创建教师" width="120" />
      <el-table-column prop="totalScore" label="总分" width="70" align="center" />
      <el-table-column prop="duration" label="时长(分钟)" width="100" align="center" />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small" effect="light">
            {{ getStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="$emit('detail', row)">详情</el-button>
          <el-button
            v-if="row.status === 'PENDING'"
            text
            type="success"
            size="small"
            @click="$emit('approve', row)"
            >通过</el-button
          >
          <el-button
            v-if="row.status === 'PENDING'"
            text
            type="danger"
            size="small"
            @click="$emit('reject', row)"
            >拒绝</el-button
          >
          <el-button text type="warning" size="small" @click="$emit('stats', row)">统计</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        :current-page="page"
        :page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @update:current-page="$emit('update:page', $event)"
        @update:page-size="$emit('update:size', $event)"
        @size-change="$emit('search')"
        @current-change="$emit('search')"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 全校考试管理 — 试卷列表
 * <p>试卷表格（状态标签/操作）与分页控件。</p>
 */
defineProps({
  /** 试卷列表 */
  examList: { type: Array, required: true },
  /** 加载态 */
  loading: { type: Boolean, default: false },
  /** 当前页（v-model） */
  page: { type: Number, default: 1 },
  /** 每页条数（v-model） */
  size: { type: Number, default: 10 },
  /** 总条数 */
  total: { type: Number, default: 0 },
  /** 状态 → 标签色映射 */
  getStatusType: { type: Function, required: true },
  /** 状态 → 文本映射 */
  getStatusText: { type: Function, required: true },
})

defineEmits(['detail', 'approve', 'reject', 'stats', 'update:page', 'update:size', 'search'])
</script>

<style scoped>
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
}

.pagination-wrapper {
  margin-top: var(--ac-spacing-md);
  display: flex;
  justify-content: flex-end;
}
</style>
