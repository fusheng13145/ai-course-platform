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
          @clear="handleFilter"
          @keyup.enter="handleFilter"
        />
      </div>
    </div>

    <!-- 主内容区：知识树 + 知识点列表 -->
    <div class="km-main-grid animate-in d1">
      <!-- 左侧：知识树 -->
      <div class="tree-panel">
        <div class="panel-header">
          <span class="chart-title">知识结构</span>
          <el-button text size="small" type="primary" @click="expandAll">展开全部</el-button>
        </div>
        <div v-loading="treeLoading" class="tree-container">
          <el-tree
            ref="treeRef"
            :data="knowledgeTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            empty-text="请先选择课程加载知识点"
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <span class="tree-node-label">
                <span class="node-name">{{ node.label }}</span>
                <el-tag
                  size="small"
                  :type="getDifficultyTagType(data.difficulty)"
                  class="tree-tag"
                  >{{ getDifficultyLabel(data.difficulty) }}</el-tag
                >
              </span>
            </template>
          </el-tree>
          <el-empty
            v-if="!treeLoading && (!knowledgeTree || knowledgeTree.length === 0)"
            description="暂无知识树数据"
            :image-size="60"
          />
        </div>
      </div>

      <!-- 右侧：知识点列表 + 图谱 -->
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
              <template #default="{ row }"
                ><span>{{ row.questionCount || 0 }}</span></template
              >
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openEditDialog(row)"
                  >编辑</el-button
                >
                <el-button text type="primary" size="small" @click="openRelationDialog(row)"
                  >关联</el-button
                >
                <el-button text type="danger" size="small" @click="handleDelete(row)"
                  >删除</el-button
                >
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
            <el-radio-group v-model="graphViewMode" size="small">
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
    </div>

    <!-- 新建/编辑知识点对话框 -->
    <el-dialog
      v-model="formDialogVisible"
      :title="isEdit ? '编辑知识点' : '新建知识点'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="kpForm" :rules="formRules" label-width="100px">
        <el-form-item label="知识点名称" prop="name">
          <el-input
            v-model="kpForm.name"
            placeholder="输入知识点名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="所属课程" prop="courseId">
          <el-select v-model="kpForm.courseId" placeholder="选择课程" class="w-100" filterable>
            <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="父级知识点">
          <el-tree-select
            v-model="kpForm.parentId"
            :data="flatTreeOptions"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            check-strictly
            clearable
            placeholder="选择父级（可选）"
            class="w-100"
            filterable
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="难度" prop="difficulty">
              <el-select v-model="kpForm.difficulty" class="w-100">
                <el-option label="简单" value="EASY" />
                <el-option label="中等" value="MEDIUM" />
                <el-option label="困难" value="HARD" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重要度" prop="importance">
              <el-rate v-model="kpForm.importance" :max="3" show-text :texts="['低', '中', '高']" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input
            v-model="kpForm.description"
            type="textarea"
            :rows="3"
            placeholder="知识点描述（可选）"
            maxlength="300"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{
          isEdit ? '保存修改' : '创建'
        }}</el-button>
      </template>
    </el-dialog>

    <!-- 关联关系对话框 -->
    <el-dialog v-model="relationDialogVisible" title="管理关联关系" width="520px">
      <div v-if="currentKpForRelation">
        <p class="rel-desc">
          当前知识点：<strong>{{ currentKpForRelation.name }}</strong>
        </p>

        <!-- 添加前置依赖 -->
        <div class="rel-section">
          <h4 class="rel-heading">添加前置依赖</h4>
          <div class="rel-row">
            <el-select
              v-model="newPrereqId"
              placeholder="选择前置知识点"
              clearable
              filterable
              class="rel-select"
            >
              <el-option v-for="k in availablePrereqs" :key="k.id" :label="k.name" :value="k.id" />
            </el-select>
            <el-button type="primary" size="small" :disabled="!newPrereqId" @click="addPrerequisite"
              >添加</el-button
            >
          </div>
        </div>

        <!-- 已有前置依赖列表 -->
        <div>
          <h4 class="rel-heading">已有前置依赖</h4>
          <div v-if="prerequisitesList.length > 0" class="relation-list">
            <div v-for="p in prerequisitesList" :key="p.id" class="relation-item">
              <span class="relation-arrow">←</span>
              <span class="relation-name">{{ p.name }}</span>
              <el-button text type="danger" size="small" @click="removePrerequisite(p.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <el-empty v-else description="暂无前置依赖" :image-size="50" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Delete } from '@element-plus/icons-vue'
