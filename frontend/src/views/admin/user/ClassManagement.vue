<template>
  <div class="fade-in class-management">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">组织与班级管理</h2>
        <p class="page-desc">按院系 / 专业 / 年级维度组织班级，管理班级成员与班主任</p>
      </div>
    </div>

    <!-- 统计概览 -->
    <div class="stats-row">
      <div v-for="(stat, idx) in classStats" :key="idx" class="stat-mini-card" :class="stat.class">
        <span class="label">{{ stat.label }}</span>
        <span class="value">{{ stat.value }}</span>
      </div>
    </div>

    <div class="layout-split">
      <!-- 左侧：组织树 -->
      <div class="org-panel content-card">
        <div class="org-panel-head">
          <span class="org-title">组织维度</span>
          <el-button text type="primary" size="small" @click="resetOrgFilter">重置</el-button>
        </div>
        <el-tree
          ref="orgTreeRef"
          :data="orgTree"
          :props="{ label: 'label', children: 'children' }"
          node-key="key"
          highlight-current
          :expand-on-click-node="false"
          default-expand-all
          @node-click="onOrgNodeClick"
        >
          <template #default="{ data }">
            <span class="org-node">
              <el-icon v-if="data.type === 'root'"><School /></el-icon>
              <el-icon v-else-if="data.type === 'major'"><Collection /></el-icon>
              <el-icon v-else><UserFilled /></el-icon>
              <span class="org-label">{{ data.label }}</span>
              <span v-if="data.count != null" class="org-count">{{ data.count }}</span>
            </span>
          </template>
        </el-tree>
      </div>

      <!-- 右侧：班级列表 -->
      <div class="list-panel">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <el-button type="primary" @click="openCreate"
              ><el-icon><Plus /></el-icon>新建班级</el-button
            >
            <span v-if="activeOrgLabel" class="active-filter">
              当前筛选：<b>{{ activeOrgLabel }}</b>
            </span>
          </div>
          <div class="toolbar-right">
            <el-input
              v-model="keyword"
              placeholder="搜索班级名称 / 编码"
              clearable
              style="width: 220px"
              @keyup.enter="loadClassrooms"
            >
              <template #prefix
                ><el-icon><Search /></el-icon
              ></template>
            </el-input>
            <el-button @click="loadClassrooms">查询</el-button>
          </div>
        </div>

        <div class="content-card">
          <el-table v-loading="loading" :data="pagedList" style="width: 100%">
            <el-table-column prop="id" label="ID" width="55" />
            <el-table-column prop="name" label="班级名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="code" label="班级编码" min-width="110" show-overflow-tooltip />
            <el-table-column prop="major" label="专业" min-width="120" show-overflow-tooltip />
            <el-table-column prop="gradeLevel" label="年级" width="90" align="center" />
            <el-table-column label="人数" width="110" align="center">
              <template #default="{ row }">
                <span class="count-badge"
                  >{{ row.studentCount || 0 }} / {{ row.maxStudents || '—' }}</span
                >
              </template>
            </el-table-column>
            <el-table-column
              prop="isActive"
              label="状态"
              width="90"
              align="center"
              class-name="tag-col"
            >
              <template #default="{ row }">
                <span
                  class="status-dot"
                  :class="row.isActive !== false ? 'status-dot--active' : 'status-dot--inactive'"
                >
                  <i></i>{{ row.isActive !== false ? '启用' : '停用' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" align="center">
              <template #default="{ row }">
                <div class="action-row">
                  <button class="act-btn act-btn--edit" @click="manageStudents(row)">成员</button>
                  <button class="act-btn act-btn--edit" @click="openEdit(row)">编辑</button>
                  <button class="act-btn--del" @click="removeClassroom(row)">删除</button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无班级数据" />
            </template>
          </el-table>

          <div v-if="filteredList.length > pageSize" class="pager">
            <el-pagination
              layout="prev, pager, next, total"
              :total="filteredList.length"
              :page-size="pageSize"
              :current-page="page"
              @current-change="(p) => (page = p)"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 新建/编辑班级 -->
    <el-dialog
      v-model="showEdit"
      :title="editing ? '编辑班级' : '新建班级'"
      width="520px"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="班级名称" prop="name"
          ><el-input v-model="form.name" placeholder="如：计算机2201班"
        /></el-form-item>
        <el-form-item label="班级编码" prop="code"
          ><el-input v-model="form.code" placeholder="如：CS2201"
        /></el-form-item>
        <el-form-item label="专业" prop="major"
          ><el-input v-model="form.major" placeholder="如：计算机科学与技术"
        /></el-form-item>
        <el-form-item label="年级" prop="gradeLevel"
          ><el-input v-model="form.gradeLevel" placeholder="如：2022"
        /></el-form-item>
        <el-form-item label="最大人数"
          ><el-input-number v-model="form.maxStudents" :min="0" :max="500" style="width: 100%"
        /></el-form-item>
        <el-form-item label="班级简介"
          ><el-input v-model="form.description" type="textarea" :rows="2"
        /></el-form-item>
        <el-form-item label="状态"
          ><el-switch v-model="form.isActive" active-text="启用" inactive-text="停用"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveClassroom">保存</el-button>
      </template>
    </el-dialog>

    <!-- 班级成员管理 -->
    <el-dialog
      v-model="showStudents"
      :title="`成员管理 · ${currentClass?.name || ''}`"
      width="720px"
      append-to-body
    >
      <div class="student-toolbar">
        <el-input
          v-model="studentKeyword"
          placeholder="搜索学生姓名 / 学号"
          clearable
          style="width: 240px"
          @keyup.enter="loadStudents"
        >
          <template #prefix
            ><el-icon><Search /></el-icon
          ></template>
        </el-input>
        <el-button @click="loadStudents">查询</el-button>
        <el-button type="primary" @click="openAddStudent"
          ><el-icon><Plus /></el-icon>添加学生</el-button
        >
      </div>
      <el-table v-loading="studentLoading" :data="studentList" max-height="380" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="学号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="fullName" label="姓名" min-width="100" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <button class="act-btn--del" @click="removeStudentFromClass(row)">移除</button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="该班级暂无学生" :image-size="80" /></template>
      </el-table>
    </el-dialog>

    <!-- 添加学生 -->
    <el-dialog v-model="showAddStudent" title="添加学生到班级" width="620px" append-to-body>
      <div class="student-toolbar">
        <el-input
          v-model="candidateKeyword"
          placeholder="搜索学生姓名 / 学号"
          clearable
          style="width: 280px"
        />
      </div>
      <el-table
        v-loading="candidateLoading"
        :data="filteredCandidates"
        max-height="380"
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="学号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="fullName" label="姓名" min-width="100" show-overflow-tooltip />
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <button class="act-btn act-btn--approve" @click="addStudentToClass(row)">添加</button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="无可添加学生" :image-size="80" /></template>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { School, Collection, UserFilled, Plus, Search } from '@element-plus/icons-vue'
import classroomApi from '@/api/classroom'
import adminApi from '@/api/admin'

const loading = ref(false)
const saving = ref(false)
const allClassrooms = ref([])
const keyword = ref('')
const page = ref(1)
const pageSize = 10

/* ---- 组织树筛选 ---- */
const orgTreeRef = ref(null)
const activeMajor = ref('')
const activeGrade = ref('')
const activeOrgLabel = ref('')

const classStats = ref([
  { label: '班级总数', value: 0, class: '' },
  { label: '专业数', value: 0, class: 'teacher' },
  { label: '在册学生', value: 0, class: 'student' },
  { label: '平均班额', value: 0, class: 'admin' },
])

// 根据班级数据动态构建 专业 -> 年级 组织树
const orgTree = computed(() => {
  const majors = {}
  for (const c of allClassrooms.value) {
    const m = c.major || '未分配专业'
    const g = c.gradeLevel || '未分年级'
    if (!majors[m]) majors[m] = {}
    majors[m][g] = (majors[m][g] || 0) + 1
  }
  const children = Object.keys(majors)
    .sort()
    .map((m) => ({
      key: `major:${m}`,
      label: m,
      type: 'major',
      count: Object.values(majors[m]).reduce((a, b) => a + b, 0),
      children: Object.keys(majors[m])
        .sort()
        .map((g) => ({
          key: `grade:${m}:${g}`,
          label: `${g} 级`,
          type: 'grade',
          count: majors[m][g],
          major: m,
          grade: g,
        })),
    }))
  return [
    {
      key: 'root',
      label: '全校',
      type: 'root',
      count: allClassrooms.value.length,
      children,
    },
  ]
})

const filteredList = computed(() => {
  let list = allClassrooms.value
  if (activeMajor.value) list = list.filter((c) => (c.major || '未分配专业') === activeMajor.value)
  if (activeGrade.value)
    list = list.filter((c) => String(c.gradeLevel || '未分年级') === String(activeGrade.value))
  const q = keyword.value.trim().toLowerCase()
  if (q)
    list = list.filter(
      (c) => c.name?.toLowerCase().includes(q) || c.code?.toLowerCase().includes(q),
    )
  return list
})

const pagedList = computed(() => {
  const start = (page.value - 1) * pageSize
  return filteredList.value.slice(start, start + pageSize)
})

function onOrgNodeClick(data) {
  if (data.type === 'root') {
    resetOrgFilter()
    return
  }
  if (data.type === 'major') {
    activeMajor.value = data.label
    activeGrade.value = ''
    activeOrgLabel.value = data.label
  } else if (data.type === 'grade') {
    activeMajor.value = data.major
    activeGrade.value = data.grade
    activeOrgLabel.value = `${data.major} / ${data.grade}级`
  }
  page.value = 1
}

function resetOrgFilter() {
  activeMajor.value = ''
  activeGrade.value = ''
  activeOrgLabel.value = ''
  page.value = 1
  orgTreeRef.value?.setCurrentKey(null)
}

function updateStats() {
  const list = allClassrooms.value
  const majors = new Set(list.map((c) => c.major || '未分配专业'))
  const totalStudents = list.reduce((s, c) => s + (c.studentCount || 0), 0)
  classStats.value[0].value = list.length
  classStats.value[1].value = majors.size
  classStats.value[2].value = totalStudents
  classStats.value[3].value = list.length ? Math.round(totalStudents / list.length) : 0
}

async function loadClassrooms() {
  loading.value = true
  try {
    const res = await classroomApi.getClassroomList({ keyword: '' })
    const raw = res.data?.records || res.data?.list || res.data || res
    allClassrooms.value = Array.isArray(raw) ? raw : []
    updateStats()
    page.value = 1
  } catch (e) {
    logger.error('加载班级失败:', e)
    allClassrooms.value = []
    ElMessage.error('加载班级列表失败')
  } finally {
    loading.value = false
  }
}

/* ---- 新建 / 编辑 ---- */
const showEdit = ref(false)
const editing = ref(false)
const formRef = ref(null)
const form = reactive({
  id: null,
  name: '',
  code: '',
  major: '',
  gradeLevel: '',
  maxStudents: 50,
  description: '',
  isActive: true,
})
const rules = {
  name: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入班级编码', trigger: 'blur' }],
}

function openCreate() {
  editing.value = false
  Object.assign(form, {
    id: null,
    name: '',
    code: '',
    major: activeMajor.value || '',
    gradeLevel: activeGrade.value || '',
    maxStudents: 50,
    description: '',
    isActive: true,
  })
  showEdit.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    name: row.name,
    code: row.code,
    major: row.major,
    gradeLevel: row.gradeLevel,
    maxStudents: row.maxStudents || 50,
    description: row.description || '',
    isActive: row.isActive !== false,
  })
  showEdit.value = true
}

