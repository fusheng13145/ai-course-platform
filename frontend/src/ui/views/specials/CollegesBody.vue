<template>
  <section class="colleges-body">
    <div v-if="listError" class="callout" role="alert">
      <span>ERROR / 组织架构</span>
      <p>{{ listError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="reload">
        {{ loading ? '重试中…' : '重试' }}
      </button>
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">ADMIN ORG / COLLEGES</p>
          <h2>
            学院 <span class="chip chip-mute">{{ colleges.length }} 个</span>
          </h2>
        </div>
        <div class="filter-row class-tools">
          <button class="btn btn-sm btn-ghost" :disabled="loading" @click="reload">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
          <button class="btn btn-sm btn-primary" @click="openCollegeCreate">新建学院</button>
        </div>
      </header>
      <div v-if="colleges.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>学院名称</th>
              <th class="w-140">编码</th>
              <th class="w-140">简介</th>
              <th class="w-140 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in colleges" :key="String(row.id)">
              <td>
                <b>{{ row.name || '—' }}</b>
              </td>
              <td class="mono">{{ row.code || '—' }}</td>
              <td>{{ row.description || '—' }}</td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="openCollegeEdit(row)">编辑</button>
                  <button class="text-btn danger" @click="askCollegeDelete(row)">归档</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载学院…</b></div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="building" /></span>
        <b>暂无学院</b>
        <p>用右上角「新建学院」建立第一层组织架构。</p>
      </div>
    </section>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">ADMIN ORG / MAJORS</p>
          <h2>
            专业 <span class="chip chip-mute">{{ majors.length }} 个</span>
          </h2>
        </div>
        <div class="filter-row class-tools">
          <select v-model="majorCollegeId" class="input filter-select" aria-label="按学院筛选专业">
            <option :value="null">全部学院</option>
            <option v-for="college in colleges" :key="String(college.id)" :value="college.id">
              {{ college.name }}
            </option>
          </select>
          <button class="btn btn-sm btn-primary" @click="openMajorCreate">新建专业</button>
        </div>
      </header>
      <div v-if="pagedMajors.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>专业名称</th>
              <th class="w-140">编码</th>
              <th class="w-140">所属学院</th>
              <th class="w-140 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in pagedMajors" :key="String(row.id)">
              <td>
                <b>{{ row.name || '—' }}</b>
              </td>
              <td class="mono">{{ row.code || '—' }}</td>
              <td>{{ collegeName(row.collegeId) }}</td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="openMajorEdit(row)">编辑</button>
                  <button class="text-btn danger" @click="askMajorDelete(row)">归档</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载专业…</b></div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="layers" /></span>
        <b>暂无专业</b>
        <p>先建学院，再在对应学院下建专业。</p>
      </div>
      <footer v-if="majorShowPager" class="card-foot">
        <span>第 {{ majorPage }} / {{ majorPageCount }} 页 · 共 {{ majorTotal }} 个</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="majorPage <= 1" @click="majorPage -= 1">
            上一页
          </button>
          <button
            class="btn btn-sm"
            :disabled="majorPage >= majorPageCount"
            @click="majorPage += 1"
          >
            下一页
          </button>
        </div>
      </footer>
      <p class="hint">行政班的学生归属在「行政班」页维护；本页只管 学校 → 学院 →专业 三层骨架。</p>
    </section>

    <Modal
      v-model:open="collegeOpen"
      :title="collegeEditingId === null ? '新建学院' : '编辑学院'"
      kicker="ORG / COLLEGE"
      width="520px"
      :close-on-backdrop="false"
    >
      <div class="form-grid">
        <label class="field"
          ><span>学院名称 *</span>
          <input v-model="collegeForm.name" class="input" placeholder="如：计算机学院" />
        </label>
        <label class="field"
          ><span>编码 *</span>
          <input v-model="collegeForm.code" class="input mono" placeholder="如：CS" />
        </label>
        <label class="field span-2"
          ><span>简介</span>
          <textarea
            v-model="collegeForm.description"
            class="input textarea"
            rows="2"
            placeholder="可选"
          ></textarea>
        </label>
      </div>
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="saving" @click="collegeOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submitCollege">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <Modal
      v-model:open="majorOpen"
      :title="majorEditingId === null ? '新建专业' : '编辑专业'"
      kicker="ORG / MAJOR"
      width="520px"
      :close-on-backdrop="false"
    >
      <div class="form-grid">
        <label class="field"
          ><span>专业名称 *</span>
          <input v-model="majorForm.name" class="input" placeholder="如：计算机科学与技术" />
        </label>
        <label class="field"
          ><span>编码 *</span>
          <input v-model="majorForm.code" class="input mono" placeholder="如：CST" />
        </label>
        <label class="field"
          ><span>所属学院 *</span>
          <select v-model="majorForm.collegeId" class="input">
            <option :value="null" disabled>请选择学院</option>
            <option v-for="college in colleges" :key="String(college.id)" :value="college.id">
              {{ college.name }}
            </option>
          </select>
        </label>
      </div>
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="saving" @click="majorOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submitMajor">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <Modal
      v-model:open="deleteOpen"
      title="确认归档"
      kicker="CONFIRM / ARCHIVE"
      width="460px"
      :close-on-backdrop="false"
    >
      <p class="modal-text">
        确定归档 <b>{{ deleteTarget?.name || '该条目' }}</b> 吗？
      </p>
      <p class="modal-hint">
        归档为软删除：组织树与下拉里不再出现；历史数据（用户/课程归属）不受影响。
      </p>
      <p v-if="deleteError" class="form-error" role="alert">{{ deleteError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="deleting" @click="deleteOpen = false">取消</button>
        <button class="btn btn-danger" :disabled="deleting" @click="confirmDelete">
          {{ deleting ? '处理中…' : '确定归档' }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import Icon from '../../components/Icon.vue'
import Modal from '../../components/Modal.vue'
import { useUi } from '../../composables/ui'
import { useClientPagination } from '@/composables/useClientPagination'
import {
  archiveCollege,
  archiveMajor,
  createCollege,
  createMajor,
  getColleges,
  getMajors,
  updateCollege,
  updateMajor,
} from '@/api/admin'
import { pickList, unwrap } from '@/utils/response'

interface OrgRow {
  id?: number | string
  name?: string
  code?: string
  description?: string
  collegeId?: number | string | null
}

const { toast } = useUi()
const colleges = ref<OrgRow[]>([])
const majors = ref<OrgRow[]>([])
const loading = ref(false)
const listError = ref('')

const saving = ref(false)
const formError = ref('')

const collegeOpen = ref(false)
const collegeEditingId = ref<number | string | null>(null)
const collegeForm = reactive({ name: '', code: '', description: '' })

const majorOpen = ref(false)
const majorEditingId = ref<number | string | null>(null)
const majorCollegeId = ref<number | string | null>(null)
const majorForm = reactive({ name: '', code: '', collegeId: null as number | string | null })

const deleteOpen = ref(false)
const deleteTarget = ref<OrgRow | null>(null)
const deleteKind = ref<'college' | 'major'>('college')
const deleting = ref(false)
const deleteError = ref('')

const filteredMajors = computed(() =>
  majorCollegeId.value === null
    ? majors.value
    : majors.value.filter((row) => String(row.collegeId ?? '') === String(majorCollegeId.value)),
)

const {
  page: majorPage,
  pageSize: majorPageSize,
  total: majorTotal,
  paged: pagedMajors,
  showPager: majorShowPager,
  onPageChange: onMajorPageChange,
  reset: resetMajorPage,
} = useClientPagination<OrgRow>(() => filteredMajors.value, { pageSize: 15 })
const majorPageCount = computed(() =>
  Math.max(1, Math.ceil(majorTotal.value / majorPageSize.value)),
)

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

function collegeName(collegeId: unknown): string {
  const found = colleges.value.find((c) => String(c.id) === String(collegeId))
  return found?.name || `学院 #${String(collegeId)}`
}

async function reload() {
  loading.value = true
  listError.value = ''
  try {
    const [collegePayload, majorPayload] = await Promise.all([getColleges(), getMajors({})])
    const collegeRows = pickList<OrgRow>(unwrap(collegePayload))
    const majorRows = pickList<OrgRow>(unwrap(majorPayload))
    if (!Array.isArray(collegeRows) || !Array.isArray(majorRows)) {
      throw new Error('组织架构响应格式无效')
    }
    colleges.value = collegeRows
    majors.value = majorRows
  } catch (error) {
    listError.value = errorText(error, '组织架构加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function openCollegeCreate() {
  collegeEditingId.value = null
  Object.assign(collegeForm, { name: '', code: '', description: '' })
  formError.value = ''
  collegeOpen.value = true
}

function openCollegeEdit(row: OrgRow) {
  collegeEditingId.value = row.id ?? null
  Object.assign(collegeForm, {
    name: row.name || '',
    code: row.code || '',
    description: row.description || '',
  })
  formError.value = ''
  collegeOpen.value = true
}

async function submitCollege() {
  const name = collegeForm.name.trim()
  const code = collegeForm.code.trim()
  if (!name || !code) {
    formError.value = '学院名称与编码必填'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    const payload = { name, code, description: collegeForm.description.trim() }
    if (collegeEditingId.value === null) {
      await createCollege(payload)
      toast('学院已创建', 'ok')
    } else {
      await updateCollege(collegeEditingId.value, payload)
      toast('学院已更新', 'ok')
    }
    collegeOpen.value = false
    await reload()
  } catch (error) {
    formError.value = errorText(error, '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function openMajorCreate() {
  majorEditingId.value = null
  Object.assign(majorForm, { name: '', code: '', collegeId: majorCollegeId.value })
  formError.value = ''
  majorOpen.value = true
}

function openMajorEdit(row: OrgRow) {
  majorEditingId.value = row.id ?? null
  Object.assign(majorForm, {
    name: row.name || '',
    code: row.code || '',
    collegeId: row.collegeId ?? null,
  })
  formError.value = ''
  majorOpen.value = true
}

async function submitMajor() {
  const name = majorForm.name.trim()
  const code = majorForm.code.trim()
  if (!name || !code) {
    formError.value = '专业名称与编码必填'
    return
  }
  if (!majorForm.collegeId) {
    formError.value = '请选择所属学院'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    const payload = { name, code, collegeId: majorForm.collegeId }
    if (majorEditingId.value === null) {
      await createMajor(payload)
      toast('专业已创建', 'ok')
    } else {
      await updateMajor(majorEditingId.value, payload)
      toast('专业已更新', 'ok')
    }
    majorOpen.value = false
    await reload()
  } catch (error) {
    formError.value = errorText(error, '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function askDelete(row: OrgRow, kind: 'college' | 'major') {
  if (row.id === undefined || row.id === null) return
  deleteTarget.value = row
  deleteKind.value = kind
  deleteError.value = ''
  deleteOpen.value = true
}
function askCollegeDelete(row: OrgRow) {
  askDelete(row, 'college')
}
function askMajorDelete(row: OrgRow) {
  askDelete(row, 'major')
}

async function confirmDelete() {
  const row = deleteTarget.value
  if (!row || row.id === undefined || row.id === null) return
  deleting.value = true
  deleteError.value = ''
  try {
    if (deleteKind.value === 'college') {
      await archiveCollege(row.id)
      toast('学院已归档', 'ok')
    } else {
      await archiveMajor(row.id)
      toast('专业已归档', 'ok')
    }
    deleteOpen.value = false
    deleteTarget.value = null
    await reload()
  } catch (error) {
    deleteError.value = errorText(error, '归档失败，请稍后重试')
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  void reload()
  void onMajorPageChange(1)
  resetMajorPage()
})
</script>

<style scoped>
.colleges-body {
  display: grid;
  gap: 14px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.filter-select {
  width: 180px;
  height: 30px;
}
</style>
