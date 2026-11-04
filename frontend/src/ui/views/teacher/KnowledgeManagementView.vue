<template>
  <section class="module">
    <PageHead
      kicker="TEACHER / KNOWLEDGE · 2026 秋"
      title="知识管理"
      sub="维护课程知识点结构、层级与前置依赖，支持批量导入。"
      source="api"
    >
      <template #actions>
        <select v-model="courseId" class="input course-pick" aria-label="选择课程">
          <option :value="null">选择课程</option>
          <option v-for="course in courses" :key="course.id" :value="course.id">
            {{ course.name || course.title }}
          </option>
        </select>
        <button class="btn btn-ghost" :disabled="!courseId" @click="openImport">批量导入</button>
        <button class="btn btn-primary" @click="openCreate">新建知识点</button>
      </template>
    </PageHead>

    <div v-if="loadError" class="callout">
      <span>ERROR / 加载失败</span>
      <p>{{ loadError }}</p>
    </div>

    <div class="km-grid">
      <section class="card">
        <header class="card-head">
          <div>
            <p class="card-kicker">KNOWLEDGE / TREE</p>
            <h2>知识树</h2>
          </div>
          <button class="text-btn" :disabled="!flatTree.length" @click="expanded = !expanded">
            {{ expanded ? '收起全部' : '展开全部' }}
          </button>
        </header>
        <ul v-if="flatTree.length" class="tree-list">
          <li
            v-for="(node, index) in visibleTree"
            :key="rowKey(node, index)"
            :style="{ paddingLeft: `${node.level * 14}px` }"
          >
            <span class="t-bullet">{{ node.children ? '·' : '—' }}</span>
            <span class="t-name">{{ node.name }}</span>
            <span class="chip" :class="difficultyChip(node.difficulty)">
              {{ difficultyLabel(node.difficulty) }}
            </span>
          </li>
        </ul>
        <div v-else-if="treeLoading" class="empty-state compact"><b>正在加载知识树…</b></div>
        <div v-else class="empty-state compact">
          <b>{{
            treeError ? '知识树未能加载' : courseId ? '该课程还没有知识点' : '请先选择课程'
          }}</b>
          <p>{{ treeError ? '请查看上方提示后重试。' : '新建知识点后会自动形成层级。' }}</p>
        </div>
      </section>

      <section class="card table-card">
        <header class="card-head">
          <div>
            <p class="card-kicker">KNOWLEDGE / POINTS</p>
            <h2>
              知识点列表 <span class="chip chip-mute">{{ filteredList.length }}</span>
            </h2>
          </div>
          <input
            v-model="searchText"
            class="input km-search"
            type="search"
            placeholder="搜索知识点…"
            @keyup.enter="loadAllData"
          />
        </header>

        <div v-if="filteredList.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>名称</th>
                <th class="w-96">难度</th>
                <th class="w-80">重要度</th>
                <th class="w-220">描述</th>
                <th class="w-220">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in filteredList" :key="rowKey(row, index)">
                <td>
                  <b class="k-name">{{ row.name || `知识点 #${row.id}` }}</b>
                </td>
                <td>
                  <span class="chip" :class="difficultyChip(row.difficulty)">
                    {{ difficultyLabel(row.difficulty) }}
                  </span>
                </td>
                <td>{{ row.importance ?? '—' }}</td>
                <td>
                  <span class="k-desc">{{ row.description || '—' }}</span>
                </td>
                <td>
                  <div class="row-actions">
                    <button class="text-btn" @click="openEdit(row)">编辑</button>
                    <button class="text-btn" @click="openRelation(row)">关联</button>
                    <button class="text-btn danger" @click="askDelete(row)">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else-if="tableLoading" class="empty-state compact"><b>正在加载知识点…</b></div>
        <div v-else class="empty-state compact">
          <span class="e-ico"><Icon name="path" /></span>
          <b>{{
            listError ? '知识点列表未能加载' : courseId ? '暂无知识点数据' : '请先选择课程'
          }}</b>
          <p>
            {{
              listError
                ? '请查看上方提示后重试。'
                : '可新建单个知识点，或用「批量导入」一次建结构。'
            }}
          </p>
        </div>
      </section>
    </div>

    <!-- 新建 / 编辑知识点 -->
    <Modal
      v-model:open="formOpen"
      :title="editingId ? '编辑知识点' : '新建知识点'"
      kicker="KNOWLEDGE / FORM"
      width="620px"
    >
      <div class="form-grid">
        <label class="field span-2">
          <span>知识点名称 *</span>
          <input v-model="form.name" class="input" placeholder="如：子网划分与 CIDR" />
        </label>
        <label class="field">
          <span>所属课程 *</span>
          <select v-model="form.courseId" class="input">
            <option :value="null">请选择课程</option>
            <option v-for="course in courses" :key="course.id" :value="course.id">
              {{ course.name || course.title }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>父知识点</span>
          <select v-model="form.parentId" class="input">
            <option :value="null">无（顶级）</option>
            <option v-for="option in parentOptions" :key="option.id" :value="option.id">
              {{ option.name }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>难度 *</span>
          <select v-model="form.difficulty" class="input">
            <option value="EASY">简单</option>
            <option value="MEDIUM">中等</option>
            <option value="HARD">困难</option>
          </select>
        </label>
        <label class="field">
          <span>重要程度（1-10）</span>
          <input v-model.number="form.importance" class="input" type="number" min="1" max="10" />
        </label>
        <label class="field span-2">
          <span>描述</span>
          <textarea
            v-model="form.description"
            class="textarea"
            rows="3"
            placeholder="可选描述"
          ></textarea>
        </label>
      </div>
      <p v-if="formError" class="form-error">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="formOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="submitting" @click="submitForm">
          {{ submitting ? '提交中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <!-- 前置依赖 -->
    <Modal
      v-model:open="relationOpen"
      :title="relationTarget ? `前置依赖 · ${relationTarget.name || ''}` : '前置依赖'"
      kicker="KNOWLEDGE / RELATIONS"
      width="560px"
    >
      <div class="relation-row">
        <select v-model="newPrereqId" class="input">
          <option :value="null">选择前置知识点</option>
          <option v-for="option in availablePrereqs" :key="option.id" :value="option.id">
            {{ option.name }}
          </option>
        </select>
        <button class="btn btn-ghost" :disabled="!newPrereqId" @click="addPrerequisite">
          添加
        </button>
      </div>
      <ul v-if="prerequisites.length" class="prereq-list">
        <li v-for="(item, index) in prerequisites" :key="rowKey(item, index)">
          <span class="t-name">{{ item.name || `知识点 #${item.id}` }}</span>
          <button class="text-btn danger" @click="removePrerequisite(item)">移除</button>
        </li>
      </ul>
      <div v-else class="empty-state compact"><b>暂无前置依赖</b></div>
      <template #footer>
        <button class="btn btn-ghost" @click="relationOpen = false">关闭</button>
      </template>
    </Modal>

    <!-- 批量导入 -->
    <Modal
      v-model:open="importOpen"
      title="批量导入知识点"
      kicker="KNOWLEDGE / IMPORT"
      width="560px"
    >
      <p class="modal-text">上传 .xlsx 一次性建立知识点结构；父知识点填名称即可形成层级。</p>
      <ul class="import-notes">
        <li v-for="note in IMPORT_NOTES" :key="note">{{ note }}</li>
      </ul>
      <input
        ref="fileInput"
        class="file-hidden"
        type="file"
        accept=".xlsx"
        @change="handleImport"
      />
      <div class="cover-row">
        <button class="btn btn-ghost" :disabled="importing" @click="pickFile">
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
    </Modal>

    <!-- 删除确认 -->
    <Modal v-model:open="confirm.open" title="删除知识点" kicker="CONFIRM / DELETE" width="460px">
      <p class="modal-text">{{ confirmText }}</p>
      <p class="modal-hint">其子节点也将被一并删除，删除后不可恢复。</p>
      <template #footer>
        <button class="btn btn-ghost" @click="confirm.open = false">取消</button>
        <button class="btn btn-danger" :disabled="confirm.busy" @click="runDelete">确定删除</button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import PageHead from '../../components/PageHead.vue'
import Modal from '../../components/Modal.vue'
import Icon from '../../components/Icon.vue'
import { useUi } from '../../composables/ui'
import knowledgeApi from '@/api/knowledge'
import knowledgeRelationApi from '@/api/knowledgePointRelation'
import { getAllMyCourses } from '@/api/course'
import { unwrap, pickList } from '@/utils/response'
import logger from '@/utils/logger'

interface Row {
  id?: number | string
  name?: string
  difficulty?: string
  importance?: number
  description?: string
  parentId?: number | string
  courseId?: number | string
  children?: Row[]
  [key: string]: unknown
}

interface KpForm {
  name: string
  courseId: number | string | null
  parentId: number | string | null
  difficulty: string
  importance: number
  description: string
}

const DIFFICULTY_LABEL: Record<string, string> = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
const IMPORT_NOTES = [
  '仅「知识点名称」必填，为空的行会被跳过',
  '父知识点名称填写已有知识点名即可建立层级（可留空表示顶级）',
  '难度填 EASY / MEDIUM / HARD；重要程度 1-10，留空默认 5',
]

const { toast } = useUi()

const courses = ref<Row[]>([])
const courseId = ref<number | string | null>(null)
const searchText = ref('')

const knowledgeList = ref<Row[]>([])
const knowledgeTree = ref<Row[]>([])
const treeLoading = ref(false)
const tableLoading = ref(false)
const treeError = ref('')
const listError = ref('')
const expanded = ref(true)

const formOpen = ref(false)
const formError = ref('')
const submitting = ref(false)
const editingId = ref<number | string | null>(null)
const form = reactive<KpForm>({
  name: '',
  courseId: null,
  parentId: null,
  difficulty: 'MEDIUM',
  importance: 2,
  description: '',
})

const relationOpen = ref(false)
const relationTarget = ref<Row | null>(null)
const prerequisites = ref<Row[]>([])
const newPrereqId = ref<number | string | null>(null)

const importOpen = ref(false)
const importing = ref(false)
const importError = ref('')
const importResult = ref('')
const fileName = ref('')
const pickedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const confirm = reactive({ open: false, row: null as Row | null, busy: false })

const loadError = computed(() => treeError.value || listError.value)
const filteredList = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword) return knowledgeList.value
  return knowledgeList.value.filter((row) =>
    String(row.name || '')
      .toLowerCase()
      .includes(keyword),
  )
})
const parentOptions = computed(() =>
  knowledgeList.value.filter((row) => Number(row.id) !== Number(editingId.value)),
)
const availablePrereqs = computed(() =>
  knowledgeList.value.filter(
    (row) =>
      Number(row.id) !== Number(relationTarget.value?.id) &&
      !prerequisites.value.some((item) => Number(item.id) === Number(row.id)),
  ),
)
const confirmText = computed(() => `确定删除知识点「${confirm.row?.name ?? ''}」？`)

interface FlatNode {
  id: number | string | undefined
  name: string | undefined
  difficulty: string | undefined
  level: number
  children: FlatNode[]
}

function flatten(nodes: Row[], level = 0): FlatNode[] {
  return (nodes || []).flatMap((node) => {
    const children = Array.isArray(node.children) ? (node.children as Row[]) : []
    const self: FlatNode = {
      id: node.id,
      name: node.name,
      difficulty: node.difficulty,
      level,
      children: flatten(children, level + 1),
    }
    return [self, ...self.children]
  })
}

const flatTree = computed<FlatNode[]>(() => flatten(knowledgeTree.value as Row[]))
const visibleTree = computed(() => {
  if (expanded.value) return flatTree.value
  return flatTree.value.filter((node) => node.level === 0)
})

function rowKey(row: { id?: number | string }, index: number): string {
  return String(row.id ?? index)
}

function difficultyLabel(difficulty: unknown): string {
  return DIFFICULTY_LABEL[String(difficulty)] || '—'
}

function difficultyChip(difficulty: unknown): string {
  const map: Record<string, string> = { EASY: 'chip-ok', MEDIUM: 'chip-warn', HARD: 'chip-danger' }
  return map[String(difficulty)] || 'chip-mute'
}

function rowsOf(body: unknown): Row[] {
  return pickList<Row>(body)
}

async function loadCourses() {
  try {
    courses.value = (await getAllMyCourses()) as Row[]
  } catch (error) {
    logger.error('加载教师课程失败', error)
    courses.value = []
  }
}

async function loadKnowledgeTree() {
  if (!courseId.value) {
    knowledgeTree.value = []
    return
  }
  treeLoading.value = true
  treeError.value = ''
  try {
    const tree = rowsOf(unwrap(await knowledgeApi.getTree(courseId.value)))
    if (tree.length) {
      knowledgeTree.value = tree
    } else {
      const points = rowsOf(unwrap(await knowledgeApi.getKnowledgePoints(courseId.value)))
      knowledgeTree.value = buildTree(points)
    }
  } catch (error) {
    logger.error('加载知识树失败', error)
    knowledgeTree.value = []
    treeError.value = '知识树加载失败，请检查网络后重试'
  } finally {
    treeLoading.value = false
  }
}

function buildTree(points: Row[]): Row[] {
  const byId = new Map<string, Row & { children: Row[] }>()
  points.forEach((point) => byId.set(String(point.id), { ...point, children: [] }))
  const roots: Row[] = []
  byId.forEach((node) => {
    const parent = node.parentId ? byId.get(String(node.parentId)) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
}

async function loadKnowledgeList() {
  if (!courseId.value) {
    knowledgeList.value = []
    return
  }
  tableLoading.value = true
  listError.value = ''
  try {
    knowledgeList.value = rowsOf(unwrap(await knowledgeApi.getKnowledgePoints(courseId.value)))
  } catch (error) {
    logger.error('加载知识点列表失败', error)
    knowledgeList.value = []
    listError.value = '知识点列表加载失败，请检查网络后重试'
  } finally {
    tableLoading.value = false
  }
}

async function loadAllData() {
  await Promise.all([loadKnowledgeTree(), loadKnowledgeList()])
}

function openCreate() {
  formError.value = ''
  editingId.value = null
  Object.assign(form, {
    name: '',
    courseId: courseId.value,
    parentId: null,
    difficulty: 'MEDIUM',
    importance: 2,
    description: '',
  })
  formOpen.value = true
}

function openEdit(row: Row) {
  formError.value = ''
  editingId.value = row.id as number | string
  Object.assign(form, {
    name: row.name || '',
    courseId: row.courseId || courseId.value,
    parentId: row.parentId || null,
    difficulty: row.difficulty || 'MEDIUM',
    importance: row.importance ?? 2,
    description: row.description || '',
  })
  formOpen.value = true
}

async function submitForm() {
  if (!String(form.name || '').trim()) {
    formError.value = '请输入知识点名称'
    return
  }
  if (!form.courseId) {
    formError.value = '请选择所属课程'
    return
  }
  if (!form.difficulty) {
    formError.value = '请选择难度'
    return
  }
  submitting.value = true
  try {
    if (editingId.value) await knowledgeApi.updateKnowledgePoint(editingId.value, { ...form })
    else await knowledgeApi.createKnowledgePoint({ ...form })
    toast(editingId.value ? '知识点已更新' : '知识点已创建', 'ok')
    formOpen.value = false
    await loadAllData()
  } catch (error) {
    formError.value = '保存失败，请稍后重试'
    logger.error('保存知识点失败', error)
  } finally {
    submitting.value = false
  }
}

function askDelete(row: Row) {
  confirm.row = row
  confirm.open = true
}

async function runDelete() {
  if (!confirm.row) return
  confirm.busy = true
  try {
    await knowledgeApi.deleteKnowledgePoint(confirm.row.id as number | string)
    toast('删除成功', 'ok')
    confirm.open = false
    confirm.row = null
    await loadAllData()
  } catch (error) {
    toast('删除失败', 'danger')
    logger.error('删除知识点失败', error)
  } finally {
    confirm.busy = false
  }
}

async function openRelation(row: Row) {
  relationTarget.value = row
  newPrereqId.value = null
  prerequisites.value = []
  relationOpen.value = true
  try {
    const edges = rowsOf(
      unwrap(await knowledgeRelationApi.getTargetRelations(row.id as number | string)),
    )
    const byId = new Map(knowledgeList.value.map((point) => [String(point.id), point]))
    prerequisites.value = edges
      .filter(
        (edge) =>
          String(edge.relationType || '').toUpperCase() === 'PREREQUISITE' &&
          Number(edge.targetId) === Number(row.id),
      )
      .map((edge) => {
        const point = byId.get(String(edge.sourceId))
        return {
          id: edge.sourceId as number | string,
          relationId: edge.id,
          name: point?.name || `知识点 #${edge.sourceId}`,
        }
      })
  } catch (error) {
    logger.error('加载前置依赖失败', error)
    toast('前置依赖加载失败', 'danger')
  }
}

async function addPrerequisite() {
  if (!newPrereqId.value || !relationTarget.value) return
  try {
    await knowledgeApi.addRelation({
      sourceId: newPrereqId.value,
      targetId: relationTarget.value.id,
      relationType: 'PREREQUISITE',
    })
    toast('添加关联成功', 'ok')
    await openRelation(relationTarget.value)
  } catch (error) {
    toast('添加失败', 'danger')
    logger.error('添加前置依赖失败', error)
  }
}

async function removePrerequisite(item: Row) {
  if (!item.relationId) {
    toast('缺少关联记录 id，无法移除', 'warn')
    return
  }
  try {
    await knowledgeRelationApi.delete(item.relationId as number | string)
    toast('移除关联成功', 'ok')
    if (relationTarget.value) await openRelation(relationTarget.value)
  } catch (error) {
    toast('移除失败', 'danger')
    logger.error('移除前置依赖失败', error)
  }
}

function openImport() {
  importError.value = ''
  importResult.value = ''
  fileName.value = ''
  pickedFile.value = null
  importOpen.value = true
}

function pickFile() {
  const input = fileInput.value
  if (!input) return
  input.value = ''
  input.click()
}

function handleImport(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  fileName.value = file.name
  pickedFile.value = file
  importError.value = ''
  input.value = ''
}

async function runImport() {
  if (!pickedFile.value || !courseId.value) return
  importing.value = true
  importError.value = ''
  importResult.value = ''
  try {
    const body = unwrap(
      await knowledgeApi.importKnowledgePointsExcel(courseId.value, pickedFile.value),
    )
    const data = body as {
      successCount?: number
      created?: number
      total?: number
      failed?: number
    }
    importResult.value = `导入完成：成功 ${data?.successCount ?? data?.created ?? '—'} 条，共 ${data?.total ?? '—'} 条`
    await loadAllData()
  } catch (error) {
    importError.value = '导入失败，请检查文件格式后重试'
    logger.error('批量导入知识点失败', error)
  } finally {
    importing.value = false
  }
}

onMounted(async () => {
  await loadCourses()
  if (courseId.value) await loadAllData()
})

watch(courseId, () => {
  searchText.value = ''
  void loadAllData()
})
</script>

<style scoped>
.course-pick {
  height: 34px;
  width: 190px;
  font-size: 11px;
}
.km-grid {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 14px;
  align-items: start;
}
.km-search {
  height: 34px;
  width: 220px;
}
.tree-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.tree-list li {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  font-size: 12px;
}
.t-bullet {
  color: var(--ink-3);
  width: 10px;
}
.t-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.k-name {
  display: block;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
.k-desc {
  display: block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: var(--ink-3);
}
.relation-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.prereq-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.prereq-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  background: var(--surface-2);
  font-size: 12px;
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
@media (max-width: 1024px) {
  .km-grid {
    grid-template-columns: 1fr;
  }
}
</style>
