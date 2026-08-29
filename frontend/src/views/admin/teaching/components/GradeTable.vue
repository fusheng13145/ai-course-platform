<template>
  <div class="grade-list-section">
    <div class="content-card">
      <el-table v-loading="loading" :data="gradeList" style="width: 100%">
        <el-table-column prop="studentName" label="学生姓名" width="110" />
        <el-table-column prop="studentId" label="学号" width="120" />
        <el-table-column prop="courseName" label="课程名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="examTitle" label="考试名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="score" label="成绩" width="80" align="center">
          <template #default="{ row }">
            <span class="score-badge" :class="getScoreClass(row.score)">{{
              row.score ?? '--'
            }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="rank" label="排名" width="70" align="center" />
        <el-table-column prop="submitTime" label="提交时间" width="160" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isAbnormal" type="danger" size="small" effect="light">异常</el-tag>
            <span v-else class="text-muted">正常</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="emit('view', row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无成绩数据，请调整筛选条件" :image-size="80" />
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="emit('search')"
          @current-change="emit('search')"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 成绩管理列表（表格 + 分页）
 * <p>展示学生成绩明细（含分数段徽章、异常标记），分页变化经 search 事件上抛重新取数，
 * 点击详情经 view 事件打开详情对话框。</p>
 */
defineProps({
  /** 成绩行数据 */
  gradeList: { type: Array, default: () => [] },
  /** 加载态 */
  loading: { type: Boolean, default: false },
  /** 分页对象（page/size/total） */
  pagination: { type: Object, required: true },
  /** 分数段样式函数 */
  getScoreClass: { type: Function, required: true },
})

const emit = defineEmits(['search', 'view'])
</script>

<style scoped>
.grade-list-section {
  min-width: 0;
}
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

/* 成绩徽章（与详情对话框同套样式） */
.score-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 6px;
  font-weight: 600;
  font-size: 13px;
}
.score-badge.excellent {
  background: var(--ok-soft);
  color: var(--ok);
}
.score-badge.good {
  background: var(--info-soft);
  color: var(--info);
}
.score-badge.medium {
  background: var(--violet-soft);
  color: var(--violet);
}
.score-badge.pass {
  background: var(--warn-soft);
  color: var(--warn);
}
.score-badge.fail {
  background: var(--accent-soft);
  color: var(--accent);
}

.text-muted {
  color: var(--ac-text-muted);
  font-size: 13px;
}
</style>
