import logger from '@/utils/logger'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Cpu, Warning, CircleCheck } from '@element-plus/icons-vue'
import ragApi from '@/api/rag'
import courseApi from '@/api/course'
import { useCourseKbBuild } from './useCourseKbBuild'
import { confirmDanger } from '@/utils/dangerConfirm'

/**
 * 知识库管理领域逻辑
 * <p>课程/文档加载、模型状态检测、文档增删改、索引重建/清理、RAG 问答与语义召回。</p>
 */
export function useKnowledgeBase() {
  const loading = ref(false)
  const indexing = ref(false)
  const asking = ref(false)
  const retrieving = ref(false)
  const saving = ref(false)

  const courseList = ref([])
  const selectedCourseId = ref(null)
  const docs = ref([])
  const keyword = ref('')

  const currentCourseName = computed(() => {
    const c = courseList.value.find((x) => x.id === selectedCourseId.value)
    return c ? c.name || c.title : ''
  })

  /* 模型状态检测 */
  const modelLabel = ref('检测中…')
  const llmReady = ref(null) // null=未知, true=已配置, false=检索态
  const bannerClass = computed(() => {
    if (llmReady.value === false) return 'banner--warn'
    if (llmReady.value === true) return 'banner--ok'
    return 'banner--unknown'
  })
  const bannerIcon = computed(() =>
    llmReady.value === false ? Warning : llmReady.value === true ? CircleCheck : Cpu,
  )
  const bannerText = computed(() => {
    if (llmReady.value === true) return '大模型 API Key 已配置，问答将基于知识库融合生成。'
    if (llmReady.value === false)
      return '未检测到大模型 API Key：问答将回退为「知识库原文展示」（抑制幻觉，保证可用）。配置环境变量 DEEPSEEK_API_KEY / OPENAI_API_KEY / QWEN_API_KEY 并重启后端即可启用生成态。'
    return '正在检测大模型配置状态…'
  })

  const filteredDocs = computed(() => {
    const q = keyword.value.trim().toLowerCase()
    if (!q) return docs.value
    return docs.value.filter(
      (d) =>
        (d.name || '').toLowerCase().includes(q) || (d.description || '').toLowerCase().includes(q),
    )
  })

  function preview(text) {
    if (!text) return '—'
    return text.length > 80 ? text.slice(0, 80) + '…' : text
  }

  /* 加载课程 */
  async function loadCourses() {
    try {
      courseList.value = await courseApi.getAllCourses()
      if (courseList.value.length && !selectedCourseId.value) {
        selectedCourseId.value = courseList.value[0].id
        await loadDocs()
      }
    } catch (e) {
      logger.error('加载课程失败:', e)
      courseList.value = []
    }
  }

  /* 加载文档 */
  async function loadDocs() {
    if (!selectedCourseId.value) {
      docs.value = []
      return
    }
    loading.value = true
    try {
      const res = await ragApi.ragListDocuments(selectedCourseId.value)
      if (res && res.status === 'error') {
        ElMessage.error(res.message || '加载知识库失败')
        docs.value = []
      } else {
        docs.value = Array.isArray(res?.documents) ? res.documents : []
      }
    } catch (e) {
      logger.error('加载知识库失败:', e)
      docs.value = []
      ElMessage.error('加载知识库失败')
    } finally {
      loading.value = false
    }
  }

  function onCourseChange() {
    loadDocs()
  }

  /* 新增 / 编辑 */
  const showDialog = ref(false)
  const dialogMode = ref('add')
  const form = reactive({ id: null, name: '', content: '' })

  function openAdd() {
    dialogMode.value = 'add'
    form.id = null
    form.name = ''
    form.content = ''
    showDialog.value = true
  }
  function openEdit(row) {
    dialogMode.value = 'edit'
    form.id = row.id
    form.name = row.name
    form.content = row.description || ''
    showDialog.value = true
  }
  async function saveDoc() {
    if (dialogMode.value === 'add') {
      if (!form.name.trim()) {
        ElMessage.warning('请输入文档名称')
        return
      }
      if (!form.content.trim()) {
        ElMessage.warning('请输入文档内容')
        return
      }
      saving.value = true
      try {
        const res = await ragApi.ragAddDocument(
          selectedCourseId.value,
          form.name.trim(),
          form.content.trim(),
        )
        if (res && res.status === 'error') {
          ElMessage.error(res.message || '新增失败')
          return
        }
        ElMessage.success('文档已加入知识库')
        showDialog.value = false
        await loadDocs()
      } catch {
        ElMessage.error('新增失败')
      } finally {
        saving.value = false
      }
    } else {
      if (!form.content.trim()) {
        ElMessage.warning('请输入文档内容')
        return
      }
      saving.value = true
      try {
        const res = await ragApi.ragUpdateDocument(form.id, form.content.trim())
        if (res && res.status === 'error') {
          ElMessage.error(res.message || '更新失败')
          return
        }
        ElMessage.success('文档已更新')
        showDialog.value = false
        await loadDocs()
      } catch {
        ElMessage.error('更新失败')
      } finally {
        saving.value = false
      }
    }
  }
  function removeDoc(row) {
    confirmDanger({
      verb: '删除',
      target: '知识库文档',
      subject: row.name || '未命名',
      impact: '将一并移除该文档的向量索引，需重新上传并重建索引后才能用于检索',
    })
      .then(async (ok) => {
        if (!ok) return
        try {
          const res = await ragApi.ragDeleteDocument(row.id)
          if (res && res.status === 'error') {
            ElMessage.error(res.message || '删除失败')
            return
          }
          ElMessage.success('已删除')
          await loadDocs()
        } catch {
          ElMessage.error('删除失败')
        }
      })
      .catch(() => {})
  }

  /* 索引 */
  async function buildIndex() {
    indexing.value = true
    try {
      const res = await ragApi.ragBuildIndex(selectedCourseId.value)
      ElMessage.success((res && res.note) || '索引已构建（知识库实时构建，无需离线处理）')
    } catch {
      ElMessage.error('构建索引失败')
    } finally {
      indexing.value = false
    }
  }
  async function clearIndex() {
    indexing.value = true
    try {
      const res = await ragApi.ragClearIndex(selectedCourseId.value)
      ElMessage.success((res && res.note) || '索引已清理')
    } catch {
      ElMessage.error('清理索引失败')
    } finally {
      indexing.value = false
    }
  }

  /* 从课程资料一键构建知识库（共享逻辑：枚举课程简介/章节/资源 → 幂等写入 → 重建索引） */
  const {
    building: buildingFromCourse,
    buildError: courseKbError,
    buildResult: courseKbResult,
    build: runCourseKbBuild,
  } = useCourseKbBuild(selectedCourseId)
  /** 真正写入后刷新文档列表，让「新增了多少篇」在列表上可直接核对 */
  async function buildCourseKb() {
    if (await runCourseKbBuild()) {
      await loadDocs()
    }
  }

  /* 问答 */
  const askForm = reactive({ question: '', topK: 3 })
  const askResult = ref(null)
  async function doAsk() {
    if (!askForm.question.trim()) {
      ElMessage.warning('请输入问题')
      return
    }
    asking.value = true
    askResult.value = null
    try {
      const res = await ragApi.ragAsk({
        question: askForm.question.trim(),
        courseId: selectedCourseId.value,
        topK: askForm.topK,
      })
      askResult.value = res || {}
      if (res && res.model && res.model !== 'retrieval-only') llmReady.value = true
      else if (res) llmReady.value = false
    } catch {
      ElMessage.error('提问失败')
    } finally {
      asking.value = false
    }
  }

  /* 召回 */
  const retrieveForm = reactive({ query: '', topK: 5 })
  const retrieveResult = ref(null)
  async function doRetrieve() {
    if (!retrieveForm.query.trim()) {
      ElMessage.warning('请输入查询词')
      return
    }
    retrieving.value = true
    retrieveResult.value = null
    try {
      const res = await ragApi.ragRetrieve({
        query: retrieveForm.query.trim(),
        courseId: selectedCourseId.value,
        topK: retrieveForm.topK,
      })
      retrieveResult.value = res || {}
    } catch {
      ElMessage.error('召回失败')
    } finally {
      retrieving.value = false
    }
  }

  onMounted(loadCourses)

  return {
    loading,
    indexing,
    asking,
    retrieving,
    saving,
    buildingFromCourse,
    courseKbError,
    courseKbResult,
    courseList,
    selectedCourseId,
    docs,
    keyword,
    currentCourseName,
    modelLabel,
    llmReady,
    bannerClass,
    bannerIcon,
    bannerText,
    filteredDocs,
    preview,
    showDialog,
    dialogMode,
    form,
    askForm,
    askResult,
    retrieveForm,
    retrieveResult,
    loadDocs,
    onCourseChange,
    openAdd,
    openEdit,
    saveDoc,
    removeDoc,
    buildIndex,
    clearIndex,
    buildCourseKb,
    doAsk,
    doRetrieve,
  }
}
