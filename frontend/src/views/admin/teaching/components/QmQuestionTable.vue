<template>
  <div class="content-card">
    <el-table v-loading="loading" :data="questionList" style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />

      <el-table-column label="题型" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="light" :type="getTypeTagType(row.type)">
            {{ getTypeText(row.type) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="content" label="题目内容" min-width="300" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="question-content">{{ row.content }}</span>
        </template>
      </el-table-column>

      <el-table-column label="难度" width="110" align="center">
        <template #default="{ row }">
          <el-rate
            :model-value="getDifficultyValue(row.difficulty)"
            disabled
            size="small"
            :colors="['#8BA888', '#CBB58A', '#C48080']"
            show-text
            :texts="['简单', '中等', '困难']"
          />
        </template>
      </el-table-column>

      <el-table-column prop="courseName" label="所属课程" width="140" show-overflow-tooltip />

      <el-table-column label="使用次数" width="90" align="center">
        <template #default="{ row }">
          <span class="usage-count">{{ row.usageCount || 0 }}次</span>
        </template>
      </el-table-column>

      <el-table-column label="正确率" width="90" align="center">
        <template #default="{ row }">
          <span class="accuracy" :class="getAccuracyClass(row.correctRate)">{{
            row.correctRate ? (row.correctRate * 100).toFixed(0) + '%' : '--'
          }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="emit('edit', row)"
            >编辑</el-button
          >
          <el-button text type="warning" size="small" @click="emit('stats', row)">统计</el-button>
          <el-button text type="danger" size="small" @click="emit('delete', row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
      <!-- 空状态 -->
      <template #empty>
        <el-empty description="暂无题目数据">
          <template #image><div class="empty-deco">📚</div></template>
        </el-empty>
      </template>
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
        @update:current-page="emit('update:page', $event)"
        @update:page-size="emit('update:size', $event)"
        @size-change="emit('search')"
        @current-change="emit('search')"
      />
    </div>
  </div>
</template>

<script setup>
/**
 * 题库列表表格（数据列 + 操作 + 分页）
 * <p>题型/内容/难度/课程/使用次数/正确率列 + 编辑/统计/删除操作 + 分页。
 * 展示辅助函数经 props 注入，操作经事件上抛父组件（useQuestions）。</p>
 */
defineProps({
  questionList: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
  total: { type: Number, default: 0 },
  getTypeTagType: { type: Function, required: true },
  getTypeText: { type: Function, required: true },
  getDifficultyValue: { type: Function, required: true },
  getAccuracyClass: { type: Function, required: true },
})

const emit = defineEmits(['edit', 'stats', 'delete', 'update:page', 'update:size', 'search'])
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

.question-content {
  font-size: 13px;
  color: var(--ac-text-secondary);
}

.usage-count {
  color: var(--ac-text-secondary);
  font-weight: 500;
}

.accuracy {
  font-weight: 600;
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 4px;
}

.accuracy.high {
  color: var(--ac-success);
  background: rgba(139, 168, 136, 0.12);
}
.accuracy.medium {
  color: var(--ac-warning);
  background: rgba(203, 181, 138, 0.12);
}
.accuracy.low {
  color: var(--ac-danger);
  background: rgba(196, 128, 128, 0.12);
}

.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
</style>
