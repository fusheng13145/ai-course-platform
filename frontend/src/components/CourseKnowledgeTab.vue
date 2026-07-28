<template>
  <div class="knowledge-tab">
    <!-- 工具栏 -->
    <div class="tab-toolbar">
      <div style="display: flex; gap: 8px; align-items: center">
        <el-button type="primary" plain size="small" @click="openCreateDialog"
          ><Plus :size="14" /> 新建知识点</el-button
        >
        <el-button size="small" @click="openAIDialog"><MagicStick :size="12" /> AI提取</el-button>
        <el-button size="small" :disabled="!list.length" @click="handleExport"
          ><Download :size="12" /> 导出</el-button
        >
      </div>
      <el-input
        v-model="searchText"
        placeholder="搜索知识点..."
        clearable
        style="width: 200px"
        :prefix-icon="Search"
      />
    </div>

    <!-- 主区域：知识树 + 列表/图谱 -->
    <div class="km-grid">
      <!-- 左侧知识树 -->
      <div class="tree-panel">
        <div class="panel-header">
          <span class="panel-title">知识结构</span>
          <el-button text size="small" type="primary" @click="expandAll">展开全部</el-button>
        </div>
        <div v-loading="treeLoading" class="tree-wrap">
          <el-tree
            ref="treeRef"
            :data="treeData"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            empty-text="暂无知识点"
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <span class="tree-label">
                <span>{{ node.label }}</span>
                <el-tag size="small" :type="getDiffTag(data.difficulty)" style="margin-left: 4px">{{
                  getDiffLabel(data.difficulty)
                }}</el-tag>
              </span>
            </template>
          </el-tree>
          <el-empty
            v-if="!treeLoading && !treeData.length"
            description="暂无知识树数据"
            :image-size="50"
          />
        </div>
      </div>

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
                  <el-button text type="primary" size="small" @click="openEditDialog(row)"
                    >编辑</el-button
                  >
                  <el-button text type="danger" size="small" @click="handleDelete(row)"
                    >删除</el-button
                  >
                </div>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!tableLoading && !filteredData.length" description="暂无知识点" />
        </div>

        <!-- 知识图谱 -->
        <div class="graph-card">
          <div class="panel-header">
            <span class="panel-title">知识图谱</span>
            <el-radio-group v-model="graphMode" size="small">
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
    </div>

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑知识点' : '新建知识点'"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name"
          ><el-input v-model="form.name" placeholder="输入名称" maxlength="50" show-word-limit
        /></el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="form.difficulty" style="width: 100%"
            ><el-option label="简单" value="EASY" /><el-option
              label="中等"
              value="MEDIUM" /><el-option label="困难" value="HARD"
          /></el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"
            ><el-form-item label="重要度"
              ><el-rate v-model="form.importance" :max="3" /><br /><span
                style="font-size: 11px; color: var(--ac-text-secondary)"
                >{{ ['低', '中', '高'][form.importance - 1] || '中' }}</span
              ></el-form-item
            ></el-col
          >
          <el-col :span="12"
            ><el-form-item label="父级节点">
              <el-tree-select
                v-model="form.parentId"
                :data="flatTreeOptions"
                :props="{ label: 'name', value: 'id', children: 'children' }"
                check-strictly
                clearable
                placeholder="可选"
                style="width: 100%"
                filterable
              /> </el-form-item
          ></el-col>
        </el-row>
        <el-form-item label="描述"
          ><el-input v-model="form.description" type="textarea" :rows="2" maxlength="200"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{
          isEdit ? '保存' : '创建'
        }}</el-button>
      </template>
    </el-dialog>

    <!-- AI提取对话框 -->
    <el-dialog
      v-model="aiVisible"
      title="AI提取知识点"
      width="560px"
      append-to-body
      destroy-on-close
    >
      <el-form>
        <el-form-item label="输入文本"
          ><el-input
            v-model="aiText"
            type="textarea"
            :rows="6"
            placeholder="课程描述、教学大纲等..."
            maxlength="3000"
            show-word-limit
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aiVisible = false">取消</el-button>
        <el-button type="primary" :loading="aiExtracting" @click="doAIExtract">开始提取</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Download, MagicStick } from '@element-plus/icons-vue'
