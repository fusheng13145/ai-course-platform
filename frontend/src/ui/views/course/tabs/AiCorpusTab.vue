<template>
  <div class="corpus-tab">
    <div class="course-tab-bar">
      <span class="tab-hint">
        语料来自本课程的知识库文档；构建索引后 AI 助教问答才可命中课程资料（mode=ENGINE）。
      </span>
      <div class="row-actions">
        <button class="btn btn-sm" :disabled="busy" @click="load()">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
        <button class="btn btn-sm btn-primary" :disabled="busy" @click="openComposer()">
          新增文档
        </button>
      </div>
    </div>

    <div v-if="engineNote" class="callout" role="alert">
      <span>ENGINE / 引擎状态</span>
      <p>{{ engineNote }}</p>
    </div>

    <div v-if="composerOpen" class="card composer">
      <header class="card-head">
        <div>
          <p class="card-kicker">CORPUS / DOCUMENT</p>
          <h2>{{ editingId == null ? '新增文档' : `编辑文档 #${editingId}` }}</h2>
        </div>
        <button class="text-btn" @click="closeComposer()">取消</button>
      </header>
      <div class="composer-body">
        <input
          v-model="form.name"
          class="input"
          placeholder="文档名称（如：第 5 章讲义）"
          :disabled="editingId != null"
          aria-label="文档名称"
        />
        <textarea
          v-model="form.content"
          class="input content-input"
          rows="8"
          placeholder="粘贴文档正文（讲义、笔记、FAQ 等；将按段落切分并向量化）"
          aria-label="文档内容"
        ></textarea>
        <div class="composer-actions">
          <span v-if="formError" class="form-error" role="alert">{{ formError }}</span>
          <button class="btn btn-primary btn-sm" :disabled="busy" @click="submitDocument()">
            {{ busy ? '保存中…' : editingId == null ? '保存文档' : '保存修改' }}
          </button>
        </div>
      </div>
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">CORPUS / DOCUMENTS</p>
          <h2>
            知识库文档 <span class="chip chip-mute">{{ rows.length }} 篇</span>
          </h2>
        </div>
        <div class="filter-row">
          <button class="btn btn-sm" :disabled="busy || !rows.length" @click="buildIndex()">
            {{ busy ? '处理中…' : '重建索引' }}
          </button>
          <button
            class="btn btn-sm btn-ghost"
            :disabled="busy || !rows.length"
            @click="clearIndex()"
          >
            清空索引
          </button>
        </div>
      </header>

      <div v-if="rows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>文档</th>
              <th class="w-120">内容摘要</th>
              <th class="w-96">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="String(row.id)">
              <td>
                <b>{{ row.documentName || row.document_name || row.name || `文档 #${row.id}` }}</b>
                <small class="cell-sub">文档 ID {{ row.id }}</small>
              </td>
              <td class="cell-excerpt">{{ excerpt(row.content) }}</td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" :disabled="busy" @click="openEditor(row)">编辑</button>
                  <button class="text-btn danger" :disabled="busy" @click="removeDocument(row)">
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact">
        <b>正在加载知识库文档…</b>
      </div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="file" /></span>
        <b>{{ error ? '知识库文档加载失败' : '本课程还没有语料文档' }}</b>
        <p>{{ error || '新增文档并重建索引后，AI 助教问答即可命中课程资料。' }}</p>
      </div>
      <p class="hint">
        引擎未接入或不可达时列表/索引操作按契约返回 503 降级提示（此处如实透出，不伪装成功）。
      </p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import Icon from '../../../components/Icon.vue'
import { useUi } from '../../../composables/ui'
import {
  ragAddDocument,
  ragBuildIndex,
  ragClearIndex,
  ragDeleteDocument,
  ragListDocuments,
  ragUpdateDocument,
} from '@/api/rag'
import { pickList, unwrap } from '@/utils/response'

/**
 * 教师侧「AI 语料」页签（2026-11-07 P2 批次：语料自助管理闭环）。
 * api/rag.ts 的文档六端点此前零消费（语料只能靠 seed 脚本重放）；本页补齐教师自助链路：
 * 增删改查文档 + 重建/清空向量索引。权限与降级语义由后端把守（assertCanManageCourse / 503）。
 */
