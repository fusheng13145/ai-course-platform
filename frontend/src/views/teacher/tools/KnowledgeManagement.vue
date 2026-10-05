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
        <el-button :disabled="!selectedCourseId" @click="openImport">
          <el-icon><Upload /></el-icon>批量导入
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
      <KntTreePanel
        ref="treeRef"
        :tree-data="knowledgeTree"
        :tree-loading="treeLoading"
        :get-difficulty-tag-type="getDifficultyTagType"
        :get-difficulty-label="getDifficultyLabel"
        empty-text="请先选择课程加载知识点"
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

    <!-- 批量导入知识点（Excel） -->
    <ExcelImportDialog
      v-if="showImport"
      title="批量导入知识点"
      sub="上传 .xlsx 一次性建立知识点结构；父知识点填名称即可形成层级"
      :columns="IMPORT_COLUMNS"
      :notes="IMPORT_NOTES"
      :importing="importing"
      :result="importResult"
      @close="showImport = false"
      @submit="handleImport"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Plus, Search, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useKnowledgeManagement } from '@/composables/useKnowledgeManagement'
import knowledgeApi from '@/api/knowledge'
import { unwrap } from '@/utils/response'
import PageHeader from '@/components/PageHeader.vue'
import KntTreePanel from '@/components/knowledge/KntTreePanel.vue'
import KmListGraph from './components/KmListGraph.vue'
import KmDialogs from './components/KmDialogs.vue'
import ExcelImportDialog from '@/components/ui/ExcelImportDialog.vue'

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

/* ---------------- 批量导入知识点（Excel，2026-09-23 新增） ---------------- */

const showImport = ref(false)
const importing = ref(false)
const importResult = ref(null)

/** 与后端 TeacherImportResultVO 对齐 */
interface ImportResultPayload {
  importedCount?: number
  skippedCount?: number
  failedCount?: number
  total?: number
  errors?: string[]
}

const IMPORT_COLUMNS = [
  { name: '知识点名称*', required: true },
  { name: '父知识点名称' },
  { name: '难度' },
  { name: '重要程度(1-10)' },
  { name: '预计学习时长(分钟)' },
  { name: '类型' },
  { name: '描述' },
]
const IMPORT_NOTES = [
  '仅「知识点名称」必填，为空的行会被跳过',
  '父知识点名称填写已有知识点名即可建立层级（可留空表示顶级）',
  '难度填 EASY / MEDIUM / HARD；重要程度 1-10，留空默认 5',
  '类型填 concept（概念）/ skill（技能）/ principle（原理）',
  '同一课程下名称重复的行会被跳过',
]

function openImport() {
  if (!selectedCourseId.value) {
    ElMessage.warning('请先选择课程')
    return
  }
  importResult.value = null
  showImport.value = true
}

async function handleImport(file) {
  if (!file) return
  importing.value = true
  try {
    const res = await knowledgeApi.importKnowledgePointsExcel(selectedCourseId.value, file)
    const payload = (unwrap(res) || {}) as ImportResultPayload
    importResult.value = payload
    if (payload.importedCount > 0) {
      ElMessage.success(`已导入 ${payload.importedCount} 个知识点`)
      loadAllData()
    } else {
      ElMessage.warning('没有知识点被导入，请检查文件内容')
    }
  } catch (e) {
    ElMessage.error('导入失败，请检查文件格式（.xlsx，7 列表头）')
  } finally {
    importing.value = false
  }
}

/** 展开树：经子组件暴露的 expandAll 方法 */
function onExpandAll() {
  treeRef.value?.expandAll()
}

/** 树节点点击：保持原高亮语义（无额外行为） */
function onNodeClick() {}

/** 课程上下文：驾驶舱子路由（/teacher/courses/:id/knowledge-management）或统一路由 ?courseId 预选 */
const route = useRoute()
onMounted(async () => {
  const cid = route.params.id || route.query.courseId
  if (cid) selectedCourseId.value = Number(cid)
  await loadCourseOptions()
  if (cid) await loadAllData()
})
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
