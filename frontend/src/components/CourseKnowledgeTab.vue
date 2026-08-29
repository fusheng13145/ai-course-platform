<template>
  <div class="knowledge-tab">
    <!-- 工具栏 -->
    <KntToolbar
      :search-text="searchText"
      :export-disabled="!list.length"
      @update:search-text="searchText = $event"
      @create="openCreateDialog"
      @ai="openAIDialog"
      @export="handleExport"
    />

    <!-- 主区域：知识树 + 列表/图谱 -->
    <div class="km-grid">
      <KntTreePanel :tree-data="treeData" :tree-loading="treeLoading" />
      <KntListGraph
        v-model:graph-mode="graphMode"
        :filtered-data="filteredData"
        :table-loading="tableLoading"
        :force-option="forceOption"
        :tree-option="treeOption"
        @edit="openEditDialog"
        @delete="handleDelete"
      />
    </div>

    <!-- 新建/编辑对话框 -->
    <KntEditDialog
      v-model="formVisible"
      :is-edit="isEdit"
      :form="form"
      :rules="rules"
      :submitting="submitting"
      :flat-tree-options="flatTreeOptions"
      @submit="savePoint"
    />

    <!-- AI提取对话框 -->
    <KntAiDialog
      v-model="aiVisible"
      v-model:ai-text="aiText"
      :ai-extracting="aiExtracting"
      @extract="doAIExtract"
    />
  </div>
</template>

<script setup>
import { toRef } from 'vue'
import KntToolbar from './knowledge/KntToolbar.vue'
import KntTreePanel from './knowledge/KntTreePanel.vue'
import KntListGraph from './knowledge/KntListGraph.vue'
import KntEditDialog from './knowledge/KntEditDialog.vue'
import KntAiDialog from './knowledge/KntAiDialog.vue'
import { useKnowledgeTab } from '@/composables/useKnowledgeTab'

const props = defineProps({ courseId: { type: [Number, String], required: true } })
const courseIdRef = toRef(props, 'courseId')

const {
  searchText,
  treeLoading,
  tableLoading,
  treeData,
  list,
  formVisible,
  isEdit,
  submitting,
  form,
  rules,
  aiVisible,
  aiText,
  aiExtracting,
  graphMode,
  filteredData,
  flatTreeOptions,
  forceOption,
  treeOption,
  openCreateDialog,
  openEditDialog,
  savePoint,
  handleDelete,
  openAIDialog,
  doAIExtract,
  handleExport,
} = useKnowledgeTab(courseIdRef)
</script>

<style scoped>
.knowledge-tab {
  padding: 4px 0;
}
.km-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 14px;
  min-height: 420px;
}
@media (max-width: 1024px) {
  .km-grid {
    grid-template-columns: 1fr;
  }
}
</style>
