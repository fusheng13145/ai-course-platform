<template>
  <!-- 右侧：列表 + 图谱 -->
  <div class="right-area">
    <!-- 知识点列表 -->
    <div class="list-card">
      <el-table
        v-loading="tableLoading"
        :data="filteredData"
        style="width: 100%"
        :header-cell-style="{
          background: 'var(--ac-bg)',
          color: 'var(--ac-text-secondary)',
          fontWeight: 600,
        }"
      >
        <el-table-column prop="name" label="知识点名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="difficulty" label="难度" width="75" align="center">
          <template #default="{ row }"
            ><el-tag :type="getDiffTag(row.difficulty)" size="small">{{
              getDiffLabel(row.difficulty)
            }}</el-tag></template
          >
        </el-table-column>
        <el-table-column prop="importance" label="重要度" width="80" align="center">
          <template #default="{ row }"
            ><el-rate v-model="row.importance" disabled :max="3" size="small"
          /></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-row">
              <el-button text type="primary" size="small" @click="$emit('edit', row)"
                >编辑</el-button
              >
              <el-button text type="danger" size="small" @click="$emit('delete', row)"
                >删除</el-button
              >
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!tableLoading && !filteredData.length" :description="emptyText" />
    </div>

    <!-- 知识图谱 -->
    <div class="graph-card">
      <div class="panel-header">
        <span class="panel-title">知识图谱</span>
        <el-radio-group
          :model-value="graphMode"
          size="small"
          @update:model-value="$emit('update:graphMode', $event)"
        >
          <el-radio-button value="force">力导向</el-radio-button>
          <el-radio-button value="tree">树形</el-radio-button>
        </el-radio-group>
      </div>
      <v-chart
        v-if="graphMode === 'force'"
        :option="forceOption"
        style="width: 100%; height: 300px"
        autoresize
      />
      <v-chart v-else :option="treeOption" style="width: 100%; height: 300px" autoresize />
    </div>
  </div>
</template>

<script setup>
defineProps({
  filteredData: { type: Array, default: () => [] },
  tableLoading: { type: Boolean, default: false },
  graphMode: { type: String, default: 'force' },
  forceOption: { type: Object, default: () => ({}) },
  treeOption: { type: Object, default: () => ({}) },
  /** 列表空态文案（失败时由页面传入「未能加载」文案，默认保留原语义） */
  emptyText: { type: String, default: '暂无知识点' },
})
defineEmits(['update:graphMode', 'edit', 'delete'])

function getDiffTag(d) {
  return { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }[d] || 'info'
}
function getDiffLabel(d) {
  return { EASY: '简单', MEDIUM: '中等', HARD: '困难' }[d] || '-'
}
</script>

<style scoped>
.right-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.list-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  overflow: hidden;
  flex-shrink: 0;
}
.graph-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 12px 14px;
  flex: 1;
  min-height: 340px;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 0 10px;
  border-bottom: 1px solid var(--ac-border);
  margin-bottom: 10px;
}
.panel-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}
.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  flex-wrap: nowrap;
}
</style>
