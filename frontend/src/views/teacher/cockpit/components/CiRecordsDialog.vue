<template>
  <!-- 签到记录 -->
  <Modal
   
    :model-value="modelValue"
    title="签到记录"
    width="520px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-table :data="pagedRecords" size="small" border max-height="420" empty-text="暂无记录">
      <el-table-column prop="studentName" label="学生" min-width="120" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.status === 'SIGNED' ? 'success' : 'info'"
            effect="light"
            >{{ row.status === 'SIGNED' ? '已签到' : row.status || '未签' }}</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="时间" min-width="150">
        <template #default="{ row }">{{ row.time || '—' }}</template>
      </el-table-column>
      <el-table-column label="标记" width="100" align="center">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :icon="Check"
            :disabled="row.status === 'SIGNED'"
            @click="$emit('mark', row)"
            >标记到</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <!-- D2-5：签到记录无界（大课程数百人），客户端分页仅超一页时出现 -->
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
  </Modal>
</template>

<script setup lang="ts">
import { Check } from '@element-plus/icons-vue'
import { useClientPagination } from '@/composables/useClientPagination'
import Modal from '@/components/ui/Modal.vue'

/**
 * 签到记录对话框（D2-5：记录数无界，渲染层客户端分页，每页 20；
 * 仅超一页时出现分页器，数据收缩自动收敛页码）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  records: { type: Array, default: () => [] },
})

// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
defineEmits(['update:modelValue', 'mark'])

const {
  page,
  pageSize,
  total,
  paged: pagedRecords,
  showPager,
  onPageChange,
} = useClientPagination(() => props.records)
</script>

<style scoped>
.pager-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 10px;
}
</style>
