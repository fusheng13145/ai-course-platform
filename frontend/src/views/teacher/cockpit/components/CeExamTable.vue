<template>
  <div class="l1-card">
    <div class="l1-head">
      <div class="l1-title">
        课程试卷
        <el-tag size="small" type="info" effect="light" round>{{ total }}</el-tag>
      </div>
    </div>
    <div class="l1-body">
      <el-table v-if="list.length" :data="list" size="small" @row-dblclick="emit('edit', $event)">
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="时长" width="90">
          <template #default="{ row }">{{ row.duration }} 分钟</template>
        </el-table-column>
        <el-table-column label="总分" width="80">
          <template #default="{ row }">{{ row.totalScore }}</template>
        </el-table-column>
        <el-table-column label="题量" width="72">
          <template #default="{ row }">{{ row.questionCount ?? '--' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.status === '已发布' ? 'success' : 'info'"
              effect="light"
            >
              {{ row.status === '已发布' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== '已发布'"
              link
              type="success"
              :icon="Promotion"
              @click="emit('publish', row)"
              >发布</el-button
            >
            <el-button link type="primary" :icon="View" @click="emit('view-questions', row)"
              >查看题目</el-button
            >
            <el-button link type="info" :icon="DataAnalysis" @click="emit('view-stats', row)"
              >统计</el-button
            >
            <el-button link type="warning" :icon="CopyDocument" @click="emit('copy', row)"
              >复制</el-button
            >
            <el-button link type="primary" :icon="Edit" @click="emit('edit', row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="emit('remove', row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else :description="emptyText" :image-size="72" />

      <div v-if="list.length" class="pager">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="emit('page-change', $event)"
          @size-change="emit('size-change', $event)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Promotion, View, DataAnalysis, CopyDocument, Edit, Delete } from '@element-plus/icons-vue'

/**
 * 课程试卷 — 试卷列表表格（含分页与行操作）。
 * <p>拆分子组件：纯展示 + 行操作 emit；数据/分页状态由页面壳持有。</p>
 */
defineProps({
  list: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
  /** 列表空态文案（失败时由页面传入「未能加载」文案，默认保留原语义） */
  emptyText: { type: String, default: '暂无试卷，点击右上角新建' },
})

const emit = defineEmits([
  'publish',
  'view-questions',
  'view-stats',
  'copy',
  'edit',
  'remove',
  'page-change',
  'size-change',
])
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--ac-spacing-md);
}
</style>