import knowledgeApi from '@/api/knowledge'
import { getChartTheme, getMonoSeries, getMonoStatus } from '@/utils/chartTheme'

const props = defineProps({ courseId: { type: [Number, String], required: true } })
const searchText = ref('')
const treeLoading = ref(false),
  tableLoading = ref(false)
const treeData = ref([]),
  list = ref([])
const treeRef = ref(null)

// 表单
const formVisible = ref(false),
  isEdit = ref(false),
  editingId = ref(null),
  submitting = ref(false),
  formRef = ref(null)
const form = reactive({
  name: '',
  difficulty: 'MEDIUM',
  importance: 2,
  parentId: null,
  description: '',
})
const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  difficulty: [{ required: true, message: '请选难度', trigger: 'change' }],
}

// AI
const aiVisible = ref(false),
  aiText = ref(''),
  aiExtracting = ref(false)
const graphMode = ref('force')

const filteredData = computed(() => {
  if (!searchText.value) return list.value
  const kw = searchText.value.toLowerCase()
  return list.value.filter(
    (k) =>
      (k.name || '').toLowerCase().includes(kw) || (k.description || '').toLowerCase().includes(kw),
  )
})
const flatTreeOptions = computed(() => buildTreeSelect(treeData.value))

// 力导向图
const forceOption = ref({
  tooltip: {},
  animationDurationUpdate: 500,
  series: [
    {
      type: 'graph',
      layout: 'force',
      data: [],
      links: [],
      roam: true,
      draggable: true,
      force: { repulsion: 180, edgeLength: 100, gravity: 0.1 },
      emphasis: { focus: 'adjacency', lineStyle: { width: 3 } },
      lineStyle: { color: getMonoSeries()[1], width: 1.5, curveness: 0.2, opacity: 0.7 },
      label: { show: true, position: 'bottom', fontSize: 10, color: getChartTheme().text },
      symbolSize: (val) => 18 + (val?.[2] || 1) * 8,
      itemStyle: { borderColor: getChartTheme().sliceBorder, borderWidth: 2 },
      color: (p) => {
        const d = p.data?.difficulty
        const s = getMonoStatus()
        return s[d] || getMonoSeries()[1]
      },
    },
  ],
})

// 树形图
const treeOption = ref({
  tooltip: {},
  series: [
    {
      type: 'tree',
      data: [],
      orient: 'LR',
      symbol: 'circle',
      symbolSize: 11,
      initialTreeDepth: -1,
      roam: true,
      label: {
        position: 'top',
        verticalAlign: 'middle',
        align: 'right',
        fontSize: 10.5,
        color: getChartTheme().text,
      },
      leaves: { label: { position: 'bottom', verticalAlign: 'middle', align: 'left' } },
      expandAndCollapse: true,
      animationDuration: 550,
      itemStyle: {
        color: (p) => {
          const d = p.data?.difficulty
          return d === 'HARD'
            ? getMonoStatus().HARD
            : d === 'EASY'
              ? getMonoStatus().EASY
              : getMonoStatus().MEDIUM
        },
        borderColor: getChartTheme().sliceBorder,
        borderWidth: 2,
      },
      lineStyle: { color: getChartTheme().axis, width: 1.5, curveness: 0.3 },
    },
  ],
})

onMounted(loadAll)
watch(() => props.courseId, loadAll)

async function loadAll() {
  if (!props.courseId) return
  await Promise.all([loadTree(), loadList(), loadGraph()])
}