async function saveClassroom() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...form }
      if (editing.value) {
        await classroomApi.updateClassroom(form.id, payload)
        ElMessage.success('班级更新成功')
      } else {
        delete payload.id
        await classroomApi.createClassroom(payload)
        ElMessage.success('班级创建成功')
      }
      showEdit.value = false
      await loadClassrooms()
    } catch (e) {
      logger.error(e)
      ElMessage.error('保存失败：' + (e.response?.data?.message || e.message || '未知错误'))
    } finally {
      saving.value = false
    }
  })
}

function removeClassroom(row) {
  ElMessageBox.confirm(`确定删除班级「${row.name}」？该操作不可恢复。`, '删除确认', {
    type: 'warning',
  })
    .then(async () => {
      try {
        await classroomApi.deleteClassroom(row.id)
        ElMessage.success('删除成功')
        await loadClassrooms()
      } catch (e) {
        ElMessage.error('删除失败')
      }
    })
    .catch(() => {})
}

/* ---- 成员管理 ---- */
const showStudents = ref(false)
const currentClass = ref(null)
const studentList = ref([])
const studentLoading = ref(false)
const studentKeyword = ref('')

async function manageStudents(row) {
  currentClass.value = row
  studentKeyword.value = ''
  showStudents.value = true
  await loadStudents()
}