const props = defineProps<{ courseId: number | string }>()

const { toast } = useUi()

interface CorpusRow {
  id?: number | string
  documentName?: string | null
  document_name?: string | null
  name?: string | null
  content?: string | null
}

const rows = ref<CorpusRow[]>([])
const loading = ref(false)
const error = ref('')
const busy = ref(false)
const composerOpen = ref(false)
const editingId = ref<number | string | null>(null)
const formError = ref('')
const form = reactive({ name: '', content: '' })
const engineNote = ref('')

function excerpt(content?: string | null): string {
  const text = (content || '').replace(/\s+/g, ' ').trim()
  return text.length <= 60 ? text || '（空）' : `${text.slice(0, 60)}…`
}

function errMsg(e: unknown, fallback: string): string {
  const resp = (e as { response?: { data?: { message?: string } } })?.response
  return resp?.data?.message || fallback
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = unwrap<Record<string, unknown>>(await ragListDocuments(props.courseId))
    rows.value = pickList(data).map((row) => row as CorpusRow)
  } catch (e) {
    rows.value = []
    error.value = errMsg(e, '知识库文档加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function openComposer() {
  editingId.value = null
  form.name = ''
  form.content = ''
  formError.value = ''
  composerOpen.value = true
}

function openEditor(row: CorpusRow) {
  editingId.value = row.id ?? null
  form.name = String(row.documentName || row.document_name || row.name || '')
  form.content = String(row.content || '')
  formError.value = ''
  composerOpen.value = true
}

function closeComposer() {
  composerOpen.value = false
  editingId.value = null
}

async function submitDocument() {
  if (busy.value) return
  formError.value = ''
  if (editingId.value == null && !form.name.trim()) {
    formError.value = '文档名称不能为空'
    return
  }
  if (!form.content.trim()) {
    formError.value = '文档内容不能为空'
    return
  }
  busy.value = true
  try {
    if (editingId.value == null) {
      await ragAddDocument(props.courseId, form.name.trim(), form.content)
      toast('文档已保存，建议重建索引生效', 'ok')
    } else {
      await ragUpdateDocument(editingId.value, form.content)
      toast('文档已更新，建议重建索引生效', 'ok')
    }
    closeComposer()
    await load()
  } catch (e) {
    const message = errMsg(e, '保存失败，请稍后重试')
    toast(message, 'danger')
    if (message.includes('503') || message.includes('不可用') || message.includes('未启用')) {
      engineNote.value = message
    }
  } finally {
    busy.value = false
  }
}

async function removeDocument(row: CorpusRow) {
  if (busy.value || row.id == null) return
  busy.value = true
  try {
    await ragDeleteDocument(row.id)
    toast('文档已删除', 'ok')
    await load()
  } catch (e) {
    toast(errMsg(e, '删除失败，请稍后重试'), 'danger')
  } finally {
    busy.value = false
  }
}

async function buildIndex() {
  if (busy.value) return
  busy.value = true
  try {
    const data = (unwrap<Record<string, unknown>>(await ragBuildIndex(props.courseId)) ||
      {}) as Record<string, unknown>
    const indexed = data.indexed_chunks ?? data.indexedChunks
    toast(indexed != null ? `索引已重建（${String(indexed)} 个片段）` : '索引已重建', 'ok')
    engineNote.value = ''
  } catch (e) {
    const message = errMsg(e, '索引构建失败，请稍后重试')
    toast(message, 'danger')
    engineNote.value = message
  } finally {
    busy.value = false
  }
}

async function clearIndex() {
  if (busy.value) return
  busy.value = true
  try {
    await ragClearIndex(props.courseId)
    toast('索引已清空（语料文档保留）', 'ok')
  } catch (e) {
    toast(errMsg(e, '索引清空失败，请稍后重试'), 'danger')
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.corpus-tab {
  display: grid;
  gap: 12px;
}
.course-tab-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.tab-hint {
  font-size: 12px;
  color: var(--text-3);
}
.row-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.composer-body {
  display: grid;
  gap: 8px;
}
.content-input {
  resize: vertical;
  font-family: inherit;
}
.composer-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}
.form-error {
  color: var(--danger, #dc2626);
  font-size: 12px;
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