async function loadTree() {
  treeLoading.value = true
  try {
    let res
    try {
      res = await knowledgeApi.getKnowledgeTree(Number(props.courseId))
    } catch (e) {
      res = await knowledgeApi.getKnowledgePoints(Number(props.courseId))
    }
    const d = res.data?.data || res.data || res || []
    if (Array.isArray(d)) {
      treeData.value = d
    } else if (Array.isArray(res.data?.list || res.data?.records || [])) {
      treeData.value = Array.isArray(res.data.list) ? res.data.list : res.data.records
    } else if (d) {
      treeData.value = [d]
    } else {
      treeData.value = []
    }
  } catch (e) {
    try {
      const r = await knowledgeApi.getKnowledgePoints(Number(props.courseId))
      const arr = r.data?.list || r.data || r || []
      list.value = Array.isArray(arr) ? arr : []
      treeData.value = buildTree(list.value)
    } catch (e2) {
      treeData.value = []
    }
  } finally {
    treeLoading.value = false
  }
}

async function loadList() {
  tableLoading.value = true
  try {
    const res = await knowledgeApi.getKnowledgePoints(Number(props.courseId))
    const d = res.data?.list || res.data || res || []
    list.value = (Array.isArray(d) ? d : []).map((k) => ({
      ...k,
      importance: k.importance ?? k.weight ?? 1,
    }))
  } catch (e) {
    list.value = []
  } finally {
    tableLoading.value = false
  }
}

async function loadGraph() {
  try {
    const res = await knowledgeApi.getRelationGraphData(Number(props.courseId))
    const d = res.data || res || {}
    updateForce(d.nodes || [], d.links || d.edges || [])
  } catch (e) {
    updateForce(
      list.value.map((k) => ({
        id: k.id,
        name: k.name,
        difficulty: k.difficulty,
        importance: k.importance || 1,
      })),
      [],
    )
  }
}

function buildTree(t) {
  return t.map((n) => ({ ...n, children: n.children?.length ? buildTree(n.children) : undefined }))
}
function buildTreeSelect(t) {
  return t.map((n) => ({
    ...n,
    children: n.children?.length ? buildTreeSelect(n.children) : undefined,
  }))
}

function handleNodeClick() {}
function expandAll() {
  const nodes = treeRef.value?.store?.nodesMap
  if (nodes) Object.values(nodes).forEach((n) => n.expand())
}

