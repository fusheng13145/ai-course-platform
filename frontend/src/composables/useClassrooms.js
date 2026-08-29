import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import logger from '@/utils/logger'
import classroomApi from '@/api/classroom'
import adminApi from '@/api/admin'

/**
 * 组织与班级管理（管理员视角）数据与业务逻辑
 * <p>承载班级管理页全部状态与操作：班级加载与统计（总数/专业数/在册学生/平均班额）、
 * 组织树构建（专业→年级）与筛选、关键字过滤 + 前端分页、班级新建/编辑（含表单校验）、
 * 删除确认、成员加载/移除、候选学生筛选与添加。</p>
 */
export function useClassrooms() {
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

  /** 按 专业 → 年级 动态构建组织树（含各节点班级数） */
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
      { key: 'root', label: '全校', type: 'root', count: allClassrooms.value.length, children },
    ]
  })

  /** 组织维度 + 关键字过滤后的班级列表 */
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

  /** 组织树节点点击：root 重置，major/grade 设置筛选 */
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

  /** 更新四项概览统计 */
  function updateStats() {
    const list = allClassrooms.value
    const majors = new Set(list.map((c) => c.major || '未分配专业'))
    const totalStudents = list.reduce((s, c) => s + (c.studentCount || 0), 0)
    classStats.value[0].value = list.length
    classStats.value[1].value = majors.size
    classStats.value[2].value = totalStudents
    classStats.value[3].value = list.length ? Math.round(totalStudents / list.length) : 0
  }

  /** 加载全部班级并刷新统计 */
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

  /** 保存班级（编辑/新建分流；表单校验失败不提交） */
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

  /** 删除班级（二次确认，不可恢复） */
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

  /** 打开成员管理并加载该班级学生 */
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

  /** 移除班级成员（二次确认） */
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

  /** 候选学生：排除已入班 + 关键字过滤（最多 100 条） */
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

  /** 打开添加学生（首次加载全量候选学生） */
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

  /** 添加学生入班并刷新成员列表 */
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

  return {
    loading,
    saving,
    keyword,
    page,
    pageSize,
    orgTreeRef,
    orgTree,
    filteredList,
    pagedList,
    activeOrgLabel,
    classStats,
    onOrgNodeClick,
    resetOrgFilter,
    loadClassrooms,
    showEdit,
    editing,
    formRef,
    form,
    rules,
    openCreate,
    openEdit,
    saveClassroom,
    removeClassroom,
    showStudents,
    currentClass,
    studentList,
    studentLoading,
    studentKeyword,
    manageStudents,
    loadStudents,
    removeStudentFromClass,
    showAddStudent,
    candidateList,
    candidateLoading,
    candidateKeyword,
    filteredCandidates,
    openAddStudent,
    addStudentToClass,
  }
}
