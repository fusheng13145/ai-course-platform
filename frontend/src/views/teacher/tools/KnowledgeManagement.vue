<template>
  <div class="fade-in">
    <PageHeader title="知识管理" subtitle="维护课程知识点结构、关联关系与知识图谱" icon="brain" />
    <!-- 顶部工具栏 -->
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-select
          v-model="selectedCourseId"
          placeholder="选择课程"
          clearable
          class="w-200"
          @change="loadAllData"
        >
          <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>新建知识点
        </el-button>
      </div>
      <div class="toolbar-right">
        <el-input
          v-model="searchText"
          placeholder="搜索知识点..."
          clearable
          class="w-220"
          :prefix-icon="Search"
          @keyup.enter="loadAllData"
        />
      </div>
    </div>

    <!-- 主内容区：知识树 + 知识点列表/图谱 -->
    <div class="km-main-grid animate-in d1">
      <KmTreePanel
        ref="treeRef"
        :knowledge-tree="knowledgeTree"
        :tree-loading="treeLoading"
        :get-difficulty-tag-type="getDifficultyTagType"
        :get-difficulty-label="getDifficultyLabel"
        @expand="onExpandAll"
        @node-click="onNodeClick"
      />
      <KmListGraph
        :filtered-knowledge-list="filteredKnowledgeList"
        :table-loading="tableLoading"
        :get-difficulty-tag-type="getDifficultyTagType"
        :get-difficulty-label="getDifficultyLabel"
        :graph-view-mode="graphViewMode"
        :force-graph-option="forceGraphOption"
        :tree-graph-option="treeGraphOption"
        @edit="openEditDialog"
        @relation="openRelationDialog"
        @delete="handleDelete"
        @view-mode="graphViewMode = $event"
      />
    </div>

    <!-- 新建/编辑 + 关联关系对话框 -->
    <KmDialogs
      ref="formRef"
      :form-dialog-visible="formDialogVisible"
      :is-edit="isEdit"
      :submitting="submitting"
      :kp-form="kpForm"
      :form-rules="formRules"
      :course-list="courseList"
      :flat-tree-options="flatTreeOptions"
      :relation-visible="relationDialogVisible"
      :current-kp-for-relation="currentKpForRelation"
      :new-prereq-id="newPrereqId"
      :available-prereqs="availablePrereqs"
      :prerequisites-list="prerequisitesList"
      @update:form-visible="formDialogVisible = $event"
      @update:relation-visible="relationDialogVisible = $event"
      @update:new-prereq-id="newPrereqId = $event"
      @submit="submitForm"
      @add-prereq="addPrerequisite"
      @remove-prereq="removePrerequisite"
    />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { useKnowledgeManagement } from '@/composables/useKnowledgeManagement'
import PageHeader from '@/components/PageHeader.vue'
import KmTreePanel from './components/KmTreePanel.vue'
import KmListGraph from './components/KmListGraph.vue'
import KmDialogs from './components/KmDialogs.vue'

/**
 * 知识管理页面（教师）
 * <p>页面壳：工具栏（课程选择/搜索/新建）+ 左侧知识树 + 右侧列表与图谱
 * + 新建编辑/关联关系对话框；全部数据与业务逻辑由 useKnowledgeManagement 承载。</p>
 */
const {
  selectedCourseId,
  courseList,
  searchText,
  treeLoading,
  knowledgeTree,
  treeRef,
  tableLoading,
  formDialogVisible,
  isEdit,
  submitting,
  formRef,
  kpForm,
  formRules,
  relationDialogVisible,
  currentKpForRelation,
  newPrereqId,
  prerequisitesList,
  graphViewMode,
  filteredKnowledgeList,
  flatTreeOptions,
  availablePrereqs,
  forceGraphOption,
  treeGraphOption,
  loadCourseOptions,
  loadAllData,
  openCreateDialog,
  openEditDialog,
  submitForm,
  handleDelete,
  openRelationDialog,
  addPrerequisite,
  removePrerequisite,
  getDifficultyTagType,
  getDifficultyLabel,
} = useKnowledgeManagement()

/** 展开树：经子组件暴露的 expandAll 方法 */
function onExpandAll() {
  treeRef.value?.expandAll()
}

/** 树节点点击：保持原高亮语义（无额外行为） */
function onNodeClick() {}

onMounted(loadCourseOptions)
</script>

<style scoped>
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ac-spacing-md);
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.w-200 {
  width: 200px;
}
.w-220 {
  width: 220px;
}
.km-main-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: var(--ac-spacing-md);
  min-height: calc(100vh - 200px);
}

@media (max-width: 1024px) {
  .km-main-grid {
    grid-template-columns: 1fr;
  }
  .tree-panel {
    max-height: 300px;
  }
}
</style>