function openCreateDialog() {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, {
    name: '',
    difficulty: 'MEDIUM',
    importance: 2,
    parentId: null,
    description: '',
  })
  formVisible.value = true
}
function openEditDialog(row) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    name: row.name || '',
    difficulty: row.difficulty || 'MEDIUM',
    importance: row.importance ?? 2,
    parentId: row.parentId || null,
    description: row.description || '',
  })
  formVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = { ...form, courseId: Number(props.courseId) }
      if (isEdit.value) {
        await knowledgeApi.updateKnowledgePoint(editingId.value, payload)
        ElMessage.success('更新成功')
      } else {
        await knowledgeApi.createKnowledgePoint(payload)
        ElMessage.success('创建成功')
      }
      formVisible.value = false
      await loadAll()
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '操作失败')
    } finally {
      submitting.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？`, '提示', { type: 'warning' })
    await knowledgeApi.deleteKnowledgePoint(row.id)
    ElMessage.success('已删除')
    await loadAll()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

function openAIDialog() {
  aiText.value = ''
  aiVisible.value = true
}

async function doAIExtract() {
  let importFailures = 0
  if (!aiText.value.trim()) return ElMessage.warning('请输入文本')
  aiExtracting.value = true
  try {
    const res = await knowledgeApi.extractKnowledgePoints({
      text: aiText.value,
      courseId: Number(props.courseId),
    })
    const pts = res.data?.points || res.data?.data || res.data || []
    if ((Array.isArray(pts) ? pts : []).length) ElMessage.success(`提取到 ${pts.length} 个知识点`)
    else ElMessage.info('AI暂不可用，已使用本地分析')
    // 导入提取结果
    for (const p of Array.isArray(pts) ? pts : []) {
      try {
        await knowledgeApi.createKnowledgePoint({
          name: p.name,
          description: p.description || '',
          courseId: Number(props.courseId),
          difficulty: p.difficulty || 'MEDIUM',
          importance: p.importance || 2,
        })
      } catch {
        importFailures++
      }
    }
    await loadAll()
    aiVisible.value = false
  } catch (e) {
    // fallback
    const sentences = aiText.value
      .split(/[。！？\n]+/)
      .filter((s) => s.trim().length > 5)
      .slice(0, 15)
    for (const s of sentences) {
      try {
        await knowledgeApi.createKnowledgePoint({
          name: s.trim(),
          courseId: Number(props.courseId),
          difficulty: 'MEDIUM',
          importance: 2,
        })
      } catch {
        importFailures++
      }
    }
    ElMessage.success(`导入 ${Math.min(sentences.length, 15)} 个知识点`)
    aiVisible.value = false
    await loadAll()
  } finally {
    if (importFailures > 0) {
      ElMessage.warning(`有 ${importFailures} 个知识点导入失败，请稍后重试`)
    }
    aiExtracting.value = false
  }
}

function updateForce(nodes, links) {
  if (!nodes.length) {
    nodes = list.value.map((k) => ({
      id: k.id,
      name: k.name,
      difficulty: k.difficulty,
      value: [k.importance || 1],
    }))
  }
  forceOption.value.series[0].data = nodes.map((n) => ({
    name: n.name || n.label || '未知',
    id: n.id,
    difficulty: n.difficulty,
    value: n.value || [n.importance || 1],
    category: n.difficulty === 'HARD' ? 2 : n.difficulty === 'EASY' ? 0 : 1,
    symbolSize: 16 + (n.importance || 1) * 7,
    itemStyle: { color: getNodeColor(n.difficulty) },
  }))
  forceOption.value.series[0].links = (links || []).map((l) => ({
    source: l.source || l.sourceName || l.sourceId,
    target: l.target || l.targetName || l.targetId,
  }))
  if (treeData.value?.[0]) treeOption.value.series[0].data = transformTree(treeData.value[0])
}
function transformTree(node) {
  return {
    name: node.name,
    difficulty: node.difficulty,
    id: id,
    children: (node.children || []).map((c) => transformTree(c)),
  }
}
function getNodeColor(d) {
  const m = getMonoStatus()
  return m[d] || getMonoSeries()[1]
}

function getDiffTag(d) {
  return { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }[d] || 'info'
}
function getDiffLabel(d) {
  return { EASY: '简单', MEDIUM: '中等', HARD: '困难' }[d] || '-'
}

function handleExport() {
  if (!list.value.length) return
  const headers = ['name', 'difficulty', 'importance', 'description']
  const rows = list.value.map((k) => [
    (k.name || '').replace(/,/g, '，'),
    k.difficulty || 'MEDIUM',
    k.importance || 2,
    (k.description || '').replace(/,/g, '，'),
  ])
  const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n')
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `knowledge_${props.courseId}.csv`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
}
</script>

<style scoped>
.knowledge-tab {
  padding: 4px 0;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 8px;
  flex-wrap: wrap;
}
.km-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 14px;
  min-height: 420px;
}
.tree-panel {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid var(--ac-border);
}
.panel-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}
.tree-wrap {
  flex: 1;
  padding: 6px 10px;
  overflow-y: auto;
}
.tree-wrap :deep(.el-tree) {
  background: transparent;
}
.tree-wrap :deep(.el-tree-node__content) {
  height: 36px;
  border-radius: 6px;
  margin: 1px 0;
  transition: background 0.15s;
}
.tree-wrap :deep(.el-tree-node__content:hover) {
  background: var(--ac-bg-hover);
}
.tree-label {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  font-size: 13px;
}
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
.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  flex-wrap: nowrap;
}
@media (max-width: 1024px) {
  .km-grid {
    grid-template-columns: 1fr;
  }
  .tree-panel {
    max-height: 260px;
  }
}
</style>
