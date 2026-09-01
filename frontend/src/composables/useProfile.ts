import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import { EditPen, WarningFilled, Bell } from '@element-plus/icons-vue'
import { unwrap } from '@/utils/response'
import { formatTime, formatDate } from '@/utils/datetime'
import { monoScore } from '@/utils/chartTheme'
import studentApi from '@/api/student'
import authApi from '@/api/auth'

/** 课程封面配色（令牌色系淡彩，按序循环） */
const coursePalette = [
  {
    bg: 'linear-gradient(135deg,var(--accent-soft),var(--accent-line))',
    fg: 'var(--accent-strong)',
  },
  { bg: 'linear-gradient(135deg,var(--info-soft),var(--line-2))', fg: 'var(--info)' },
  { bg: 'linear-gradient(135deg,var(--ok-soft),var(--line-2))', fg: 'var(--ok)' },
  { bg: 'linear-gradient(135deg,var(--warn-soft),var(--line-2))', fg: 'var(--warn)' },
  { bg: 'linear-gradient(135deg,var(--violet-soft),var(--line-2))', fg: 'var(--violet)' },
  { bg: 'linear-gradient(135deg,var(--surface-3),var(--line))', fg: 'var(--ink-2)' },
]

/**
 * 个人中心（Profile）数据与表单逻辑
 * <p>聚合个人中心所需的全部状态与操作：资料表单、密码修改、学生端仪表盘聚合
 * （课程/动态/统计）、本地偏好开关，以及角色/头像展示派生数据。</p>
 *
 * @param {() => Object} getUser 获取当前登录用户对象（来自 auth store）
 * @returns 资料表单、密码表单、仪表盘数据、偏好开关与派生展示数据
 */
export function useProfile(getUser) {
  const user = computed(() => getUser() || {})

  /* ---- 资料表单 ---- */
  const form = reactive({
    username: user.value.username || '',
    fullName: user.value.fullName || '',
    email: user.value.email || '',
    phone: user.value.phone || '',
  })
  const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
  const saving = ref(false)
  const changingPassword = ref(false)

  /* ---- 仪表盘聚合数据 ---- */
  const loading = ref(false)
  const courses = ref([])
  const activities = ref([])
  const unreadCount = ref(0)
  const stats = ref({ courseCount: 0, avgScore: 0, streakDays: 0, todoCount: 0 })

  /* ---- 偏好开关（本地态） ---- */
  const notifyOn = ref(true)
  const emailOn = ref(false)

  /* ---- 角色展示 ---- */
  const roleKey = computed(() => {
    const r = user.value.role
    return r === 'ADMIN' ? 'admin' : r === 'TEACHER' ? 'teacher' : 'student'
  })
  const roleLabel = computed(() => {
    const r = user.value.role
    return r === 'ADMIN' ? '管理员' : r === 'TEACHER' ? '教师' : '学生'
  })
  const avatarText = computed(() => {
    const name = user.value.fullName || user.value.username || '?'
    return name.trim().charAt(0).toUpperCase()
  })

  const memberSince = formatDate(user.value.createdAt, '—')
  const lastLogin = formatTime(user.value.lastLoginAt, '—') || '刚刚'

  /* ---- 课程封面工具 ---- */
  function courseInitial(name) {
    return (name || '?').trim().charAt(0).toUpperCase()
  }
  function progressColor(p) {
    return monoScore(p)
  }

  /**
   * 加载仪表盘聚合数据（仅学生端；其它角色仅展示资料/安全/设置）
   * 课程项按调色板循环附加封面色（_bg/_fg）
   */
  async function loadDashboard() {
    if (user.value.role !== 'STUDENT') return
    loading.value = true
    try {
      const res = await studentApi.getStudentDashboard()
      const d = unwrap(res) || {}

      courses.value = (d.courses || []).slice(0, 6).map((c, i) => {
        const p = c.progress != null ? Math.round(Number(c.progress)) : 0
        const pal = coursePalette[i % coursePalette.length]
        return { ...c, progress: p, _bg: pal.bg, _fg: pal.fg }
      })

      stats.value = {
        courseCount: d.enrolledCourses ?? courses.value.length ?? 0,
        avgScore: d.avgScore ?? d.averageScore ?? 0,
        streakDays: d.streakDays ?? d.consecutiveDays ?? 0,
        todoCount: d.pendingTasks ?? d.todoCount ?? 0,
      }
      unreadCount.value = d.unreadCount ?? 0
      activities.value = buildActivities(d)
    } catch (e) {
      logger.warn('加载个人中心数据失败:', e)
    } finally {
      loading.value = false
    }
  }

  /** 由仪表盘数据构造学习动态（作业/考试/通知，按紧急度着色） */
  function buildActivities(d) {
    const list = []
    for (const t of (d.pendingTaskList || d.upcomingTasks || []).slice(0, 5)) {
      list.push({
        type: '作业',
        title: t.title || t.taskTitle || '待完成作业',
        due: t.dueDate || t.endTime,
        courseId: t.courseId,
        color: 'warning',
        icon: EditPen,
      })
    }
    for (const e of (d.upcomingExams || []).slice(0, 3)) {
      list.push({
        type: '考试',
        title: e.title || e.examTitle || '即将开始的考试',
        due: e.startTime || e.examTime,
        courseId: e.courseId,
        color: 'danger',
        icon: WarningFilled,
      })
    }
    for (const n of (d.notices || []).slice(0, 3)) {
      list.push({
        type: '通知',
        title: n.title || '系统通知',
        due: n.createdAt,
        courseId: n.courseId,
        color: 'primary',
        icon: Bell,
      })
    }
    return list
  }

  /** 保存基本资料（成功后同步 auth store 中的用户信息） */
  async function saveProfile() {
    saving.value = true
    try {
      await authApi.updateCurrentUser({
        fullName: form.fullName,
        email: form.email,
        phone: form.phone,
      })
      ElMessage.success('资料已更新')
    } catch (e) {
      ElMessage.error(e?.message || '保存失败')
    } finally {
      saving.value = false
    }
  }

  /** 修改密码：长度 ≥6 且两次一致 */
  async function changePassword() {
    if (passwordForm.newPassword.length < 6) {
      ElMessage.warning('新密码至少 6 位')
      return
    }
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      ElMessage.warning('两次输入的密码不一致')
      return
    }
    changingPassword.value = true
    try {
      await authApi.changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword,
      })
      ElMessage.success('密码修改成功')
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
    } catch (e) {
      ElMessage.error(e?.message || '密码修改失败')
    } finally {
      changingPassword.value = false
    }
  }

  return {
    user,
    form,
    passwordForm,
    saving,
    changingPassword,
    loading,
    courses,
    activities,
    unreadCount,
    stats,
    notifyOn,
    emailOn,
    roleKey,
    roleLabel,
    avatarText,
    memberSince,
    lastLogin,
    courseInitial,
    progressColor,
    loadDashboard,
    saveProfile,
    changePassword,
  }
}
