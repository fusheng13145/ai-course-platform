<template>
  <div class="tab-card">
    <div class="tab-toolbar">
      <el-button type="primary" plain @click="emit('add')"><Plus :size="14" /> 添加章节</el-button>
      <span v-if="dragChanged" class="drag-hint">排序已变更，请保存</span>
    </div>
    <el-table v-loading="chaptersLoading" :data="chapters" row-key="id">
      <el-table-column width="46" align="center"
        ><template #default><Rank class="drag-handle" /></template
      ></el-table-column>
      <el-table-column prop="title" label="章节名称" min-width="200" />
      <el-table-column prop="duration" label="时长(分)" width="90"
        ><template #default="{ row }">{{ row.duration || 0 }}</template></el-table-column
      >
      <el-table-column prop="isPublished" label="状态" width="90">
        <template #default="{ row }">
          <span class="status-dot" :class="row.isPublished ? 'status-dot--active' : 'status-dot--inactive'"
            ><i />{{ row.isPublished ? '已发布' : '草稿' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column width="200" align="right">
        <template #default="{ row }">
          <el-button text size="small" @click="emit('edit', row)">编辑</el-button>
          <el-button text size="small" type="danger" @click="emit('delete', row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="dragChanged" class="order-actions">
      <el-button type="warning" size="small" :loading="savingOrder" @click="emit('save-order')">保存排序</el-button>
      <el-button size="small" @click="emit('reset-order')">取消</el-button>
    </div>
    <el-empty v-if="!chaptersLoading && !chapters.length" description="暂无章节，点击上方添加" />
  </div>
</template>

<script setup>
import { Plus, Rank } from '@element-plus/icons-vue'

/**
 * 章节管理 Tab
 * <p>章节列表（拖拽手柄示意/时长/发布状态）+ 添加/编辑/删除 + 排序变更保存/取消。</p>
 */
defineProps({
  chapters: { type: Array, default: () => [] },
  chaptersLoading: { type: Boolean, default: false },
  dragChanged: { type: Boolean, default: false },
  savingOrder: { type: Boolean, default: false },
})

const emit = defineEmits(['add', 'edit', 'delete', 'save-order', 'reset-order'])
</script>

<style scoped>
.tab-card {
  padding: 8px 4px;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.drag-hint {
  font-size: 12.5px;
  color: var(--warn);
  font-weight: 500;
}
.order-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  justify-content: flex-end;
}
.drag-handle {
  cursor: grab;
  color: var(--ink-3);
}
.drag-handle:active {
  cursor: grabbing;
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
  color: #15803d;
  background: rgba(22, 163, 74, 0.08);
}
.status-dot--active i {
  background: #22c55e;
}
.status-dot--inactive {
  color: var(--ac-text-muted);
  background: var(--ac-bg-hover);
}
.status-dot--inactive i {
  background: #9ca3af;
}
</style>
