<template>
  <section class="classes-body">
    <div v-if="listError" class="callout" role="alert">
      <span>ERROR / 班级列表</span>
      <p>{{ listError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="loadClasses()">
        {{ loading ? '重试中…' : '重试' }}
      </button>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">ADMIN CLASSES / CLASSROOMS</p>
          <h2>
            行政班列表 <span class="chip chip-mute">{{ rows.length }} 个</span>
          </h2>
        </div>
        <div class="filter-row class-tools">
          <form class="search-form" @submit.prevent="loadClasses(true)">
            <input
              v-model="keyword"
              class="input search-input"
              type="search"
              placeholder="搜索班级名称 / 编号 / 简介"
              aria-label="搜索班级名称、编号或简介"
            />
            <button class="btn btn-sm" type="submit" :disabled="loading">查询</button>
          </form>
          <button class="btn btn-sm btn-ghost" :disabled="loading" @click="loadClasses()">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
          <button class="btn btn-sm btn-primary" @click="openCreate">新建班级</button>
        </div>
      </header>

      <div v-if="paged.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>班级名称 / 编号</th>
              <th class="w-140">专业</th>
              <th class="w-80">年级</th>
              <th class="w-140">班主任</th>
              <th class="w-96">人数 / 上限</th>
              <th class="w-80">状态</th>
              <th class="w-140">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in paged" :key="String(row.id ?? index)">
              <td>
                <b>{{ row.name || '—' }}</b>
                <small class="cell-sub">{{ row.code || '—' }}</small>
              </td>
              <td>{{ row.major || '未分配专业' }}</td>
              <td>{{ row.gradeLevel || '—' }}</td>
              <td>{{ advisorLabel(row) }}</td>
              <td>{{ row.studentCount ?? 0 }} / {{ row.maxStudents ?? '—' }}</td>
              <td>
                <span class="chip" :class="activeOf(row) ? 'chip-ok' : 'chip-mute'">
                  {{ activeOf(row) ? '启用' : '停用' }}
                </span>
              </td>
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

      <div v-else-if="loading" class="empty-state compact">
        <b>正在加载班级…</b>
      </div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="building" /></span>
        <b>{{ emptyDescription() }}</b>
        <p>
          {{ listError ? '请查看上方提示后重试。' : '调整搜索词，或用右上角「新建班级」创建。' }}
        </p>
      </div>

      <footer v-if="showPager" class="card-foot">
        <span>
          第 {{ page }} / {{ pageCount }} 页 · 共 {{ total }} 个（每页 {{ pageSize }} 个）
        </span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1" @click="onPageChange(page - 1)">
            上一页
          </button>
          <button class="btn btn-sm" :disabled="page >= pageCount" @click="onPageChange(page + 1)">
            下一页
          </button>
        </div>
      </footer>

      <p class="hint">
        本页是行政班（classrooms）：字段为名称、编号、专业、年级、班主任、人数上限。
        学院见「学院与专业」，教学班见「教学班监管」，两者都不在本表。
      </p>
    </section>

    <Modal
      v-model:open="editorOpen"
      :title="editingId === null ? '新建班级' : '编辑班级'"
      kicker="CLASSROOM / EDIT"
      width="640px"
      :close-on-backdrop="false"
    >
      <div class="form-grid">
        <label class="field">
          <span>班级名称 *</span>
          <input
            v-model="form.name"
            class="input"
            data-testid="class-name"
            placeholder="如：计算机 2201 班"
          />
        </label>
        <label class="field">
          <span>班级编号 *</span>
          <input
            v-model="form.code"
            class="input mono"
            data-testid="class-code"
            placeholder="如：CS2201"
          />
        </label>
        <label class="field">
          <span>专业</span>
          <input
            v-model="form.major"
            class="input"
            data-testid="class-major"
            placeholder="如：计算机科学与技术"
          />
        </label>
        <label class="field">
          <span>年级</span>
          <input
            v-model="form.gradeLevel"
            class="input"
            data-testid="class-grade"
            placeholder="如：2022"
          />
        </label>
        <label class="field">
          <span>人数上限</span>
          <input
            v-model.number="form.maxStudents"
            class="input"
            data-testid="class-max"
            type="number"
            min="0"
          />
        </label>
        <label class="field">
          <span>状态</span>
          <select v-model="form.isActive" class="input">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select>
        </label>
        <label class="field span-2">
          <span>班级简介</span>
          <textarea
            v-model="form.description"
            class="input textarea"
            data-testid="class-desc"
            rows="2"
            placeholder="可选"
          ></textarea>
        </label>
      </div>
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      <p class="modal-hint">班主任与学生归属不在本表单修改；行政班不绑定教学班或学院。</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="saving" @click="closeEditor">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submitEditor">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <Modal
      v-model:open="deleteOpen"
      title="删除班级"
      kicker="CONFIRM / DELETE"
      width="460px"
      :close-on-backdrop="false"
    >
      <p class="modal-text">
        确定删除班级 <b>{{ deleteTarget?.name || '该班级' }}</b> 吗？
      </p>
      <p class="modal-hint">
        删除后该校班级记录不可恢复，学生的班级归属会被清空；历史学习记录不受影响。
      </p>
      <p v-if="deleteError" class="form-error" role="alert">{{ deleteError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="deleting" @click="closeDelete">取消</button>
        <button class="btn btn-danger" :disabled="deleting" @click="confirmDelete">
          {{ deleting ? '删除中…' : '确定删除' }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Icon from '../../components/Icon.vue'
import Modal from '../../components/Modal.vue'
import Stat from '../../components/Stat.vue'
import { useUi } from '../../composables/ui'
import { useListFeedback } from '@/composables/useListFeedback'
import { useClientPagination } from '@/composables/useClientPagination'
import {
  createClassroom,
  deleteClassroom,
  getClassroomList,
  updateClassroom,
} from '@/api/classroom'
import { pickList, unwrap } from '@/utils/response'
import type { Metric } from '../../nav'

interface ClassroomRow {
  id?: number | string
  name?: string
  code?: string
  description?: string
  gradeLevel?: string
  major?: string
  advisorId?: number | string | null
  studentCount?: number | null
  maxStudents?: number | null
  isActive?: boolean
  [key: string]: unknown
}

const { toast } = useUi()

const rows = ref<ClassroomRow[]>([])
const keyword = ref('')

const { page, pageSize, total, paged, showPager, onPageChange, reset } =
  useClientPagination<ClassroomRow>(() => rows.value, { pageSize: 20 })
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const editorOpen = ref(false)
const editingId = ref<ClassroomRow['id'] | null>(null)
const saving = ref(false)
const formError = ref('')
const form = reactive({
  name: '',
  code: '',
  major: '',
  gradeLevel: '',
  maxStudents: 50,
  description: '',
  isActive: true,
})

const deleteOpen = ref(false)
const deleteTarget = ref<ClassroomRow | null>(null)
const deleting = ref(false)
const deleteError = ref('')

const { loading, listError, run, emptyDescription } = useListFeedback({
  failMessage: '班级列表加载失败，请稍后重试。',
  toastMessage: '班级列表加载失败',
  emptyText: '暂无行政班',
  errorEmptyText: '无法确认班级数据，请查看上方提示后重试',
  notify: (message) => toast(message, 'danger'),
})

const studentTotal = computed(() =>
  rows.value.reduce((sum, row) => sum + Number(row.studentCount || 0), 0),
)
const majorCount = computed(() => new Set(rows.value.map((row) => row.major || '未分配专业')).size)

const metrics = computed<Metric[]>(() => [
  {
    label: '班级总数',
    value: listError.value ? '—' : rows.value.length,
    unit: '个',
    note: '当前结果',
    icon: 'building',
  },
  {
    label: '在册学生',
    value: listError.value ? '—' : studentTotal.value,
    unit: '人',
    note: 'studentCount 合计',
    icon: 'users',
    tone: 'ok',
  },
  {
    label: '专业数',
    value: listError.value ? '—' : majorCount.value,
    unit: '个',
    note: '按 major 去重',
    icon: 'layers',
    tone: 'info',
  },
  {
    label: '平均班额',
    value: listError.value
      ? '—'
      : rows.value.length
        ? Math.round(studentTotal.value / rows.value.length)
        : 0,
    unit: '人',
    note: '学生数 / 班级数',
    icon: 'chart',
  },
])

function activeOf(row: ClassroomRow): boolean {
  return row.isActive !== false
}

function advisorLabel(row: ClassroomRow): string {
  return row.advisorId ? `教师 #${row.advisorId}` : '未指定'
}

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

async function loadClasses(resetPage = false) {
  if (resetPage) reset()
  await run(async () => {
    const payload = unwrap(await getClassroomList({ keyword: keyword.value.trim() || undefined }))
    if (!Array.isArray(payload)) throw new Error('班级列表响应格式无效')
    rows.value = pickList<ClassroomRow>(payload)
  })
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    code: '',
    major: '',
    gradeLevel: '',
    maxStudents: 50,
    description: '',
    isActive: true,
  })
  formError.value = ''
  editorOpen.value = true
}

function openEdit(row: ClassroomRow) {
  editingId.value = row.id ?? null
  Object.assign(form, {
    name: row.name || '',
    code: row.code || '',
    major: row.major || '',
    gradeLevel: row.gradeLevel || '',
    maxStudents: row.maxStudents ?? 50,
    description: row.description || '',
    isActive: activeOf(row),
  })
  formError.value = ''
  editorOpen.value = true
}

function closeEditor() {
  if (saving.value) return
  editorOpen.value = false
  formError.value = ''
}

async function submitEditor() {
  const name = form.name.trim()
  const code = form.code.trim()
  if (!name) {
    formError.value = '请填写班级名称'
    return
  }
  if (!code) {
    formError.value = '请填写班级编号'
    return
  }
  const maxStudents = Number(form.maxStudents)
  if (!Number.isInteger(maxStudents) || maxStudents < 0) {
    formError.value = '人数上限必须是非负整数'
    return
  }
  const payload = {
    name,
    code,
    major: form.major.trim(),
    gradeLevel: form.gradeLevel.trim(),
    maxStudents,
    description: form.description.trim(),
    isActive: form.isActive,
  }
  saving.value = true
  formError.value = ''
  try {
    if (editingId.value === null) {
      await createClassroom(payload)
      toast('班级已创建', 'ok')
    } else {
      await updateClassroom(editingId.value, payload)
      toast('班级已更新', 'ok')
    }
    editorOpen.value = false
    await loadClasses()
  } catch (error) {
    formError.value = errorText(error, '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function askDelete(row: ClassroomRow) {
  if (row.id === undefined || row.id === null) return
  deleteTarget.value = row
  deleteError.value = ''
  deleteOpen.value = true
}

function closeDelete() {
  if (deleting.value) return
  deleteOpen.value = false
  deleteTarget.value = null
  deleteError.value = ''
}

async function confirmDelete() {
  const row = deleteTarget.value
  if (!row || row.id === undefined || row.id === null) return
  deleting.value = true
  deleteError.value = ''
  try {
    await deleteClassroom(row.id)
    deleteOpen.value = false
    deleteTarget.value = null
    toast('班级已删除', 'ok')
    await loadClasses()
  } catch (error) {
    deleteError.value = errorText(error, '删除失败，请稍后重试')
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  void loadClasses()
})
</script>

<style scoped>
.classes-body {
  display: grid;
  gap: 14px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.search-form {
  display: flex;
  gap: 6px;
}
.search-input {
  width: 240px;
  height: 30px;
}
</style>
