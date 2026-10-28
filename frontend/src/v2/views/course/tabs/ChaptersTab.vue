<template>
  <section class="chapters">
    <div class="course-tab-bar">
      <div>
        <p class="card-kicker">COURSE / CHAPTERS</p>
        <h2>
          章节结构 <span class="chip chip-mute">{{ flatCount }} 节</span>
        </h2>
      </div>
      <div class="course-tab-actions">
        <button class="btn btn-ghost" @click="openImport">批量导入</button>
        <button class="btn btn-primary" @click="openCreate">新增顶级章节</button>
      </div>
    </div>

    <div v-if="loadError" class="callout">
      <span>ERROR / 加载失败</span>
      <p>{{ loadError }}</p>
    </div>

    <section class="card">
      <div v-if="tree.length" class="chapter-tree">
        <article v-for="node in tree" :key="rowKey(node, 0)" class="ct-node">
          <div class="ct-row" @click="toggle(node.id)">
            <span class="ct-caret" :class="{ 'is-open': isOpen(node.id) }"
              ><V2Icon name="chevron"
            /></span>
            <b class="ct-title">{{ node.title || `章节 #${node.id}` }}</b>
            <span class="ct-meta"
              >任务点 {{ node.taskCount || 0 }} · 完成率 {{ pct(node.completionRate) }}</span
            >
            <span class="chip" :class="node.isPublished ? 'chip-ok' : 'chip-mute'">
              {{ node.isPublished ? '已发布' : '草稿' }}
            </span>
            <span class="row-actions" @click.stop>
              <button class="text-btn" @click="openEdit(node)">编辑</button>
              <button class="text-btn danger" @click="askRemove(node)">删除</button>
            </span>
          </div>

          <div v-if="isOpen(node.id) && node.children?.length" class="ct-children">
            <div v-for="child in node.children" :key="rowKey(child, 1)" class="ct-child">
              <div class="ct-row ct-row--child">
                <span class="ct-title ct-title--child">{{
                  child.title || `小节 #${child.id}`
                }}</span>
                <span class="ct-meta"
                  >任务点 {{ child.taskCount || 0 }} · 完成率 {{ pct(child.completionRate) }}</span
                >
                <span class="chip" :class="child.isPublished ? 'chip-ok' : 'chip-mute'">
                  {{ child.isPublished ? '已发布' : '草稿' }}
                </span>
                <span class="row-actions" @click.stop>
                  <button class="text-btn" @click="openEdit(child)">编辑</button>
                  <button class="text-btn danger" @click="askRemove(child)">删除</button>
                </span>
              </div>
              <div v-if="child.taskPoints?.length" class="ct-tags">
                <span
                  v-for="point in child.taskPoints"
                  :key="rowKey(point, 2)"
                  class="chip chip-info"
                >
                  {{ point.title || `任务点 #${point.id}` }}
                  <em>{{ point.taskType || '' }}</em>
                </span>
              </div>
            </div>
          </div>
        </article>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载章节…</b></div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><V2Icon name="book" /></span>
        <b>{{ loadError ? '章节未能加载' : '暂无章节' }}</b>
        <p>
          {{ loadError ? '请查看上方提示后重试。' : '点击右上角「新增顶级章节」开始搭建大纲。' }}
        </p>
      </div>
    </section>

    <!-- 新增 / 编辑章节 -->
    <V2Modal
      v-model:open="dialogOpen"
      :title="editingId ? '编辑章节' : '新增章节'"
      kicker="CHAPTER / FORM"
      width="600px"
    >
      <div class="form-grid">
        <label class="field span-2">
          <span>标题 *</span>
          <input v-model="form.title" class="input" placeholder="如：第 3 章 网络层" />
        </label>
        <label class="field">
          <span>排序序号</span>
          <input v-model.number="form.orderIndex" class="input" type="number" min="0" />
        </label>
        <label class="field">
          <span>时长（分钟）</span>
          <input v-model.number="form.duration" class="input" type="number" min="0" />
        </label>
        <label class="field span-2">
          <span>章节内容</span>
          <textarea
            v-model="form.content"
            class="textarea"
            rows="4"
            placeholder="章节简介或讲义要点"
          ></textarea>
        </label>
      </div>
      <p v-if="formError" class="form-error">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="dialogOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </V2Modal>

    <!-- 批量导入 -->
    <V2Modal v-model:open="importOpen" title="批量导入章节" kicker="CHAPTER / IMPORT" width="560px">
      <p class="modal-text">上传 .xlsx 一次性建立课程大纲；父章节填标题即可形成二级章节。</p>
      <ul class="import-notes">
        <li v-for="note in IMPORT_NOTES" :key="note">{{ note }}</li>
      </ul>
      <input ref="fileInput" class="file-hidden" type="file" accept=".xlsx" @change="pickFile" />
      <div class="cover-row">
        <button class="btn btn-ghost" :disabled="importing" @click="chooseFile">
          {{ importing ? '导入中…' : '选择文件' }}
        </button>
        <span class="cover-name">{{ fileName || '未选择文件' }}</span>
      </div>
      <div v-if="importResult" class="callout">
        <span>RESULT</span>
        <p>{{ importResult }}</p>
      </div>
      <p v-if="importError" class="form-error">{{ importError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="importOpen = false">关闭</button>
        <button class="btn btn-primary" :disabled="importing || !fileName" @click="runImport">
          开始导入
        </button>
      </template>
    </V2Modal>

    <!-- 删除确认 -->
    <V2Modal v-model:open="confirm.open" title="删除章节" kicker="CONFIRM / DELETE" width="460px">
      <p class="modal-text">{{ confirmText }}</p>
      <p class="modal-hint">其子章节与任务点将一并移除，删除后不可恢复。</p>
      <template #footer>
        <button class="btn btn-ghost" @click="confirm.open = false">取消</button>
        <button class="btn btn-danger" :disabled="confirm.busy" @click="runRemove">确定删除</button>
      </template>
    </V2Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import V2Modal from '../../../components/V2Modal.vue'
import V2Icon from '../../../components/V2Icon.vue'
import { useV2Ui } from '../../../composables/ui'
import chapterApi from '@/api/chapter'
import teachingApi from '@/api/teacher'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'

interface TaskPoint {
  id?: number | string
  title?: string
  taskType?: string
}

interface ChapterNode {
  id: number | string
  title?: string
  content?: string
  orderIndex?: number
  duration?: number
  isPublished?: boolean
  taskCount?: number
  completionRate?: number | string
  taskPoints?: TaskPoint[]
  children?: ChapterNode[]
}

const IMPORT_NOTES = [
  '仅「章节标题」必填，为空的行会被跳过',
  '父章节标题填写已有章节名即可建立二级章节（可留空表示顶级）',
  '「是否发布」填 是/否，留空默认发布',
  '同一课程下标题重复的行会被跳过',
]

const props = defineProps<{ courseId: number }>()
const { toast } = useV2Ui()

const tree = ref<ChapterNode[]>([])
const loading = ref(false)
const loadError = ref('')
const openSet = reactive(new Set<number | string>())

const dialogOpen = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | string | null>(null)
const saving = ref(false)
const formError = ref('')
const form = reactive({ title: '', content: '', orderIndex: 0, duration: 0 })

const importOpen = ref(false)
const importing = ref(false)
const importError = ref('')
const importResult = ref('')
const fileName = ref('')
const pickedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const confirm = reactive({ open: false, node: null as ChapterNode | null, busy: false })

const flatCount = computed(() =>
  tree.value.reduce((sum, node) => sum + 1 + (node.children?.length || 0), 0),
)
const confirmText = computed(() => `确定删除章节「${confirm.node?.title || ''}」？`)

function rowKey(node: { id?: number | string }, index: number): string {
  return String(node.id ?? index)
}

function pct(value?: number | string): string {
  if (value === null || value === undefined || value === '') return '—'
  const number = Number(value)
  if (!Number.isFinite(number)) return String(value)
  return `${Math.round(number > 0 && number <= 1.0001 ? number * 100 : number)}%`
}

function isOpen(id: number | string): boolean {
  return openSet.has(id)
}

function toggle(id: number | string) {
  if (openSet.has(id)) openSet.delete(id)
  else openSet.add(id)
}

async function load() {
  if (!props.courseId) return
  loading.value = true
  loadError.value = ''
  try {
    const body = unwrap(await teachingApi.getChapterTree(props.courseId))
    const data = body as { records?: ChapterNode[]; list?: ChapterNode[] }
    const list = (data?.records ||
      data?.list ||
      (Array.isArray(data) ? (data as ChapterNode[]) : [])) as ChapterNode[]
    tree.value = list
    list.forEach((node) => openSet.add(node.id))
  } catch (error) {
    logger.error('加载章节树失败', error)
    tree.value = []
    loadError.value = '章节加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.title = ''
  form.content = ''
  form.orderIndex = 0
  form.duration = 0
  editingId.value = null
}

function openCreate() {
  formError.value = ''
  dialogMode.value = 'create'
  resetForm()
  dialogOpen.value = true
}

function openEdit(node: ChapterNode) {
  formError.value = ''
  dialogMode.value = 'edit'
  editingId.value = node.id
  form.title = node.title || ''
  form.content = node.content || ''
  form.orderIndex = node.orderIndex ?? 0
  form.duration = node.duration ?? 0
  dialogOpen.value = true
}

async function save() {
  if (!form.title.trim()) {
    formError.value = '请填写章节标题'
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      content: form.content,
      orderIndex: form.orderIndex,
      duration: form.duration,
    }
    if (dialogMode.value === 'create') {
      await chapterApi.createChapter({ courseId: props.courseId, ...payload })
      toast('章节已创建', 'ok')
    } else {
      await chapterApi.updateChapter(editingId.value as number | string, payload)
      toast('章节已更新', 'ok')
    }
    dialogOpen.value = false
    await load()
  } catch (error) {
    formError.value = '保存失败，请稍后重试'
    logger.error('保存章节失败', error)
  } finally {
    saving.value = false
  }
}

function askRemove(node: ChapterNode) {
  confirm.node = node
  confirm.open = true
}

async function runRemove() {
  if (!confirm.node) return
  confirm.busy = true
  try {
    await chapterApi.deleteChapter(confirm.node.id)
    toast('章节已删除', 'ok')
    confirm.open = false
    confirm.node = null
    await load()
  } catch (error) {
    toast('删除失败，请稍后重试', 'danger')
    logger.error('删除章节失败', error)
  } finally {
    confirm.busy = false
  }
}

function openImport() {
  importError.value = ''
  importResult.value = ''
  fileName.value = ''
  pickedFile.value = null
  importOpen.value = true
}

function chooseFile() {
  const input = fileInput.value
  if (!input) return
  input.value = ''
  input.click()
}

function pickFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  fileName.value = file.name
  pickedFile.value = file
  importError.value = ''
  input.value = ''
}

async function runImport() {
  if (!pickedFile.value) return
  importing.value = true
  importError.value = ''
  importResult.value = ''
  try {
    const body = (unwrap(await chapterApi.importChaptersExcel(props.courseId, pickedFile.value)) ||
      {}) as { importedCount?: number; skippedCount?: number; failedCount?: number }
    importResult.value = `导入 ${body.importedCount ?? 0} 节，跳过 ${body.skippedCount ?? 0} 行，失败 ${body.failedCount ?? 0} 行`
    if (body.importedCount) {
      await load()
    } else {
      toast('没有章节被导入，请检查文件内容', 'warn')
    }
  } catch (error) {
    importError.value = '导入失败，请检查文件格式（.xlsx，6 列表头）'
    logger.error('导入章节失败', error)
  } finally {
    importing.value = false
  }
}

watch(() => props.courseId, load, { immediate: true })
</script>

<style scoped>
.chapters {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.chapter-tree {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ct-node {
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface-2);
  overflow: hidden;
}
.ct-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
}
.ct-row:hover {
  background: var(--surface-3);
}
.ct-row--child {
  cursor: default;
  border-top: 1px dashed var(--line);
}
.ct-row--child:hover {
  background: none;
}
.ct-caret {
  display: inline-flex;
  transform: rotate(0deg);
  transition: transform 0.18s ease;
  color: var(--ink-3);
}
.ct-caret.is-open {
  transform: rotate(90deg);
}
.ct-title {
  font-weight: 600;
  font-size: 13px;
}
.ct-title--child {
  font-weight: 550;
  font-size: 12.5px;
}
.ct-meta {
  flex: 1;
  min-width: 0;
  font-size: 10.5px;
  color: var(--ink-3);
}
.ct-children {
  padding-left: 18px;
}
.ct-child {
  border-top: 1px solid var(--line);
}
.ct-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 12px 10px 12px;
}
.ct-tags em {
  font-style: normal;
  opacity: 0.7;
}
.import-notes {
  margin: 0 0 12px;
  padding-left: 18px;
  font-size: 11px;
  line-height: 1.8;
  color: var(--ink-3);
}
.file-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}
.cover-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cover-name {
  font-size: 11px;
  color: var(--ink-3);
}
</style>
