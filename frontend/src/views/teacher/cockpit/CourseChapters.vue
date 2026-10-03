<template>
  <div>
    <PageHeader
      compact
      title="章节结构"
      :subtitle="`${flatCount} 章 · 点击编辑节点信息`"
      icon="list"
    >
      <button class="btn btn-plain btn-sm" @click="openImport">
        <svg
          viewBox="0 0 24 24"
          width="14"
          height="14"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" />
        </svg>
        批量导入
      </button>
      <button class="btn btn-primary btn-sm" @click="openCreate()">
        <svg
          viewBox="0 0 24 24"
          width="14"
          height="14"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 5v14M5 12h14" />
        </svg>
        新增顶级章节
      </button>
    </PageHeader>
    <section class="l1-card">
      <div class="l1-body">
        <div v-if="tree.length" class="tree">
          <div v-for="node in tree" :key="node.id" class="tree-node">
            <div class="tree-row tree-row--main">
              <span class="tree-toggle" @click="toggle(node.id)"
                ><svg
                  viewBox="0 0 24 24"
                  width="14"
                  height="14"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  :class="isOpen(node.id) ? 'is-open' : ''"
                >
                  <path d="m9 18 6-6-6-6" /></svg
              ></span>
              <span class="tree-title">{{ node.title }}</span>
              <span class="muted tree-meta--main"
                >任务点 {{ node.taskCount || 0 }} · 完成率 {{ pct(node.completionRate) }}</span
              >
              <span class="tag" :class="node.isPublished ? 'green' : ''">{{
                node.isPublished ? '已发布' : '草稿'
              }}</span>
              <span class="tree-actions"
                ><span class="op-link" @click="openEdit(node)">编辑</span
                ><span class="op-link danger" @click="remove(node)">删除</span></span
              >
            </div>
            <div
              v-if="node.children && node.children.length && isOpen(node.id)"
              class="tree-children-pad"
            >
              <div v-for="c in node.children" :key="c.id">
                <div class="tree-row tree-row--child">
                  <span class="tree-title tree-title--child">{{ c.title }}</span>
                  <span class="muted tree-meta--child"
                    >任务点 {{ c.taskCount || 0 }} · 完成率 {{ pct(c.completionRate) }}</span
                  >
                  <span class="tag" :class="c.isPublished ? 'green' : ''">{{
                    c.isPublished ? '已发布' : '草稿'
                  }}</span>
                  <span class="tree-actions"
                    ><span class="op-link" @click="openEdit(c)">编辑</span
                    ><span class="op-link danger" @click="remove(c)">删除</span></span
                  >
                </div>
                <div v-if="c.taskPoints && c.taskPoints.length" class="task-tags">
                  <span v-for="tp in c.taskPoints" :key="tp.id" class="tag outline task-tag"
                    >{{ tp.title }} <em>{{ tp.taskType }}</em></span
                  >
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">
          <div class="e-ico">
            <svg
              viewBox="0 0 24 24"
              width="24"
              height="24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M21 8v13H3V8M1 3h22v5H1zM10 12h4" />
            </svg>
          </div>
          <div class="e-title">暂无章节</div>
          <div class="e-desc">点击右上角新增章节</div>
        </div>
      </div>
    </section>

    <!-- 新增/编辑对话框 -->
    <Modal v-if="dialogVisible" narrow @close="closeDialog">
      <template #title>{{ dialogMode === 'create' ? '新增章节' : '编辑章节' }}</template>
      <div class="form-grid">
        <div class="field span-2">
          <label>标题<span class="req">*</span></label
          ><input v-model="form.title" class="input" />
        </div>
        <div class="field">
          <label>排序</label><input v-model="form.sortOrder" class="input" type="number" />
        </div>
        <div class="field">
          <label>发布状态</label
          ><select v-model="form.isPublished" class="select">
            <option :value="true">已发布</option>
            <option :value="false">草稿</option>
          </select>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-plain" @click="dialogVisible = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="save">保存</button>
      </template>
    </Modal>

    <!-- 批量导入章节（Excel） -->
    <ExcelImportDialog
      v-if="showImport"
      title="批量导入章节"
      sub="上传 .xlsx 一次性建立课程大纲；父章节填标题即可形成二级章节"
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
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import chapterApi from '@/api/chapter'
import teachingApi from '@/api/teacher'
import Modal from '@/components/ui/Modal.vue'
import PageHeader from '@/components/PageHeader.vue'
import ExcelImportDialog from '@/components/ui/ExcelImportDialog.vue'

const route = useRoute()
const courseId = route.params.id

const loading = ref(false)
const saving = ref(false)
const tree = ref([])
const openSet = reactive(new Set())

