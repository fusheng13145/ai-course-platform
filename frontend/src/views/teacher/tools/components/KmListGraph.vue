<template>
  <div class="right-panel">
    <!-- 知识点列表表格 -->
    <div class="content-card list-card">
      <el-table v-loading="tableLoading" :data="filteredKnowledgeList">
        <el-table-column prop="name" label="知识点名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="difficulty" label="难度" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="getDifficultyTagType(row.difficulty)" size="small">{{
              getDifficultyLabel(row.difficulty)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="importance" label="重要度" width="80" align="center">
          <template #default="{ row }">
            <el-rate v-model="row.importance" disabled :max="3" size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="questionCount" label="关联题目" width="90" align="center">
          <template #default="{ row }"><span>{{ row.questionCount || 0 }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="emit('edit', row)">编辑</el-button>
            <el-button text type="primary" size="small" @click="emit('relation', row)"
              >关联</el-button
            >
            <el-button text type="danger" size="small" @click="emit('delete', row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!tableLoading && filteredKnowledgeList.length === 0"
        description="暂无知识点数据"
      />
    </div>

    <!-- 知识图谱可视化 -->
    <div class="graph-card">
      <div class="panel-header">
        <span class="chart-title">知识图谱</span>
        <el-radio-group :model-value="graphViewMode" size="small" @change="emit('view-mode', $event)">
          <el-radio-button value="force">力导向</el-radio-button>
          <el-radio-button value="tree">树形</el-radio-button>
        </el-radio-group>
      </div>
      <div class="graph-container">
        <v-chart
          v-if="graphViewMode === 'force'"
          :option="forceGraphOption"
          class="graph-chart"
          autoresize
        />
        <v-chart v-else :option="treeGraphOption" class="graph-chart" autoresize />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 知识管理右侧：知识点列表 + 知识图谱
 * <p>列表展示知识点（难度/重要度/关联题数）并提供编辑/关联/删除操作；
 * 图谱支持力导向与树形两种视图切换。</p>
 */
defineProps({
  filteredKnowledgeList: { type: Array, default: () => [] },
  tableLoading: { type: Boolean, default: false },
  getDifficultyTagType: { type: Function, required: true },
  getDifficultyLabel: { type: Function, required: true },
  graphViewMode: { type: String, default: 'force' },
  forceGraphOption: { type: Object, required: true },
  treeGraphOption: { type: Object, required: true },
})

const emit = defineEmits(['edit', 'relation', 'delete', 'view-mode'])
</script>

<style scoped>
.right-panel {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-md);
  min-width: 0;
}
.list-card {
  flex-shrink: 0;
}
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
}
.graph-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  flex: 1;
  min-height: 380px;
}
.graph-container {
  margin-top: var(--ac-spacing-sm);
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--ac-spacing-md) var(--ac-spacing-lg);
  border-bottom: 1px solid var(--ac-border);
  margin-bottom: var(--ac-spacing-sm);
}
.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  letter-spacing: -0.2px;
}
.graph-chart {
  width: 100%;
  height: 340px;
}
</style>