async function loadStudents() {
  if (!currentClass.value) return
  studentLoading.value = true
  try {
    const res = await classroomApi.getStudents(currentClass.value.id, {
      keyword: studentKeyword.value,
      page: 1,
      size: 200,
    })
    const raw = res.data?.records || res.data?.list || res.data || res
    studentList.value = Array.isArray(raw) ? raw : []
  } catch (e) {
    logger.error(e)
    studentList.value = []
  } finally {
    studentLoading.value = false
  }
}

function removeStudentFromClass(row) {
  ElMessageBox.confirm(`将学生「${row.fullName || row.username}」移出该班级？`, '提示')
    .then(async () => {
      try {
        await classroomApi.removeStudent(currentClass.value.id, row.id)
        ElMessage.success('已移除')
        await loadStudents()
        await loadClassrooms()
      } catch (e) {
        ElMessage.error('移除失败')
      }
    })
    .catch(() => {})
}

/* ---- 添加学生 ---- */
const showAddStudent = ref(false)
const candidateList = ref([])
const candidateLoading = ref(false)
const candidateKeyword = ref('')

const filteredCandidates = computed(() => {
  const enrolledIds = new Set(studentList.value.map((s) => s.id))
  let list = candidateList.value.filter((s) => !enrolledIds.has(s.id))
  const q = candidateKeyword.value.trim().toLowerCase()
  if (q)
    list = list.filter(
      (s) => s.fullName?.toLowerCase().includes(q) || s.username?.toLowerCase().includes(q),
    )
  return list.slice(0, 100)
})

