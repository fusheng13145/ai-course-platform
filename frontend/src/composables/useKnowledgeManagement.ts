import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import logger from '@/utils/logger'
import knowledgeApi from '@/api/knowledge'
import courseApi from '@/api/course'
import knowledgeRelationApi from '@/api/knowledgePointRelation'
import { getChartTheme, getMonoStatus } from '@/utils/chartTheme'

/** 难度 → 标签类型/文案映射 */
const DIFFICULTY_TAG = { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }
const DIFFICULTY_LABEL = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }

/**
 * 知识管理（教师）数据与业务逻辑
 * <p>承载课程知识点维护的全部状态与操作：课程切换、知识树加载（getTree 优先 + 列表回退构建）、
 * 知识点 CRUD（含表单校验与难度/重要度）、前置依赖关联管理、知识图谱构建
 * （力导向 + 树形两种视图模式）。</p>
 */
export function useKnowledgeManagement() {
  const selectedCourseId = ref(null)
  const courseList = ref([])
  const searchText = ref('')

  const treeLoading = ref(false)
  const knowledgeTree = ref([])
  const treeRef = ref(null)

  const tableLoading = ref(false)
  const knowledgeList = ref([])

  /* ---- 表单对话框 ---- */
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

  /* ---- 关联关系 ---- */
  const relationDialogVisible = ref(false)
  const currentKpForRelation = ref(null)
  const newPrereqId = ref(null)
  const prerequisitesList = ref([])

  const graphViewMode = ref('force')

  /** 关键字过滤知识点列表（名称/描述） */
  const filteredKnowledgeList = computed(() => {
    if (!searchText.value) return knowledgeList.value
    const kw = searchText.value.toLowerCase()
    return knowledgeList.value.filter(
      (k) =>
        (k.name || '').toLowerCase().includes(kw) ||
        (k.description || '').toLowerCase().includes(kw),
    )
  })

  /** 树形下拉选项（供 el-tree-select） */
  const flatTreeOptions = computed(() => buildTreeSelectData(knowledgeTree.value))

  /** 可选前置知识点（排除自身与已有关联） */
  const availablePrereqs = computed(() => {
    if (!currentKpForRelation.value) return []
    const existingIds = new Set(prerequisitesList.value.map((p) => p.id))
    existingIds.add(currentKpForRelation.value.id)
    return knowledgeList.value.filter((k) => !existingIds.has(k.id))
  })

  /** 加载课程选项；默认选中第一门并加载数据 */
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
      logger.error('加载课程失败:', e)
    }
  }

  /** 并行加载树/列表/图谱 */
  async function loadAllData() {
    if (!selectedCourseId.value) return
    await Promise.all([loadKnowledgeTree(), loadKnowledgeList(), loadKnowledgeGraph()])
  }

  /** 加载知识树（getTree 优先，失败回退列表构建） */
  async function loadKnowledgeTree() {
    treeLoading.value = true
    try {
      const res = await knowledgeApi.getTree(selectedCourseId.value)
      const data = res.data?.data || res.data || res || []
      knowledgeTree.value = Array.isArray(data) ? data : data ? [data] : []
    } catch (e) {
      try {
        const res = await knowledgeApi.getKnowledgePoints(selectedCourseId.value)
        const list = res.data?.list || res.data || res || []
        knowledgeTree.value = Array.isArray(list) ? buildTreeFromList(list) : []
      } catch (e2) {
        logger.error('加载知识树失败:', e2)
        knowledgeTree.value = []
      }
    } finally {
      treeLoading.value = false
    }
  }

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
      logger.error('加载知识点列表失败:', e)
      knowledgeList.value = []
    } finally {
      tableLoading.value = false
    }
  }

  /** 加载知识图谱（关系 API 优先，失败用列表生成节点） */
  async function loadKnowledgeGraph() {
    try {
      const res = await knowledgeRelationApi.getGraphData(selectedCourseId.value)
      const data = res.data || res || {}
      updateForceGraph(data.nodes || [], data.links || data.edges || [])
    } catch (e) {
      logger.error('加载图谱数据失败:', e)
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

  /** 从平铺列表构建父子树 */
  function buildTreeFromList(list) {
    const map = new Map()
    const roots = []
    for (const item of list) map.set(item.id, { ...item, children: [] })
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

  /** 树 → el-tree-select 数据（递归） */
  function buildTreeSelectData(tree) {
    return tree.map((node) => ({
      ...node,
      children: node.children?.length ? buildTreeSelectData(node.children) : undefined,
    }))
  }

  /** 展开树全部节点 */
  function expandAll() {
    const nodes = treeRef.value?.store?.nodesMap
    if (nodes) {
      Object.values(nodes).forEach((node) => (node as any).expand())
    }
  }

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

  /** 提交表单（校验通过后创建/更新） */
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

  /** 删除知识点（连带子节点，二次确认） */
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

  /** 打开关联关系对话框并加载前置依赖 */
  async function openRelationDialog(row) {
    currentKpForRelation.value = row
    newPrereqId.value = null
    relationDialogVisible.value = true
    try {
      const res = await knowledgeApi.getPrerequisites(row.id)
      const data = res.data?.data || res.data || res || []
      prerequisitesList.value = Array.isArray(data) ? data : []
    } catch (e) {
      prerequisitesList.value = []
    }
  }

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

  /* ---- 图谱配置 ---- */

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
          force: { repulsion: 200, edgeLength: 120, gravity: 0.1 },
          emphasis: { focus: 'adjacency', lineStyle: { width: 3 } },
          lineStyle: { color: t.axis, width: 1.5, curveness: 0.2, opacity: 0.7 },
          label: { show: true, position: 'bottom', fontSize: 11, color: t.text, formatter: '{b}' },
          symbolSize: (val) => 16 + (val?.[2] || 1) * 10,
          itemStyle: { borderColor: t.sliceBorder, borderWidth: 2 },
          color: (params) => {
            const colors = { EASY: st.EASY, MEDIUM: st.MEDIUM, HARD: st.HARD }
            return colors[params.data?.difficulty] || st.INFO
          },
        },
      ],
    }
  }
  const forceGraphOption = ref(buildForceGraphOption())

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
          leaves: { label: { position: 'bottom', verticalAlign: 'middle', align: 'left' } },
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

  /** 更新力导向图节点/边（空节点时从列表生成），并同步树形图 */
  function updateForceGraph(nodes, links) {
    if (nodes.length === 0) {
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
    if (knowledgeTree.value.length > 0) {
      treeGraphOption.value.series[0].data = [transformToTreeData(knowledgeTree.value[0])]
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
    return DIFFICULTY_TAG[difficulty] || 'info'
  }
  function getDifficultyLabel(difficulty) {
    return DIFFICULTY_LABEL[difficulty] || '-'
  }

  return {
    selectedCourseId,
    courseList,
    searchText,
    treeLoading,
    knowledgeTree,
    treeRef,
    tableLoading,
    knowledgeList,
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
    expandAll,
    openCreateDialog,
    openEditDialog,
    submitForm,
    handleDelete,
    openRelationDialog,
    addPrerequisite,
    removePrerequisite,
    getDifficultyTagType,
    getDifficultyLabel,
  }
}
