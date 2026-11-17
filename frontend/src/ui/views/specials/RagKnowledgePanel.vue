<template>
  <section class="rag-panel">
    <section class="card scope-card">
      <div class="filter-row scope-row">
        <label class="knob">
          课程
          <select v-model="courseId" class="input course-pick" aria-label="选择课程" @change="load">
            <option :value="null">选择课程</option>
            <option v-for="course in courses" :key="String(course.id)" :value="course.id">
              {{ course.name || course.title || `课程 #${course.id}` }}
            </option>
          </select>
        </label>
        <button class="btn btn-sm" :disabled="loading || !courseId" @click="load">
          {{ loading ? '加载中…' : '刷新' }}
        </button>
        <button class="btn btn-sm btn-primary" :disabled="busy || !courseId" @click="openCreate">
          新增文档
        </button>
        <button
          class="btn btn-sm"
          :disabled="busy || !courseId"
          data-testid="rag-build-course"
          @click="buildFromCourse"
        >
          从课程资料构建
        </button>
        <button class="btn btn-sm" :disabled="busy || !courseId" @click="buildIndex">
          重建索引
        </button>
        <button class="btn btn-sm btn-ghost" :disabled="busy || !courseId" @click="askClear">
          清空索引
        </button>
      </div>
      <p class="scope-note">
        文档与索引由 Python RAG 服务持有；引擎不可达时按 503 如实透出，不伪装成功。
      </p>
    </section>

    <div v-if="engineNote" class="callout danger" role="alert">
      <span>RAG 引擎</span>
      <p>{{ engineNote }}</p>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <section class="card">
      <header class="card-head">
        <div>
          <h2>检索试验</h2>
        </div>
        <span class="chip chip-mute">{{ retrieval.results.length }} 条命中</span>
      </header>
      <div class="filter-row">
        <input
          v-model="retrieval.query"
          class="input retrieval-input"
          type="search"
          placeholder="输入问题，验证课程语料能否被召回"
          aria-label="检索问题"
          @keyup.enter="runRetrieve"
        />
        <label class="knob">
          Top K
          <input
            v-model.number="retrieval.topK"
            class="input num"
            type="number"
            min="1"
            max="50"
            aria-label="检索条数"
          />
        </label>
        <button
          class="btn btn-sm btn-primary"
          :disabled="retrieval.loading || !courseId || !retrieval.query.trim()"
          @click="runRetrieve"
        >
          {{ retrieval.loading ? '检索中…' : '检索' }}
        </button>
      </div>
      <p v-if="retrieval.error" class="form-error" role="alert">{{ retrieval.error }}</p>
      <div v-else-if="retrieval.results.length" class="retrieval-list">
        <article
          v-for="(item, index) in retrieval.results"
          :key="`r-${index}`"
          class="retrieval-item"
        >
          <header>
            <b>{{
              item.documentName || item.document_name || `文档 #${item.documentId ?? '—'}`
            }}</b>
            <span class="chip chip-info">
              {{ scoreText(item.relevance_score ?? item.rerank_score) }}
            </span>
          </header>
          <p>{{ item.snippet || '（无片段正文）' }}</p>
        </article>
      </div>
      <p v-else-if="retrieval.attempted" class="modal-hint">
        检索已返回，但没有命中任何片段；请确认已构建索引。
      </p>
    </section>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>
            知识库文档 <span class="chip chip-mute">{{ total }} 篇</span>
          </h2>
        </div>
        <div class="filter-row class-tools">
          <input
            v-model="keyword"
            class="input search-input"
            type="search"
            placeholder="搜索文档名 / 正文"
            aria-label="搜索知识库文档"
          />
        </div>
      </header>

      <div v-if="listError" class="callout danger" role="alert">
        <span>知识库文档</span>
        <p>{{ listError }}</p>
        <button class="btn btn-sm" :disabled="loading" @click="load">重试</button>
      </div>
      <div v-else-if="!courseId" class="empty-state compact">
        <b>请先选择课程</b>
        <p>RAG 文档按课程隔离，先选课程再管理文档。</p>
      </div>
      <div v-else-if="loading && !rows.length" class="empty-state compact">
        <b>正在加载知识库文档…</b>
      </div>
      <div v-else-if="!rows.length" class="empty-state compact">
        <b>该课程还没有知识库文档</b>
        <p>可新增文档，或从课程简介 / 章节 / 资源一键构建。</p>
      </div>
      <div v-else-if="!filtered.length" class="empty-state compact">
        <b>没有匹配的文档</b>
        <p>换个关键词再试。</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-80">ID</th>
              <th>文档名称</th>
              <th class="w-120">字符数</th>
              <th>正文摘要</th>
              <th class="w-120 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in paged" :key="String(row.id)">
              <td>{{ row.id }}</td>
              <td>{{ documentName(row) }}</td>
              <td>{{ (row.content || '').length }}</td>
              <td class="cell-excerpt">{{ excerpt(row.content) }}</td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="openEdit(row)">编辑</button>
                  <button class="text-btn danger" @click="askDelete(row)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer v-if="showPager" class="card-foot">
        <span>第 {{ page }} 页 · 共 {{ total }} 篇</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1" @click="onPageChange(page - 1)">
            上一页
          </button>
          <button
            class="btn btn-sm"
            :disabled="page >= Math.ceil(total / pageSize)"
            @click="onPageChange(page + 1)"
          >
            下一页
          </button>
        </div>
      </footer>
    </section>

    <Modal
      v-model:open="editor.open"
      :title="editor.id == null ? '新增知识库文档' : `编辑文档 #${editor.id}`"
      width="760px"
    >
      <div class="editor-body">
        <input
          v-model="editor.name"
          class="input"
          placeholder="文档名称（如：第 5 章讲义）"
          :disabled="editor.id != null"
          aria-label="文档名称"
        />
        <textarea
          v-model="editor.content"
          class="input content-input"
          rows="14"
          placeholder="粘贴讲义、笔记或 FAQ 正文；保存后建议重建索引。"
          aria-label="文档正文"
        ></textarea>
        <p v-if="editor.error" class="form-error" role="alert">{{ editor.error }}</p>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="editor.open = false">取消</button>
        <button class="btn btn-primary" :disabled="busy" @click="saveDocument">
          {{ busy ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <Modal v-model:open="confirmState.open" :title="confirmState.title" width="460px">
      <p class="modal-text">{{ confirmState.text }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="confirmState.open = false">取消</button>
        <button class="btn btn-danger" :disabled="busy" @click="runConfirm">
          {{ busy ? '处理中…' : confirmState.confirmText }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import Stat from '../../components/Stat.vue'
import Modal from '../../components/Modal.vue'
import { useUi } from '../../composables/ui'
import { useClientPagination } from '@/composables/useClientPagination'
import { getAllCourses } from '@/api/course'
import {
  ragAddDocument,
  ragBuildCourseKnowledgeBase,
  ragBuildIndex,
  ragClearIndex,
  ragDeleteDocument,
  ragListDocuments,
  ragRetrieve,
  ragUpdateDocument,
} from '@/api/rag'
import { pickList, unwrap } from '@/utils/response'
import type { Metric } from '../../nav'

interface CourseOption {
  id?: number | string
  name?: string
  title?: string
}

interface DocumentRow {
  id?: number | string
  documentName?: string | null
  document_name?: string | null
  name?: string | null
  courseId?: number | string
  course_id?: number | string
  content?: string | null
}

interface RetrievalRow {
  documentId?: number | string
  document_id?: number | string
  documentName?: string | null
  document_name?: string | null
  relevance_score?: number | null
  rerank_score?: number | null
  snippet?: string | null
}

const { toast } = useUi()
const courses = ref<CourseOption[]>([])
const courseId = ref<number | string | null>(null)
const rows = ref<DocumentRow[]>([])
const loading = ref(false)
const listError = ref('')
const busy = ref(false)
const engineNote = ref('')
const keyword = ref('')
const lastBuild = ref('')
const retrieval = reactive({
  query: '',
  topK: 5,
  loading: false,
  error: '',
  results: [] as RetrievalRow[],
  attempted: false,
})

const filtered = computed(() => {
  const word = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    if (!word) return true
    return [documentName(row), row.content]
      .map((value) => String(value ?? '').toLowerCase())
      .some((value) => value.includes(word))
  })
})

const { page, pageSize, total, paged, showPager, onPageChange, reset } =
  useClientPagination<DocumentRow>(() => filtered.value, { pageSize: 15 })
watch(keyword, () => reset())

const metrics = computed<Metric[]>(() => {
  const chars = rows.value.reduce((sum, row) => sum + String(row.content ?? '').length, 0)
  return [
    { label: '知识库文档', value: rows.value.length, unit: '篇', note: '当前课程', icon: 'file' },
    { label: '正文字符', value: chars, unit: '字', note: '用于切分与向量化', icon: 'layers' },
    {
      label: '最近构建',
      value: lastBuild.value || '未构建',
      note: '本页操作读数',
      icon: 'refresh',
      tone: lastBuild.value ? 'ok' : 'warn',
    },
  ]
})

const editor = reactive({
  open: false,
  id: null as number | string | null,
  name: '',
  content: '',
  error: '',
})

const confirmState = reactive({
  open: false,
  title: '',
  text: '',
  confirmText: '确定',
  action: '' as 'delete' | 'clear' | '',
  row: null as DocumentRow | null,
})

function documentName(row: DocumentRow): string {
  return String(row.documentName || row.document_name || row.name || `文档 #${row.id}`)
}

function excerpt(value: unknown): string {
  const text = String(value ?? '')
    .replace(/\s+/g, ' ')
    .trim()
  return text.length <= 80 ? text || '（空）' : `${text.slice(0, 80)}…`
}

function scoreText(value: unknown): string {
  const num = Number(value)
  if (!Number.isFinite(num)) return '相关度 —'
  return `相关度 ${num.toFixed(3)}`
}

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

function isUnavailable(message: string): boolean {
  return message.includes('503') || message.includes('不可用') || message.includes('未启用')
}

async function loadCourses() {
  try {
    const payload = await getAllCourses()
    courses.value = Array.isArray(payload) ? payload : pickList<CourseOption>(payload)
    if (!courseId.value && courses.value.length) {
      courseId.value = courses.value[0].id ?? null
    }
  } catch (error) {
    toast(errorText(error, '课程列表加载失败'), 'danger')
  }
}

async function load() {
  if (!courseId.value) {
    rows.value = []
    return
  }
  loading.value = true
  listError.value = ''
  reset()
  try {
    const payload = unwrap<unknown>(await ragListDocuments(courseId.value))
    rows.value = pickList<DocumentRow>(payload)
  } catch (error) {
    listError.value = errorText(error, '知识库文档加载失败，请稍后重试')
    toast(listError.value, 'danger')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editor.open = true
  editor.id = null
  editor.name = ''
  editor.content = ''
  editor.error = ''
}

function openEdit(row: DocumentRow) {
  editor.open = true
  editor.id = row.id ?? null
  editor.name = documentName(row)
  editor.content = String(row.content ?? '')
  editor.error = ''
}

async function saveDocument() {
  if (busy.value) return
  editor.error = ''
  if (editor.id == null && !editor.name.trim()) {
    editor.error = '文档名称不能为空'
    return
  }
  if (!editor.content.trim()) {
    editor.error = '文档正文不能为空'
    return
  }
  busy.value = true
  try {
    if (editor.id == null) {
      await ragAddDocument(courseId.value!, editor.name.trim(), editor.content)
      toast('文档已保存，建议重建索引生效', 'ok')
    } else {
      await ragUpdateDocument(editor.id, editor.content)
      toast('文档已更新，建议重建索引生效', 'ok')
    }
    editor.open = false
    await load()
  } catch (error) {
    const message = errorText(error, '保存失败，请稍后重试')
    editor.error = message
    if (isUnavailable(message)) engineNote.value = message
    toast(message, 'danger')
  } finally {
    busy.value = false
  }
}

function askDelete(row: DocumentRow) {
  if (row.id == null) return
  confirmState.open = true
  confirmState.title = '删除知识库文档'
  confirmState.text = `确认删除「${documentName(row)}」？删除后需重建索引才会从检索中彻底移除。`
  confirmState.confirmText = '删除'
  confirmState.action = 'delete'
  confirmState.row = row
}

function askClear() {
  confirmState.open = true
  confirmState.title = '清空课程索引'
  confirmState.text = '清空后该课程问答将无法命中课程语料，但文档本身保留；需重新构建索引才能恢复。'
  confirmState.confirmText = '清空索引'
  confirmState.action = 'clear'
  confirmState.row = null
}

async function runConfirm() {
  if (busy.value) return
  busy.value = true
  try {
    if (confirmState.action === 'delete' && confirmState.row?.id != null) {
      await ragDeleteDocument(confirmState.row.id)
      toast('文档已删除', 'ok')
      confirmState.open = false
      await load()
    } else if (confirmState.action === 'clear') {
      await ragClearIndex(courseId.value!)
      lastBuild.value = ''
      toast('索引已清空（文档保留）', 'ok')
      confirmState.open = false
    }
  } catch (error) {
    const message = errorText(error, '操作失败，请稍后重试')
    toast(message, 'danger')
    if (isUnavailable(message)) engineNote.value = message
  } finally {
    busy.value = false
  }
}

async function buildIndex() {
  if (busy.value || !courseId.value) return
  busy.value = true
  try {
    const payload = unwrap<Record<string, unknown>>(await ragBuildIndex(courseId.value))
    const indexed = payload?.indexed_chunks ?? payload?.indexedChunks
    lastBuild.value = indexed != null ? `${String(indexed)} 片段` : '已构建'
    engineNote.value = ''
    toast(indexed != null ? `索引已重建（${String(indexed)} 个片段）` : '索引已重建', 'ok')
  } catch (error) {
    const message = errorText(error, '索引构建失败，请稍后重试')
    engineNote.value = message
    toast(message, 'danger')
  } finally {
    busy.value = false
  }
}

async function runRetrieve() {
  if (!courseId.value || !retrieval.query.trim() || retrieval.loading) return
  retrieval.loading = true
  retrieval.error = ''
  retrieval.results = []
  retrieval.attempted = true
  try {
    const payload = unwrap<Record<string, unknown>>(
      await ragRetrieve({
        query: retrieval.query.trim(),
        courseId: courseId.value,
        topK: retrieval.topK,
      }),
    )
    retrieval.results = pickList<RetrievalRow>(payload?.results)
    if (String(payload?.mode ?? '') === 'UNAVAILABLE') {
      retrieval.error = String(payload?.error || 'RAG 引擎不可用，无法检索')
    }
  } catch (error) {
    const message = errorText(error, '检索失败，请稍后重试')
    retrieval.error = message
    if (isUnavailable(message)) engineNote.value = message
  } finally {
    retrieval.loading = false
  }
}

async function buildFromCourse() {
  if (busy.value || !courseId.value) return
  busy.value = true
  try {
    const payload = unwrap<Record<string, unknown>>(
      await ragBuildCourseKnowledgeBase(courseId.value),
    )
    const mode = String(payload?.mode ?? '')
    if (mode === 'UNAVAILABLE') {
      const note = String(payload?.error || payload?.note || 'RAG 引擎不可用，未生成课程知识库')
      engineNote.value = note
      toast(note, 'danger')
      return
    }
    const added = payload?.added
    const skipped = payload?.skipped
    const chunks = payload?.indexedChunks ?? payload?.indexed_chunks
    lastBuild.value = chunks != null ? `${String(chunks)} 片段` : '已构建'
    toast(
      `课程知识库已构建${added != null ? `，新增 ${String(added)} 篇` : ''}${
        skipped != null ? `，跳过 ${String(skipped)} 篇` : ''
      }${chunks != null ? `，索引 ${String(chunks)} 片段` : ''}`,
      'ok',
    )
    engineNote.value = ''
    await load()
  } catch (error) {
    const message = errorText(error, '课程知识库构建失败，请稍后重试')
    engineNote.value = message
    toast(message, 'danger')
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  await loadCourses()
  await load()
})
</script>

<style scoped>
.rag-panel {
  display: grid;
  gap: 14px;
}
.scope-card {
  display: grid;
  gap: 8px;
}
.scope-row {
  align-items: flex-end;
}
.course-pick {
  width: 220px;
  height: 30px;
}
.scope-note {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.search-input {
  width: 220px;
  height: 30px;
}
.editor-body {
  display: grid;
  gap: 10px;
}
.content-input {
  resize: vertical;
  font-family: inherit;
}
.retrieval-input {
  flex: 1;
  min-width: 260px;
}
.retrieval-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}
.retrieval-item {
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
  padding: 10px 12px;
}
.retrieval-item header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.retrieval-item p {
  margin: 6px 0 0;
  color: var(--text-3);
  font-size: 12px;
  line-height: 1.6;
}
.cell-excerpt {
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-3);
  font-size: 12px;
}
</style>
