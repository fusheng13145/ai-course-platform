<template>
  <div class="tab-card">
    <el-table v-loading="studentsLoading" :data="students">
      <el-table-column prop="studentId" label="学号" width="120" />
      <el-table-column prop="studentName" label="姓名" min-width="110" />
      <el-table-column label="进度" width="140">
        <template #default="{ row }">
          <el-progress
            :percentage="Math.round((row.progress || 0) * 100)"
            :stroke-width="6"
            :show-text="false"
            class="prog-cell"
          />
          <span class="prog-text">{{ Math.round((row.progress || 0) * 100) }}%</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="96">
        <template #default="{ row }">
          <span
            class="status-dot"
            :class="
              row.approvalStatus === 'APPROVED' ? 'status-dot--active' : 'status-dot--inactive'
            "
            ><i />{{
              row.approvalStatus === 'APPROVED'
                ? '已通过'
                : row.approvalStatus === 'REJECTED'
                  ? '已拒绝'
                  : '待审核'
            }}</span
          >
        </template>
      </el-table-column>
      <el-table-column width="160" align="right">
        <template v-if="row.approvalStatus === 'PENDING'" #default="{ row }">
          <el-button text size="small" type="success" @click="emit('approve', row)">通过</el-button>
          <el-button text size="small" type="danger" @click="emit('reject', row)">拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!studentsLoading && !students.length" :description="emptyText" />
  </div>
</template>

<script setup lang="ts">
/**
 * 学生管理 Tab
 * <p>课程学生列表（学号/姓名/学习进度/选课状态），待审核学生可经事件上抛通过/拒绝。</p>
 */
defineProps({
  students: { type: Array, default: () => [] },
  studentsLoading: { type: Boolean, default: false },
  /** 空态文案（父页面按加载失败态透传诚实提示，默认保留原语义） */
  emptyText: { type: String, default: '暂无学生' },
})

const emit = defineEmits(['approve', 'reject'])
</script>

<style scoped>
.tab-card {
  padding: 8px 4px;
}
.prog-cell {
  width: 100px;
  vertical-align: middle;
}
.prog-text {
  font-size: 12px;
  color: var(--ink-2);
  margin-left: 6px;
}
.status-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  padding: 2px 10px;
  border-radius: 20px;
  line-height: 20px;
}
.status-dot i {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot--active {
  color: var(--ok);
  background: rgba(22, 163, 74, 0.08);
}
.status-dot--active i {
  background: var(--ok);
}
.status-dot--inactive {
  color: var(--ac-text-muted);
  background: var(--ac-bg-hover);
}
.status-dot--inactive i {
  background: var(--ink-3);
}
</style>
