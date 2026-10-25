import { ref, reactive, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import courseApi from '@/api/course'
import chapterApi from '@/api/chapter'
import enrollmentApi from '@/api/enrollment'
import notificationApi from '@/api/notification'
import resourceApi from '@/api/resource'
import taskApi from '@/api/task'
import examApi from '@/api/exam'
import { pickList } from '@/utils/response'
import { confirmDanger } from '@/utils/dangerConfirm'
import { useCourseDetailChapters } from './useCourseDetailChapters'
import { useCourseDetailNotices } from './useCourseDetailNotices'

/** 文案映射 */
const STATUS_TEXT = { APPROVED: '已通过', REJECTED: '已拒绝' }
const DIFF_NAME = { BEGINNER: '入门', EASY: '初级', MEDIUM: '中级', HARD: '高级', EXPERT: '专家' }
const ENROLL_NAME = { OPEN: '开放报名', APPROVAL: '需审核', RESTRICTED: '限制报名' }

/**
 * 课程详情（教师）数据与业务逻辑（编排层）
 * <p>承载课程/学生/统计等并行加载（Promise.allSettled 容错）、课程编辑、学生选课审核
 * 与文案工具；章节 CRUD 与拖拽排序拆见 useCourseDetailChapters，通知发送/删除拆见
 * useCourseDetailNotices。</p>
 */
export function useCourseDetail() {
  const route = useRoute()
  const activeTab = ref('info')

  /* ---- 课程数据 ---- */
  const course = reactive({
    id: null,
    name: '',
    code: '',
    category: '',
    description: '',
    credits: null,
    difficulty: '',
    approvalStatus: '',
    enrollmentType: '',
    maxStudents: null,
    semester: '',
    year: '',
    startDate: '',
    endDate: '',
  })
  const stats = reactive({ totalStudents: null })
  const resourceCount = ref(null)
  const taskCount = ref(null)
  const examCount = ref(null)

  const chapters = ref([])
  const students = ref([])
  const notifications = ref([])
  const chaptersLoading = ref(false)
  const studentsLoading = ref(false)
  const notificationsLoading = ref(false)

  /* ---- 分路错误态（失败可见：互不复位，页面级持久提示聚合） ---- */
  const courseError = ref('')
  const chaptersError = ref('')
  const studentsError = ref('')
  const notificationsError = ref('')
  const loadError = computed(
    () =>
      courseError.value || chaptersError.value || studentsError.value || notificationsError.value,
  )

  /* ---- 对话框状态 ---- */
  const categories = ref([
    '编程开发',
    '人工智能',
    '数据分析',
    '设计创意',
    '数学物理',
    '语言文学',
    '其他',
  ])

  const editDialogVisible = ref(false)
  const editForm = reactive({
    name: '',
    code: '',
    category: '',
    description: '',
    credits: null,
    difficulty: 'MEDIUM',
    enrollmentType: 'OPEN',
    maxStudents: 100,
  })
  const savingCourse = ref(false)

  async function loadCourse() {
    courseError.value = ''
    try {
      const res = await courseApi.getCourseById(course.id)
      if (res.data) Object.assign(course, res.data)
    } catch (e) {
      logger.warn('[CourseDetail] loadCourse 失败:', e.message || e)
      courseError.value = '课程信息加载失败，请检查网络后重试'
    }
  }

  async function loadChapters() {
    chaptersLoading.value = true
    chaptersError.value = ''
    try {
      const res = await chapterApi.getChapters(course.id)
      chapters.value = pickList(res.data)
    } catch (e) {
      logger.warn('[CourseDetail] loadChapters 失败:', e.message || e)
      chapters.value = []
      chaptersError.value = '章节列表加载失败，请检查网络后重试'
    } finally {
      chaptersLoading.value = false
      chapterDomain.syncBaseline(chapters.value)
    }
  }

  async function loadStudents() {
    studentsLoading.value = true
    studentsError.value = ''
    try {
      students.value = await courseApi.getAllStudents(course.id)
    } catch (e) {
      logger.warn('[CourseDetail] loadStudents 失败:', e.message || e)
      students.value = []
      studentsError.value = '学生列表加载失败，请检查网络后重试'
    } finally {
      studentsLoading.value = false
    }
  }

  async function loadNotifications() {
    notificationsLoading.value = true
    notificationsError.value = ''
    try {
      const res = await notificationApi.getNotificationList()
      notifications.value = pickList(res.data)
    } catch (e) {
      logger.warn('[CourseDetail] loadNotifications 失败:', e.message || e)
      notifications.value = []
      notificationsError.value = '通知列表加载失败，请检查网络后重试'
    } finally {
      notificationsLoading.value = false
    }
  }

  async function loadStats() {
    try {
      const res = await courseApi.getStats(course.id)
      if (res.data) Object.assign(stats, res.data)
    } catch (e) {
      logger.warn('[CourseDetail] loadStats 失败:', e.message || e)
    }
  }

  async function loadResourceCount() {
    try {
      const res = await resourceApi.getResourceList(course.id)
      resourceCount.value = pickList(res.data).length
    } catch {
      resourceCount.value = null
    }
  }
  async function loadTaskCount() {
    try {
      taskCount.value = (await taskApi.getAllTasks({ courseId: course.id })).length
    } catch {
      taskCount.value = null
    }
  }
  async function loadExamCount() {
    try {
      examCount.value = (await examApi.getAllExamPapers({ courseId: course.id })).length
    } catch {
      examCount.value = null
    }
  }

  function updateResourceCount() {
    loadResourceCount()
  }
  function updateTaskCount() {
    loadTaskCount()
  }
  function updateExamCount() {
    loadExamCount()
  }

  const chapterDomain = useCourseDetailChapters({ course, chapters, loadChapters })
  const noticeDomain = useCourseDetailNotices({ course, notifications, loadNotifications })

  /** 初始化：路由参数变化时并行加载全部子模块（单项失败不阻塞） */
  async function initData() {
    const courseId = route.params.id
    if (!courseId) {
      ElMessage.error('缺少课程 ID')
      return
    }
    course.id = courseId
    activeTab.value = 'info'
    Object.assign(course, {
      name: '',
      code: '',
      category: '',
      description: '',
      credits: null,
      difficulty: '',
      approvalStatus: '',
      enrollmentType: '',
      maxStudents: null,
      semester: '',
      year: '',
      startDate: '',
      endDate: '',
    })
    stats.totalStudents = null
    resourceCount.value = null
    taskCount.value = null
    examCount.value = null
    chapters.value = []
    students.value = []
    notifications.value = []
    courseError.value = ''
    chaptersError.value = ''
    studentsError.value = ''
    notificationsError.value = ''

    await Promise.allSettled([
      loadCourse(),
      loadChapters(),
      loadStudents(),
      loadNotifications(),
      loadStats(),
      loadResourceCount(),
      loadTaskCount(),
      loadExamCount(),
    ])
  }

  /* ---- 文案工具 ---- */
  function getStatusText(s) {
    return STATUS_TEXT[s] || '审核中'
  }
  function getDifficultyName(d) {
    return DIFF_NAME[d] || d || '未设置'
  }
  function getEnrollmentTypeName(t) {
    return ENROLL_NAME[t] || t || '开放报名'
  }
  function formatDate(d) {
    return d ? new Date(d).toLocaleDateString('zh-CN') : '–'
  }
  function formatDateTime(d) {
    return d ? new Date(d).toLocaleString('zh-CN') : '–'
  }

  /* ---- 编辑课程 ---- */
  function showEditDialog() {
    Object.assign(editForm, {
      name: course.name || '',
      code: course.code || '',
      category: course.category || '',
      description: course.description || '',
      credits: course.credits ?? 0,
      difficulty: course.difficulty || 'MEDIUM',
      enrollmentType: course.enrollmentType || 'OPEN',
      maxStudents: course.maxStudents ?? 100,
    })
    editDialogVisible.value = true
  }
  async function saveCourse() {
    if (!editForm.name.trim()) return ElMessage.warning('请输入课程名称')
    savingCourse.value = true
    try {
      await courseApi.updateCourse(course.id, editForm)
      Object.assign(course, editForm)
      editDialogVisible.value = false
      ElMessage.success('课程信息已更新')
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '保存失败')
    } finally {
      savingCourse.value = false
    }
  }

  /* ---- 学生操作 ---- */
  async function approveStudent(row) {
    try {
      await enrollmentApi.approveEnrollment(row.id)
      row.approvalStatus = 'APPROVED'
      ElMessage.success('已通过')
    } catch {
      ElMessage.error('操作失败')
    }
  }
  async function rejectStudent(row) {
    try {
      // D2-2：明确影响范围（学生无法进入课程）与恢复路径（重新提交申请）
      const ok = await confirmDanger({
        verb: '拒绝',
        target: '选课申请',
        impact: '该学生将无法进入课程学习',
        irreversible: false,
        note: '学生重新提交申请后可再次审核',
        confirmText: '确定拒绝',
      })
      if (!ok) return
      await enrollmentApi.rejectEnrollment(row.id)
      row.approvalStatus = 'REJECTED'
      ElMessage.success('已拒绝')
    } catch (e) {
      if (e !== 'cancel') ElMessage.error('操作失败')
    }
  }

  return {
    route,
    activeTab,
    course,
    stats,
    resourceCount,
    taskCount,
    examCount,
    chapters,
    students,
    notifications,
    chaptersLoading,
    studentsLoading,
    notificationsLoading,
    courseError,
    chaptersError,
    studentsError,
    notificationsError,
    loadError,
    categories,
    editDialogVisible,
    editForm,
    savingCourse,
    chapterDialogVisible: chapterDomain.chapterDialogVisible,
    editingChapter: chapterDomain.editingChapter,
    chapterForm: chapterDomain.chapterForm,
    savingChapter: chapterDomain.savingChapter,
    dragChanged: chapterDomain.dragChanged,
    savingOrder: chapterDomain.savingOrder,
    notificationDialogVisible: noticeDomain.notificationDialogVisible,
    notificationForm: noticeDomain.notificationForm,
    sendingNotification: noticeDomain.sendingNotification,
    initData,
    updateResourceCount,
    updateTaskCount,
    updateExamCount,
    getStatusText,
    getDifficultyName,
    getEnrollmentTypeName,
    formatDate,
    formatDateTime,
    showEditDialog,
    saveCourse,
    showAddChapterDialog: chapterDomain.showAddChapterDialog,
    editChapter: chapterDomain.editChapter,
    saveChapter: chapterDomain.saveChapter,
    deleteChapter: chapterDomain.deleteChapter,
    saveChapterOrder: chapterDomain.saveChapterOrder,
    resetChapterOrder: chapterDomain.resetChapterOrder,
    approveStudent,
    rejectStudent,
    showNotificationDialog: noticeDomain.showNotificationDialog,
    sendNotification: noticeDomain.sendNotification,
    deleteNotification: noticeDomain.deleteNotification,
  }
}
