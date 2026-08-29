<template>
  <div>
    <div class="toolbar">
      <div class="toolbar-left">
        <el-select
          :model-value="filters.courseId"
          placeholder="全部课程"
          clearable
          filterable
          style="width: 200px"
          @update:model-value="$emit('update:courseId', $event)"
          @change="$emit('query')"
        >
          <el-option
            v-for="c in courseList"
            :key="c.id"
            :label="c.name || c.title"
            :value="c.id"
          />
        </el-select>
        <el-button type="primary" @click="$emit('query')"
          ><el-icon><Search /></el-icon>查询</el-button
        >
      </div>
    </div>

    <div v-loading="loading" class="table-area">
      <el-table :data="examList" style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"
            ><span class="title-cell">{{ row.title }}</span></template
          >
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '—' }}</template>
        </el-table-column>
        <el-table-column label="时长(分)" width="90" align="center">
          <template #default="{ row }">{{ row.duration ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="总分" width="80" align="center">
          <template #default="{ row }">{{ row.totalScore ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="题量" width="80" align="center">
          <template #default="{ row }">{{
            row.questionCount ?? (row.questions ? row.questions.length : 0)
          }}</template>
        </el-table-column>
        <el-table-column
          prop="courseName"
          label="所属课程"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getStatusType(row.status)" effect="light">{{
              getStatusText(row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-row">
              <el-button link type="primary" size="small" @click="$emit('questions', row)"
                >题目</el-button
              >
              <el-button link type="info" size="small" @click="$emit('stats', row)"
                >统计</el-button
              >
              <el-button
                v-if="row.status === 'DRAFT'"
                link
                type="primary"
                size="small"
                @click="$emit('edit', row)"
                >编辑</el-button
              >
              <el-button
                v-if="row.status === 'DRAFT'"
                link
                type="success"
                size="small"
                @click="$emit('publish', row)"
                >发布</el-button
              >
              <el-button
                v-if="row.status === 'DRAFT'"
                link
                type="danger"
                size="small"
                @click="$emit('delete', row)"
                >删除</el-button
              >
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && !examList.length" class="empty-wrap">
        <el-empty description="暂无试卷，点击右上角「新建试卷」开始创建">
          <template #image><div class="empty-deco">📝</div></template>
        </el-empty>
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
          @current-change="$emit('query')"
          @size-change="$emit('query')"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'

defineProps({
  loading: { type: Boolean, default: false },
  examList: { type: Array, default: () => [] },
  courseList: { type: Array, default: () => [] },
  filters: { type: Object, default: () => ({}) },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
})
defineEmits([
  'update:courseId',
  'query',
  'update:page',
  'update:pageSize',
  'questions',
  'stats',
  'edit',
  'publish',
  'delete',
])

function getStatusType(s) {
  const m = { DRAFT: 'info', PUBLISHED: 'success', ENDED: 'danger' }
  return m[s] || 'info'
}
function getStatusText(s) {
  const m = { DRAFT: '草稿', PUBLISHED: '已发布', ENDED: '已结束' }
  return m[s] || s || '草稿'
}
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
.table-area {
  min-height: 200px;
}
.title-cell {
  font-weight: 500;
  color: var(--ink);
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
