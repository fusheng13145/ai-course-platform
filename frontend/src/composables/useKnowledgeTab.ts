import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import knowledgeApi from '@/api/knowledge'
import { getChartTheme, getMonoSeries, getMonoStatus } from '@/utils/chartTheme'

/**
 * 课程知识点 Tab 数据逻辑（拆分自 components/CourseKnowledgeTab.vue，2026-08-29）
 * @param {import('vue').Ref} courseIdRef 课程 ID（由父组件传入的 ref）
 */
export function useKnowledgeTab(courseIdRef) {
  const searchText = ref('')
  const treeLoading = ref(false)
  const tableLoading = ref(false)
  const treeData = ref([])
  const list = ref([])

  // 表单
  const formVisible = ref(false)
  const isEdit = ref(false)
  const editingId = ref(null)
  const submitting = ref(false)
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
  const aiVisible = ref(false)
  const aiText = ref('')
  const aiExtracting = ref(false)
  const graphMode = ref('force')

  const filteredData = computed(() => {
    if (!searchText.value) return list.value
    const kw = searchText.value.toLowerCase()
    return list.value.filter(
      (k) =>
        (k.name || '').toLowerCase().includes(kw) ||
        (k.description || '').toLowerCase().includes(kw),
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
  watch(() => courseIdRef.value, loadAll)

  async function loadAll() {
    if (!courseIdRef.value) return
    await Promise.all([loadTree(), loadList(), loadGraph()])
  }

  async function loadTree() {
    treeLoading.value = true
    try {
      let res
      try {
        res = await knowledgeApi.getTree(Number(courseIdRef.value))
      } catch (e) {
        res = await knowledgeApi.getKnowledgePoints(Number(courseIdRef.value))
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
        const r = await knowledgeApi.getKnowledgePoints(Number(courseIdRef.value))
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
      const res = await knowledgeApi.getKnowledgePoints(Number(courseIdRef.value))
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
      const res = await knowledgeApi.getRelationGraphData(Number(courseIdRef.value))
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
    return t.map((n) => ({
      ...n,
      children: n.children?.length ? buildTree(n.children) : undefined,
    }))
  }
  function buildTreeSelect(t) {
    return t.map((n) => ({
      ...n,
      children: n.children?.length ? buildTreeSelect(n.children) : undefined,
    }))
  }

  function handleNodeClick() {}

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

  async function savePoint() {
    submitting.value = true
    try {
      const payload = { ...form, courseId: Number(courseIdRef.value) }
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
        courseId: Number(courseIdRef.value),
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
            courseId: Number(courseIdRef.value),
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
            courseId: Number(courseIdRef.value),
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
    if (treeData.value?.[0])
      treeOption.value.series[0].data = transformTree(treeData.value[0]) as Record<
        string,
        unknown
      >[]
  }
  function transformTree(node) {
    return {
      name: node.name,
      difficulty: node.difficulty,
      id: node.id,
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
    link.download = `knowledge_${courseIdRef.value}.csv`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  }

  return {
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
    loadAll,
    handleNodeClick,
    openCreateDialog,
    openEditDialog,
    savePoint,
    handleDelete,
    openAIDialog,
    doAIExtract,
    getDiffTag,
    getDiffLabel,
    handleExport,
  }
}