const dialogVisible = ref(false)
const dialogMode = ref('create')
const editingId = ref(null)
const form = reactive({ title: '', content: '', orderIndex: 0, duration: 0 })

/** 批量导入（Excel）状态 */
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
  { name: '章节标题*', required: true },
  { name: '父章节标题' },
  { name: '排序序号' },
  { name: '时长(分钟)' },
  { name: '是否发布' },
  { name: '章节内容' },
]
const IMPORT_NOTES = [
  '仅「章节标题」必填，为空的行会被跳过',
  '父章节标题填写已有章节名即可建立二级章节（可留空表示顶级）',
  '「是否发布」填 是/否，留空默认发布',
  '同一课程下标题重复的行会被跳过',
]

function openImport() {
  importResult.value = null
  showImport.value = true
}

async function handleImport(file) {
  if (!file) return
  importing.value = true
  try {
    const res = await chapterApi.importChaptersExcel(courseId, file)
    const payload = (unwrap(res) || {}) as ImportResultPayload
    importResult.value = payload
    if (payload.importedCount > 0) {
      ElMessage.success(`已导入 ${payload.importedCount} 个章节`)
      load()
    } else {
      ElMessage.warning('没有章节被导入，请检查文件内容')
    }
  } catch (e) {
    logger.error('导入章节失败:', e)
    ElMessage.error('导入失败，请检查文件格式（.xlsx，6 列表头）')
  } finally {
    importing.value = false
  }
}

const flatCount = computed(() => {
  let n = 0
  tree.value.forEach((n1) => {
    n++
    ;(n1.children || []).forEach(() => n++)
  })
  return n
})

function pct(v) {
  if (v == null) return '--'
  return typeof v === 'number' && v <= 1.0001 && v > 0 ? Math.round(v * 100) + '%' : v + '%'
}
function isOpen(id) {
  return openSet.has(id)
}
function toggle(id) {
  openSet.has(id) ? openSet.delete(id) : openSet.add(id)
}

async function load() {
  loading.value = true
  try {
    const r = await teachingApi.getChapterTree(courseId)
    const body = unwrap(r)
    const list = body?.records || body?.list || body || []
    tree.value = list
    list.forEach((n) => openSet.add(n.id))
  } catch (e) {
    logger.error('加载章节树失败', e)
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
// 修复：模板多语句表达式与 prettier(semi:false) 冲突会产生 Vue 编译器无法解析的换行格式，
// 提取为方法（对话框关闭：隐藏并重置表单）
function closeDialog() {
  dialogVisible.value = false
  resetForm()
}
function openCreate() {
  dialogMode.value = 'create'
  resetForm()
  dialogVisible.value = true
}
function openEdit(node) {
  dialogMode.value = 'edit'
  editingId.value = node.id
  form.title = node.title || ''
  form.content = node.content || ''
  form.orderIndex = node.orderIndex ?? 0
  form.duration = node.duration ?? 0
  dialogVisible.value = true
}

async function save() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写章节标题')
    return
  }
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await chapterApi.createChapter({
        courseId,
        title: form.title.trim(),
        content: form.content,
        orderIndex: form.orderIndex,
        duration: form.duration,
      })
      ElMessage.success('章节已创建')
    } else {
      await chapterApi.updateChapter(editingId.value, {
        title: form.title.trim(),
        content: form.content,
        orderIndex: form.orderIndex,
        duration: form.duration,
      })
      ElMessage.success('章节已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    logger.error('保存章节失败', e)
  } finally {
    saving.value = false
  }
}

async function remove(node) {
  try {
    await ElMessageBox.confirm(
      `确认删除章节「${node.title}」？其子章节与任务点将一并移除，且不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }
  try {
    await chapterApi.deleteChapter(node.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    logger.error('删除章节失败', e)
  }
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<style scoped>
.tree {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.tree-node {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
}
.tree-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.tree-row--main {
  padding: 10px 12px;
  margin-bottom: 2px;
}
.tree-row--child {
  padding: 8px 12px;
  margin-bottom: 2px;
  background: var(--surface-3);
  border-radius: 7px;
}
.tree-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  cursor: pointer;
  color: var(--ac-text-muted);
  transition: transform var(--ac-transition-fast);
}
.tree-toggle svg {
  transition: transform var(--ac-transition-fast);
}
.tree-toggle svg.is-open {
  transform: rotate(90deg);
}
.tree-title {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 14px;
}
.tree-title--child {
  font-size: 13px;
  color: var(--ink-2);
}
.tree-meta--main {
  font-size: 11.5px;
  margin-left: auto;
}
.tree-meta--child {
  font-size: 11px;
  margin-left: auto;
}
.tree-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}
.tree-children-pad {
  padding-left: 18px;
}
.task-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 4px 0 8px 12px;
}
.task-tag {
  font-size: 10px;
}
</style>
