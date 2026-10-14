import logger from '@/utils/logger'
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import courseApi from '@/api/course'
import chapterApi from '@/api/chapter'
import enrollmentApi from '@/api/enrollment'
import learningRecordApi from '@/api/learningRecord'
import resourceApi from '@/api/resource'

/**
 * 学生课程详情领域逻辑
 * <p>课程/章节/选课并行加载、学习模式（计时+自动上报+完成标记+退出保存）、视频资源解析。</p>
 */
export function useStudentCourseDetail() {
  const route = useRoute()

  /* ---- 页面状态 ---- */
  const pageLoading = ref(false)
  const activeTab = ref('info')
  const enrolling = ref(false)
  const completing = ref(false)

  /* ---- 课程数据 ---- */
  const course = reactive({
    id: null,
    name: '加载中...',
    description: '',
    category: '',
    teacherName: '',
    credits: 0,
    difficulty: '',
  })

  /* ---- 选课状态 ---- */
  const enrollment = reactive({
    id: null,
    progress: 0,
    isActive: false,
  })

  /* ---- 章节数据 ---- */
  const chapters = ref([])

  /* ---- 分路错误态（失败可见：互不复位，页面级持久提示聚合） ---- */
  const courseError = ref('')
  const chaptersError = ref('')
  const enrollError = ref('')
  const loadError = computed(() => courseError.value || chaptersError.value || enrollError.value)

  /* ---- 学习模式状态 ---- */
  const learningMode = ref(false)
  const currentChapter = reactive({
    id: null,
    title: '',
    description: '',
    content: '',
    duration: 0,
  })
  const currentChapterVideo = ref(null)
  const learningStartTime = ref(null)
  const learningSeconds = ref(0)
  let learningTimer = null

  /* ---- 视频资源 ---- */
  function videoSrc(res) {
    if (!res?.url) return ''
    // 后端存储的相对路径 /uploads/xx 需经 /api 前缀转发（dev: Vite 代理；prod: Nginx /api location）
    return res.url.startsWith('/uploads') ? `/api${res.url}` : res.url
  }

  async function loadChapterVideo(chapterId) {
    currentChapterVideo.value = null
    if (!chapterId) return
    try {
      const res = await resourceApi.getChapterResources(chapterId)
      const list = res.data || res || []
      const arr = Array.isArray(list) ? list : []
      currentChapterVideo.value = arr.find((r) => (r.fileType || '').startsWith('video/')) || null
    } catch {
      currentChapterVideo.value = null
    }
  }

  /* ---- 计算属性 ---- */
  const estimatedDuration = computed(() => {
    return chapters.value.reduce((sum, ch) => sum + (Number(ch.duration) || 0), 0)
  })

  const learningTimeDisplay = computed(() => {
    const mins = Math.floor(learningSeconds.value / 60)
    const secs = learningSeconds.value % 60
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  })

  /* ---- 根据进度返回颜色 ---- */
  function getProgressColor(progress) {
    if (progress >= 80) return 'var(--ac-success)'
    if (progress >= 50) return 'var(--ac-primary)'
    if (progress >= 30) return 'var(--ac-warning)'
    return 'var(--ac-danger)'
  }

  /* ---- 难度类型映射 ---- */
  function getDifficultyType(difficulty) {
    const map = { 简单: 'success', 中等: 'warning', 困难: 'danger' }
    return map[difficulty] || 'info'
  }

  /* ---- 加载页面数据（三路独立归因：任一失败不影响他路展示） ---- */
  onMounted(async () => {
    pageLoading.value = true
    const courseId = route.params.id
    courseError.value = ''
    chaptersError.value = ''
    enrollError.value = ''
    const [courseRes, chapterRes, enrollRes] = await Promise.allSettled([
      courseApi.getCourseById(courseId),
      chapterApi.getChapters(courseId),
      enrollmentApi.getMyEnrollments(),
    ])

    // 课程信息
    if (courseRes.status === 'fulfilled') {
      if (courseRes.value.data) Object.assign(course, courseRes.value.data)
    } else {
      logger.error('加载课程信息失败:', courseRes.reason)
      courseError.value = '课程信息加载失败，请检查网络后重试'
    }

    // 章节信息
    if (chapterRes.status === 'fulfilled') {
      const chapterList = chapterRes.value.data || chapterRes.value || []
      chapters.value = chapterList.map((ch, _idx) => ({
        ...ch,
        completed: ch.status === 'COMPLETED',
        current: ch.status === 'IN_PROGRESS',
        progress: ch.progress ? Math.round(Number(ch.progress)) : 0,
      }))
    } else {
      logger.error('加载章节列表失败:', chapterRes.reason)
      chapters.value = []
      chaptersError.value = '章节列表加载失败，请检查网络后重试'
    }

    // 选课状态
    if (enrollRes.status === 'fulfilled') {
      const enrollments =
        enrollRes.value.data?.list || enrollRes.value.data || enrollRes.value || []
      const myEnrollment = enrollments.find(
        (e) => Number(e.courseId) === Number(courseId) && e.isActive,
      )
      if (myEnrollment) {
        enrollment.id = myEnrollment.id
        enrollment.progress = Math.round(Number(myEnrollment.progress || 0))
        enrollment.isActive = myEnrollment.isActive
      }
    } else {
      logger.error('加载选课状态失败:', enrollRes.reason)
      enrollError.value = '选课状态加载失败，请检查网络后重试'
    }

    pageLoading.value = false
  })

  /* ---- 加入课程 ---- */
  async function handleEnroll() {
    enrolling.value = true
    try {
      await enrollmentApi.enrollCourse({ courseId: Number(route.params.id) })
      ElMessage.success('加入课程成功')
      enrollment.isActive = true
      enrollment.progress = 0
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '加入课程失败')
    } finally {
      enrolling.value = false
    }
  }

  /* ---- 进入学习模式 ---- */
  function enterLearning(chapter) {
    if (!enrollment.isActive && !enrollment.id) {
      ElMessage.warning('请先加入课程')
      activeTab.value = 'info'
      return
    }

    Object.assign(currentChapter, chapter)
    learningMode.value = true
    learningStartTime.value = Date.now()
    learningSeconds.value = 0
    loadChapterVideo(chapter.id)

    // 启动计时器
    if (learningTimer) clearInterval(learningTimer)
    learningTimer = setInterval(() => {
      learningSeconds.value++
      // 每60秒自动上报一次学习记录
      if (learningSeconds.value % 60 === 0) {
        autoSaveRecord()
      }
    }, 1000)

    // 上报开始学习记录
    reportLearningStart()
  }

  /* ---- 退出学习模式 ---- */
  function exitLearning() {
    // 退出前保存学习记录
    autoSaveRecord()
    currentChapterVideo.value = null

    if (learningTimer) {
      clearInterval(learningTimer)
      learningTimer = null
    }
    learningMode.value = false
    learningSeconds.value = 0
  }

  /* ---- 清理定时器 ---- */
  onUnmounted(() => {
    if (learningTimer) clearInterval(learningTimer)
    // 修复 P1-22：离开页面时保存最终学习时长记录（fire-and-forget），
    // 原实现仅清定时器，中途退出导致本次学习数据全部丢失
    if (learningSeconds.value > 0 && currentChapter.id) {
      learningRecordApi
        .createLearningRecord({
          courseId: Number(route.params.id),
          chapterId: currentChapter.id,
          actionType: 'PROGRESS',
          duration: learningSeconds.value,
        })
        .catch(() => {})
    }
  })

  /* ---- 上报学习开始 ---- */
  async function reportLearningStart() {
    try {
      await learningRecordApi.createLearningRecord({
        courseId: Number(route.params.id),
        chapterId: currentChapter.id,
        actionType: 'START',
        duration: 0,
      })
    } catch (e) {
      logger.warn('学习记录上报失败:', e)
    }
  }

  /* ---- 自动保存学习记录 ---- */
  async function autoSaveRecord() {
    try {
      await learningRecordApi.createLearningRecord({
        courseId: Number(route.params.id),
        chapterId: currentChapter.id,
        actionType: 'PROGRESS',
        duration: learningSeconds.value,
      })
    } catch (e) {
      logger.warn('自动保存学习记录失败:', e)
    }
  }

  /* ---- 标记章节完成 ---- */
  async function markChapterComplete() {
    completing.value = true
    try {
      // 先保存最终学习记录
      await learningRecordApi.createLearningRecord({
        courseId: Number(route.params.id),
        chapterId: currentChapter.id,
        actionType: 'COMPLETE',
        duration: learningSeconds.value,
      })

      // 更新本地状态
      const idx = chapters.value.findIndex((c) => c.id === currentChapter.id)
      if (idx !== -1) {
        chapters.value[idx].completed = true
        chapters.value[idx].current = false
        chapters.value[idx].progress = 100
      }

      ElMessage.success(`「${currentChapter.title}」已完成`)

      // 自动进入下一章或返回列表
      const nextIdx = chapters.value.findIndex((c) => !c.completed)
      if (nextIdx !== -1) {
        enterLearning(chapters.value[nextIdx])
      } else {
        exitLearning()
      }
    } catch {
      ElMessage.error('标记完成失败')
    } finally {
      completing.value = false
    }
  }

  return {
    pageLoading,
    activeTab,
    enrolling,
    completing,
    course,
    enrollment,
    chapters,
    courseError,
    chaptersError,
    enrollError,
    loadError,
    learningMode,
    currentChapter,
    currentChapterVideo,
    learningTimeDisplay,
    estimatedDuration,
    videoSrc,
    getProgressColor,
    getDifficultyType,
    handleEnroll,
    enterLearning,
    exitLearning,
    markChapterComplete,
  }
}
