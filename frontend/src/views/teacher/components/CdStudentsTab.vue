<template>
  <div class="tab-card">
    <el-table v-loading="studentsLoading" :data="paged">
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
        <!-- 审批按钮按行数据显示：条件必须在槽位作用域内求值（原 v-if 写在 template 上，
             row 解析到组件级 undefined——既类型错也拿不到真实行的审批状态，FE-10 B3） -->
        <template #default="{ row }">
          <template v-if="row.approvalStatus === 'PENDING'">
            <el-button text size="small" type="success" @click="emit('approve', row)">通过</el-button>
            <el-button text size="small" type="danger" @click="emit('reject', row)">拒绝</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <!-- D2-5：大课程（>20 人）客户端分页，仅超一页时出现 -->
    <div v-if="showPager" class="pager-wrap">
      <el-pagination
        small
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>
    <EmptyState v-if="!studentsLoading && !students.length" :title="emptyText" icon="users" />
  </div>
</template>

<script setup lang="ts">
import EmptyState from '@/components/ui/EmptyState.vue'
import { useClientPagination } from '@/composables/useClientPagination'

/**
 * 学生管理 Tab
 * <p>课程学生列表（学号/姓名/学习进度/选课状态），待审核学生可经事件上抛通过/拒绝。</p>
 * <p>D2-5：课程学生无界（大课程可达数百人），渲染层客户端分页（每页 20），
 * 数据抓取保持全量不变。</p>
 */
const props = defineProps({
  students: { type: Array, default: () => [] },
  studentsLoading: { type: Boolean, default: false },
  /** 空态文案（父页面按加载失败态透传诚实提示，默认保留原语义） */
  emptyText: { type: String, default: '暂无学生' },
})

const emit = defineEmits(['approve', 'reject'])

// D2-5：大列表客户端分页（小列表全量渲染、视觉零变化；越界页自动收敛）
const { page, pageSize, total, paged, showPager, onPageChange } = useClientPagination(
  () => props.students,
)
</script>

<style scoped>
.tab-card {
  padding: 8px 4px;
}
/* D2-5：分页器右对齐 */
.pager-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 12px 4px 4px;
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