import knowledgeApi from '@/api/knowledge'
import courseApi from '@/api/course'
import knowledgeRelationApi from '@/api/knowledgePointRelation'
import PageHeader from '@/components/PageHeader.vue'
import { getChartTheme, getMonoStatus } from '@/utils/chartTheme'

// 课程选择
const selectedCourseId = ref(null)
const courseList = ref([])
const searchText = ref('')

// 知识树
const treeLoading = ref(false)
const knowledgeTree = ref([])
const treeRef = ref(null)

// 知识点列表
const tableLoading = ref(false)
const knowledgeList = ref([])

// 表单对话框
const formDialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)

const kpForm = reactive({
  name: '',
  courseId: null,
  parentId: null,
  difficulty: 'MEDIUM',
  importance: 2,
  description: '',
})

const formRules = {
  name: [{ required: true, message: '请输入知识点名称', trigger: 'blur' }],
  courseId: [{ required: true, message: '请选择所属课程', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
}

// 关联关系
const relationDialogVisible = ref(false)
const currentKpForRelation = ref(null)
const newPrereqId = ref(null)
const prerequisitesList = ref([])

// 图谱视图模式
const graphViewMode = ref('force')

// 过滤后的知识点列表
const filteredKnowledgeList = computed(() => {
  if (!searchText.value) return knowledgeList.value
  const kw = searchText.value.toLowerCase()
  return knowledgeList.value.filter(
    (k) =>
      (k.name || '').toLowerCase().includes(kw) || (k.description || '').toLowerCase().includes(kw),
  )
})

// 树形下拉选项（扁平化）
const flatTreeOptions = computed(() => {
  // 将树形数据转为 el-tree-select 需要的格式
  return buildTreeSelectData(knowledgeTree.value)
})

// 可选的前置知识点（排除自身和已有的）
const availablePrereqs = computed(() => {
  if (!currentKpForRelation.value) return []
  const existingIds = new Set(prerequisitesList.value.map((p) => p.id))
  existingIds.add(currentKpForRelation.value.id)
  return knowledgeList.value.filter((k) => !existingIds.has(k.id))
})

onMounted(async () => {
  await loadCourseOptions()
})

// 加载课程选项
async function loadCourseOptions() {
  try {
    const res = await courseApi.getMyCourses()
    const data = res.data?.list || res.data || res || []
    courseList.value = Array.isArray(data) ? data : []
    if (courseList.value.length > 0 && !selectedCourseId.value) {
      selectedCourseId.value = courseList.value[0].id
      await loadAllData()
    }
  } catch (e) {
    console.error('加载课程失败:', e)
  }
}

// 加载所有数据
async function loadAllData() {
  if (!selectedCourseId.value) return
  await Promise.all([loadKnowledgeTree(), loadKnowledgeList(), loadKnowledgeGraph()])
}

// 加载知识树（增强：优先使用 getTree API）
async function loadKnowledgeTree() {
  treeLoading.value = true
  try {
    const res = await knowledgeApi.getTree(selectedCourseId.value)
    const data = res.data?.data || res.data || res || []
    knowledgeTree.value = Array.isArray(data) ? data : data ? [data] : []
  } catch (e) {
    // getTree 接口不可用时回退到从列表构建
    try {
      const res = await knowledgeApi.getKnowledgePoints(selectedCourseId.value)
      const list = res.data?.list || res.data || res || []
      knowledgeTree.value = Array.isArray(list) ? buildTreeFromList(list) : []
    } catch (e2) {
      console.error('加载知识树失败:', e2)
      knowledgeTree.value = []
    }
  } finally {
    treeLoading.value = false
  }
}

// 加载知识点列表
async function loadKnowledgeList() {
  tableLoading.value = true
  try {
    const res = await knowledgeApi.getKnowledgePoints(selectedCourseId.value)
    const data = res.data?.list || res.data || res || []
    knowledgeList.value = (Array.isArray(data) ? data : []).map((k) => ({
      ...k,
      importance: k.importance ?? k.weight ?? 1,
    }))
  } catch (e) {
    console.error('加载知识点列表失败:', e)
    knowledgeList.value = []
  } finally {
    tableLoading.value = false
  }
}

// 加载知识图谱数据（增强：使用 knowledgeRelationApi.getGraphData）
async function loadKnowledgeGraph() {
  try {
    const res = await knowledgeRelationApi.getGraphData(selectedCourseId.value)
    const data = res.data || res || {}
    updateForceGraph(data.nodes || [], data.links || data.edges || [])
  } catch (e) {
    console.error('加载图谱数据失败:', e)
    // 使用知识点列表生成默认图谱
    updateForceGraph(
      knowledgeList.value.map((k) => ({
        id: k.id,
        name: k.name,
        difficulty: k.difficulty,
        importance: k.importance,
      })),
      [],
    )
  }
}

// 从列表构建树
function buildTreeFromList(list) {
  const map = new Map()
  const roots = []

  for (const item of list) {
    map.set(item.id, { ...item, children: [] })
  }

  for (const item of list) {
    const node = map.get(item.id)
    if (item.parentId && map.has(item.parentId)) {
      map.get(item.parentId).children.push(node)
    } else {
      roots.push(node)
    }
  }

  return roots
}

// 构建 el-tree-select 数据
function buildTreeSelectData(tree) {
  return tree.map((node) => ({
    ...node,
    children: node.children?.length ? buildTreeSelectData(node.children) : undefined,
  }))
}

function handleFilter() {}

function expandAll() {
  const nodes = treeRef.value?.store?.nodesMap
  if (nodes) {
    Object.values(nodes).forEach((node) => node.expand())
  }
}

function handleNodeClick(_data) {
  // 点击节点时高亮或定位到列表
}

// 打开创建对话框
function openCreateDialog() {
  isEdit.value = false
  editingId.value = null
  Object.assign(kpForm, {
    name: '',
    courseId: selectedCourseId.value,
    parentId: null,
    difficulty: 'MEDIUM',
    importance: 2,
    description: '',
  })
  formDialogVisible.value = true
}

// 打开编辑对话框
function openEditDialog(row) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(kpForm, {
    name: row.name || '',
    courseId: row.courseId || selectedCourseId.value,
    parentId: row.parentId || null,
    difficulty: row.difficulty || 'MEDIUM',
    importance: row.importance ?? 2,
    description: row.description || '',
  })
  formDialogVisible.value = true
}

// 提交表单
async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (isEdit.value) {
        await knowledgeApi.updateKnowledgePoint(editingId.value, kpForm)
        ElMessage.success('更新成功')
      } else {
        await knowledgeApi.createKnowledgePoint(kpForm)
        ElMessage.success('创建成功')
      }
      formDialogVisible.value = false
      await loadAllData()
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '操作失败')
    } finally {
      submitting.value = false
    }
  })
}