async function openAddStudent() {
  showAddStudent.value = true
  candidateKeyword.value = ''
  if (candidateList.value.length) return
  candidateLoading.value = true
  try {
    const res = await adminApi.getUsers({ role: 'STUDENT', size: 500 })
    const raw = res.data?.records || res.data?.list || res.data || res
    candidateList.value = Array.isArray(raw) ? raw : []
  } catch (e) {
    logger.error(e)
    candidateList.value = []
  } finally {
    candidateLoading.value = false
  }
}

async function addStudentToClass(row) {
  try {
    await classroomApi.addStudent(currentClass.value.id, row.id)
    ElMessage.success(`已添加 ${row.fullName || row.username}`)
    await loadStudents()
    await loadClassrooms()
  } catch (e) {
    ElMessage.error('添加失败')
  }
}

onMounted(loadClassrooms)
</script>

<style scoped>
.class-management {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: var(--ac-spacing-lg);
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  letter-spacing: -0.3px;
}
.page-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-top: 4px;
}

/* 统计行 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}
.stat-mini-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: all var(--ac-transition-normal);
}
.stat-mini-card:hover {
  box-shadow: var(--ac-shadow-md);
  transform: translateY(-2px);
}
.stat-mini-card .label {
  font-size: 13px;
  color: var(--ac-text-muted);
  font-weight: 500;
}
.stat-mini-card .value {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
}
.stat-mini-card.admin .value {
  color: #7f1d1d;
}
.stat-mini-card.teacher .value {
  color: #166534;
}
.stat-mini-card.student .value {
  color: #1e40af;
}

/* 左右布局 */
.layout-split {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--ac-spacing-lg);
  align-items: start;
}

.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px;
}

.org-panel {
  position: sticky;
  top: 12px;
}
.org-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.org-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.org-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  font-size: 13px;
}
.org-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.org-count {
  font-size: 12px;
  color: var(--ac-text-muted);
  background: var(--ac-bg-hover);
  border-radius: 10px;
  padding: 0 8px;
  min-width: 20px;
  text-align: center;
}

.list-panel {
  min-width: 0;
}

/* 工具栏 */
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ac-spacing-md);
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.active-filter {
  font-size: 13px;
  color: var(--ac-text-muted);
}
.active-filter b {
  color: var(--ac-primary, #3b82f6);
}

.count-badge {
  font-size: 13px;
  color: var(--ac-text-regular);
  font-weight: 500;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.student-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

/* 标签列 */
:deep(.tag-col .cell) {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0;
}
:deep(.tag-col .cell .el-tag) {
  margin: 0 !important;
}

.status-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  padding: 2px 10px;
  border-radius: 20px;
  line-height: 20px;
}
.status-dot i {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot--active {
  color: #15803d;
  background: rgba(22, 163, 74, 0.08);
}
.status-dot--active i {
  background: #22c55e;
}
.status-dot--inactive {
  color: var(--ac-text-muted);
  background: var(--ac-bg-hover);
}
.status-dot--inactive i {
  background: #9ca3af;
}

/* 操作按钮 */
.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.act-btn,
.act-btn--edit,
.act-btn--approve,
.act-btn--del {
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
  font-weight: 500;
  background: transparent;
  white-space: nowrap;
}
.act-btn:hover,
.act-btn--edit:hover,
.act-btn--approve:hover,
.act-btn--del:hover {
  transform: translateY(-1px);
}
.act-btn--edit {
  color: var(--ac-primary, #3b82f6);
}
.act-btn--edit:hover {
  background: rgba(59, 130, 246, 0.08);
}
.act-btn--approve {
  color: #15803d;
}
.act-btn--approve:hover {
  background: rgba(21, 128, 61, 0.08);
}
.act-btn--del {
  color: #b05045;
}
.act-btn--del:hover {
  background: rgba(176, 80, 69, 0.08);
}

@media (max-width: 900px) {
  .layout-split {
    grid-template-columns: 1fr;
  }
  .org-panel {
    position: static;
  }
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