// 删除知识点
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除知识点「${row.name}」？其子节点也将被删除`, '删除确认', {
      type: 'warning',
    })
    await knowledgeApi.deleteKnowledgePoint(row.id)
    ElMessage.success('删除成功')
    await loadAllData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

// 打开关联关系对话框（增强：使用 knowledgeApi.getPrerequisites）
async function openRelationDialog(row) {
  currentKpForRelation.value = row
  newPrereqId.value = null
  relationDialogVisible.value = true

  // 加载前置依赖（使用 API 方法）
  try {
    const res = await knowledgeApi.getPrerequisites(row.id)
    const data = res.data?.data || res.data || res || []
    prerequisitesList.value = Array.isArray(data) ? data : []
  } catch (e) {
    prerequisitesList.value = []
  }
}

// 添加前置依赖
async function addPrerequisite() {
  if (!newPrereqId.value || !currentKpForRelation.value) return
  try {
    await knowledgeApi.addRelation({
      sourceId: newPrereqId.value,
      targetId: currentKpForRelation.value.id,
      type: 'prerequisite',
    })
    ElMessage.success('添加关联成功')
    await openRelationDialog(currentKpForRelation.value)
    await loadKnowledgeGraph()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '添加失败')
  }
}

// 移除前置依赖（增强：使用 knowledgeRelationApi）
async function removePrerequisite(prereqId) {
  try {
    await knowledgeRelationApi.delete(prereqId)
    ElMessage.success('移除关联成功')
    await openRelationDialog(currentKpForRelation.value)
    await loadKnowledgeGraph()
  } catch (e) {
    ElMessage.error('移除失败')
  }
}

// 力导向图配置
function buildForceGraphOption() {
  const t = getChartTheme()
  const st = getMonoStatus()
  return {
    tooltip: {
      backgroundColor: t.tooltipBg,
      borderColor: t.tooltipBorder,
      textStyle: { color: t.tooltipText },
    },
    animationDurationUpdate: 500,
    animationEasingUpdate: 'quinticInOut',
    series: [
      {
        type: 'graph',
        layout: 'force',
        data: [],
        links: [],
        roam: true,
        draggable: true,
        force: {
          repulsion: 200,
          edgeLength: 120,
          gravity: 0.1,
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 3 },
        },
        lineStyle: {
          color: t.axis,
          width: 1.5,
          curveness: 0.2,
          opacity: 0.7,
        },
        label: {
          show: true,
          position: 'bottom',
          fontSize: 11,
          color: t.text,
          formatter: '{b}',
        },
        symbolSize: (val) => {
          const importance = val?.[2] || 1
          return 16 + importance * 10
        },
        itemStyle: {
          borderColor: t.sliceBorder,
          borderWidth: 2,
        },
        color: (params) => {
          const difficulty = params.data?.difficulty
          const colors = { EASY: st.EASY, MEDIUM: st.MEDIUM, HARD: st.HARD }
          return colors[difficulty] || st.INFO
        },
      },
    ],
  }
}
const forceGraphOption = ref(buildForceGraphOption())

// 树形图配置
function buildTreeGraphOption() {
  const t = getChartTheme()
  const st = getMonoStatus()
  return {
    tooltip: {
      backgroundColor: t.tooltipBg,
      borderColor: t.tooltipBorder,
      textStyle: { color: t.tooltipText },
    },
    series: [
      {
        type: 'tree',
        data: [],
        orient: 'LR',
        symbol: 'circle',
        symbolSize: 12,
        initialTreeDepth: -1,
        roam: true,
        label: {
          position: 'top',
          verticalAlign: 'middle',
          align: 'right',
          fontSize: 11,
          color: t.text,
        },
        leaves: {
          label: { position: 'bottom', verticalAlign: 'middle', align: 'left' },
        },
        expandAndCollapse: true,
        animationDuration: 550,
        animationDurationUpdate: 750,
        itemStyle: {
          color: (params) => {
            const d = params.data?.difficulty
            return d === 'HARD' ? st.HARD : d === 'EASY' ? st.EASY : st.MEDIUM
          },
          borderColor: t.sliceBorder,
          borderWidth: 2,
        },
        lineStyle: { color: t.axis, width: 1.5, curveness: 0.3 },
      },
    ],
  }
}
const treeGraphOption = ref(buildTreeGraphOption())

// 更新力导向图数据
function updateForceGraph(nodes, links) {
  if (nodes.length === 0) {
    // 从知识点列表生成节点
    nodes = knowledgeList.value.map((k) => ({
      id: k.id,
      name: k.name,
      difficulty: k.difficulty,
      category: k.difficulty === 'HARD' ? 2 : k.difficulty === 'EASY' ? 0 : 1,
      value: [k.importance || 1],
    }))
  }

  forceGraphOption.value.series[0].data = nodes.map((n) => ({
    name: n.name || n.label || '未知',
    id: n.id,
    difficulty: n.difficulty,
    value: n.value || [n.importance || 1],
    category: n.category ?? (n.difficulty === 'HARD' ? 2 : n.difficulty === 'EASY' ? 0 : 1),
    symbolSize: 18 + (n.importance || 1) * 8,
    itemStyle: { color: getNodeColor(n.difficulty) },
  }))
  forceGraphOption.value.series[0].links = (links || []).map((l) => ({
    source: l.source || l.sourceName || l.sourceId,
    target: l.target || l.targetName || l.targetId,
    value: l.type || 'related',
  }))

  // 更新树形图数据
  if (knowledgeTree.value.length > 0) {
    treeGraphOption.value.series[0].data = transformToTreeData(knowledgeTree.value[0])
  }
}

function transformToTreeData(node) {
  return {
    name: node.name,
    difficulty: node.difficulty,
    id: node.id,
    children: (node.children || []).map((c) => transformToTreeData(c)),
  }
}

function getNodeColor(difficulty) {
  const st = getMonoStatus()
  const map = { EASY: st.EASY, MEDIUM: st.MEDIUM, HARD: st.HARD }
  return map[difficulty] || st.INFO
}

function getDifficultyTagType(difficulty) {
  const map = { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }
  return map[difficulty] || 'info'
}

function getDifficultyLabel(difficulty) {
  const map = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
  return map[difficulty] || '-'
}
</script>

<style scoped>
.km-main-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: var(--ac-spacing-md);
  min-height: calc(100vh - 200px);
}

.tree-panel {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--ac-spacing-md) var(--ac-spacing-lg);
  border-bottom: 1px solid var(--ac-border);
}

.tree-container {
  flex: 1;
  padding: var(--ac-spacing-sm) var(--ac-spacing-md);
  overflow-y: auto;
}

.tree-container :deep(.el-tree) {
  background: transparent;
}

.tree-container :deep(.el-tree-node__content) {
  height: 38px;
  border-radius: var(--ac-radius-sm);
  margin: 2px 0;
  transition: background var(--ac-transition-fast);
}

.tree-container :deep(.el-tree-node__content:hover) {
  background: var(--ac-bg-hover);
}

.tree-container :deep(.is-current > .el-tree-node__content) {
  background: var(--ac-primary-bg);
  font-weight: 600;
}

.tree-node-label {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.node-name {
  font-size: 13px;
  color: var(--ac-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.right-panel {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-md);
  min-width: 0;
}

.list-card {
  flex-shrink: 0;
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

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  letter-spacing: -0.2px;
}

.relation-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.relation-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  transition: all var(--ac-transition-fast);
}

.relation-item:hover {
  background: var(--ac-bg-hover);
}

.relation-arrow {
  color: var(--ac-secondary);
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}

.relation-name {
  font-size: 13px;
  color: var(--ac-text-secondary);
  flex: 1;
}

/* ====== 提取的内联样式 ====== */
.w-200 {
  width: 200px;
}
.w-220 {
  width: 220px;
}
.w-100 {
  width: 100%;
}
.tree-tag {
  margin-left: 6px;
}
.graph-chart {
  width: 100%;
  height: 340px;
}
.rel-desc {
  margin-bottom: 16px;
  color: var(--ac-text-secondary);
  font-size: 13px;
}
.rel-desc strong {
  color: var(--ac-primary);
}
.rel-section {
  margin-bottom: 16px;
}
.rel-heading {
  font-size: 13px;
  margin-bottom: 8px;
  color: var(--ac-text-primary);
}
.rel-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.rel-select {
  flex: 1;
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
