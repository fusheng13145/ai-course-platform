<template>
  <div class="profile-page fade-up">
    <!-- ============ 顶部封面 + 头像（参考 GitHub / Bilibili 个人空间） ============ -->
    <section class="profile-hero">
      <div class="hero-cover">
        <span class="hero-blob hero-blob--1" />
        <span class="hero-blob hero-blob--2" />
      </div>

      <div class="hero-lower">
        <div class="hero-avatar">
          <el-avatar :size="92" class="hero-avatar__img">
            {{ avatarText }}
          </el-avatar>
          <span class="hero-avatar__status" title="在线" />
        </div>

        <div class="hero-text">
          <div class="hero-name">
            {{ user.fullName || user.username }}
            <el-tag size="small" :class="['role-tag', 'role-tag--' + roleKey]">{{
              roleLabel
            }}</el-tag>
          </div>
          <div class="hero-handle">@{{ user.username }}</div>
          <div class="hero-meta">
            <span
              ><el-icon><Calendar /></el-icon> 加入于 {{ memberSince }}</span
            >
            <span
              ><el-icon><Clock /></el-icon> 最近登录 {{ lastLogin }}</span
            >
          </div>
        </div>

        <!-- 关键数据条（参考 Bilibili/YouTube 频道数据条） -->
        <div class="hero-stats">
          <div class="hero-stat">
            <div class="hero-stat__value">{{ stats.courseCount }}</div>
            <div class="hero-stat__label">在学课程</div>
          </div>
          <div class="hero-stat">
            <div class="hero-stat__value">{{ stats.avgScore }}</div>
            <div class="hero-stat__label">平均分</div>
          </div>
          <div class="hero-stat">
            <div class="hero-stat__value">{{ stats.streakDays }}<i>天</i></div>
            <div class="hero-stat__label">连续学习</div>
          </div>
          <div class="hero-stat">
            <div class="hero-stat__value">{{ stats.todoCount }}</div>
            <div class="hero-stat__label">待办事项</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 内容网格 ============ -->
    <div class="profile-grid">
      <!-- 左主列 -->
      <div class="profile-col profile-col--main">
        <!-- 我的课程（参考 Coursera / 中国大学MOOC） -->
        <div class="pc-card">
          <div class="pc-header">
            <div class="pc-title">
              <el-icon><Reading /></el-icon> 我的课程
            </div>
            <el-button text type="primary" size="small" @click="goCourses">
              查看全部<el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <div v-loading="loading" class="course-list">
            <div v-for="c in courses" :key="c.courseId" class="course-item" @click="goCourse(c)">
              <div class="course-cover" :style="{ background: c._bg }">
                <span :style="{ color: c._fg }">{{ courseInitial(c.courseName) }}</span>
              </div>
              <div class="course-main">
                <div class="course-name">{{ c.courseName }}</div>
                <el-progress
                  :percentage="c.progress"
                  :stroke-width="6"
                  :show-text="false"
                  :color="progressColor(c.progress)"
                  class="course-progress"
                />
              </div>
              <div class="course-pct">{{ c.progress }}%</div>
            </div>
            <el-empty
              v-if="!loading && courses.length === 0"
              description="还没有在学课程"
              :image-size="72"
            />
          </div>
        </div>

        <!-- 学习动态 -->
        <div class="pc-card">
          <div class="pc-header">
            <div class="pc-title">
              <el-icon><Bell /></el-icon> 学习动态
            </div>
            <el-button text type="primary" size="small" @click="goTodos">
              全部待办<el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <div v-loading="loading" class="activity-list">
            <div
              v-for="(a, i) in activities"
              :key="i"
              class="activity-item"
              @click="a.courseId && goCourse({ courseId: a.courseId })"
            >
              <div class="activity-dot" :class="'dot--' + a.color">
                <el-icon><component :is="a.icon" /></el-icon>
              </div>
              <div class="activity-body">
                <div class="activity-title">
                  <el-tag size="small" :type="a.color" effect="light">{{ a.type }}</el-tag>
                  <span class="activity-name">{{ a.title }}</span>
                </div>
                <div v-if="a.due" class="activity-due">截止 {{ formatTime(a.due, '—') }}</div>
              </div>
              <el-icon class="activity-go"><ArrowRight /></el-icon>
            </div>
            <el-empty
              v-if="!loading && activities.length === 0"
              description="暂无学习动态"
              :image-size="72"
            />
          </div>
        </div>
      </div>

      <!-- 右侧列 -->
      <div class="profile-col profile-col--side">
        <!-- 基本资料 -->
        <div class="pc-card">
          <div class="pc-header">
            <div class="pc-title">
              <el-icon><Postcard /></el-icon> 基本资料
            </div>
          </div>
          <el-form :model="form" label-position="top" class="pc-form">
            <el-form-item label="用户名">
              <el-input v-model="form.username" disabled />
            </el-form-item>
            <el-form-item label="姓名">
              <el-input v-model="form.fullName" placeholder="请输入真实姓名" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="form.email" placeholder="name@example.com" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="form.phone" placeholder="请输入手机号" />
            </el-form-item>
            <el-button type="primary" :loading="saving" class="pc-submit" @click="saveProfile">
              保存修改
            </el-button>
          </el-form>
        </div>

        <!-- 账号安全 -->
        <div class="pc-card">
          <div class="pc-header">
            <div class="pc-title">
              <el-icon><Lock /></el-icon> 账号安全
            </div>
          </div>
          <el-form :model="passwordForm" label-position="top" class="pc-form">
            <el-form-item label="当前密码">
              <el-input
                v-model="passwordForm.oldPassword"
                type="password"
                show-password
                placeholder="请输入当前密码"
              />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input
                v-model="passwordForm.newPassword"
                type="password"
                show-password
                placeholder="至少 6 位"
              />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input
                v-model="passwordForm.confirmPassword"
                type="password"
                show-password
                placeholder="再次输入新密码"
              />
            </el-form-item>
            <el-button
              type="primary"
              :loading="changingPassword"
              class="pc-submit"
              @click="changePassword"
            >
              修改密码
            </el-button>
          </el-form>
        </div>

        <!-- 偏好设置 -->
        <div class="pc-card">
          <div class="pc-header">
            <div class="pc-title">
              <el-icon><Setting /></el-icon> 偏好设置
            </div>
          </div>
          <div class="setting-row">
            <span class="setting-label">主题模式</span>
            <el-radio-group :model-value="theme" size="small" @change="setTheme">
              <el-radio-button value="light">浅色</el-radio-button>
              <el-radio-button value="dark">深色</el-radio-button>
              <el-radio-button value="system">跟随系统</el-radio-button>
            </el-radio-group>
          </div>
          <div class="setting-row">
            <span class="setting-label">学习提醒</span>
            <el-switch v-model="notifyOn" />
          </div>
          <div class="setting-row">
            <span class="setting-label">邮件通知</span>
            <el-switch v-model="emailOn" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { formatTime, formatDate } from '@/utils/datetime'
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Reading,
  Calendar,
  Clock,
  Bell,
  ArrowRight,
  Postcard,
  Lock,
  Setting,
  EditPen,
  WarningFilled,
} from '@element-plus/icons-vue'
import studentApi from '@/api/student'
import authApi from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'
import { monoScore } from '@/utils/chartTheme'

const router = useRouter()
const auth = useAuthStore()
const { theme, setTheme } = useTheme()
const user = computed(() => auth.user || {})
const u = user.value

/* ---- 资料表单 ---- */
const form = reactive({
  username: u.username || '',
  fullName: u.fullName || '',
  email: u.email || '',
  phone: u.phone || '',
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

/* ---- 偏好开关（本地态，演示用） ---- */
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

const memberSince = formatDate(u.createdAt, '—')
const lastLogin = formatTime(u.lastLoginAt, '—') || '刚刚'

/* ---- 课程封面配色（克制、协调的淡彩 · 令牌色系） ---- */
const coursePalette = [
  { bg: 'linear-gradient(135deg,var(--accent-soft),var(--accent-line))', fg: 'var(--accent-strong)' },
  { bg: 'linear-gradient(135deg,var(--info-soft),var(--line-2))', fg: 'var(--info)' },
  { bg: 'linear-gradient(135deg,var(--ok-soft),var(--line-2))', fg: 'var(--ok)' },
  { bg: 'linear-gradient(135deg,var(--warn-soft),var(--line-2))', fg: 'var(--warn)' },
  { bg: 'linear-gradient(135deg,var(--violet-soft),var(--line-2))', fg: 'var(--violet)' },
  { bg: 'linear-gradient(135deg,var(--surface-3),var(--line))', fg: 'var(--ink-2)' },
]
function courseInitial(name) {
  return (name || '?').trim().charAt(0).toUpperCase()
}
function progressColor(p) {
  return monoScore(p)
}

/* ---- 数据加载（仅学生端聚合，其它角色仅保留资料/安全/设置） ---- */
async function loadDashboard() {
  if (user.value.role !== 'STUDENT') return
  loading.value = true
  try {
    const res = await studentApi.getStudentDashboard()
    const d = unwrap(res) || {}

    courses.value = (d.courses || []).slice(0, 6).map((c, i) => {
      const p = c.progress != null ? Math.round(Number(c.progress)) : 0
      const pal = coursePalette[i % coursePalette.length]
      return {
        courseId: c.courseId,
        courseName: c.courseName || `课程 #${c.courseId}`,
        progress: p,
        _bg: pal.bg,
        _fg: pal.fg,
      }
    })

    const t = d.todos || {}
    const acts = []
    ;(t.assignments || []).forEach((a) =>
      acts.push({
        type: '作业',
        title: a.title,
        courseId: a.courseId,
        due: a.dueDate,
        icon: EditPen,
        color: 'warning',
      }),
    )
    ;(t.exams || []).forEach((e) =>
      acts.push({
        type: '考试',
        title: e.title,
        courseId: e.courseId,
        icon: WarningFilled,
        color: 'danger',
      }),
    )
    ;(t.signIns || []).forEach((s) =>
      acts.push({
        type: '签到',
        title: s.title,
        courseId: s.courseId,
        icon: Calendar,
        color: 'primary',
      }),
    )
    activities.value = acts

    const summary = d.summary || {}
    const analytics = d.analytics || {}
    const learningStats = d.learningStats || {}
    stats.value = {
      courseCount: d.courseCount ?? summary.courseCount ?? courses.value.length,
      avgScore: analytics.averageScore != null ? Math.round(analytics.averageScore) : 0,
      streakDays: learningStats.consecutiveDays ?? learningStats.streakDays ?? 0,
      todoCount:
        (summary.pendingAssignmentCount ?? 0) +
        (summary.upcomingExamCount ?? 0) +
        (summary.pendingSignInCount ?? 0),
    }
    unreadCount.value = d.unreadCount ?? summary.unreadCount ?? 0
  } catch (e) {
    logger.error('加载个人中心数据失败:', e)
  } finally {
    loading.value = false
  }
}

/* ---- 保存资料 ---- */
async function saveProfile() {
  saving.value = true
  try {
    await authApi.updateCurrentUser({
      fullName: form.fullName,
      email: form.email,
      phone: form.phone,
    })
    // 同步到全局 store 与本地存储（非敏感 user 对象；令牌由 HttpOnly Cookie 管理）
    auth.user = { ...auth.user, fullName: form.fullName, email: form.email, phone: form.phone }
    const storage = auth.persistent ? localStorage : sessionStorage
    storage.setItem('user', JSON.stringify(auth.user))
    ElMessage.success('资料已保存')
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/* ---- 修改密码 ---- */
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

/* ---- 跳转 ---- */
function goCourses() {
  router.push('/student/courses')
}
function goCourse(c) {
  if (c?.courseId) router.push(`/student/courses/${c.courseId}`)
}
function goTodos() {
  router.push('/student/notifications')
}

onMounted(loadDashboard)
</script>

<style scoped>
.profile-page {
  max-width: 1180px;
  margin: 0 auto;
  padding-bottom: var(--ac-spacing-xl);
}

/* ---------------- 封面 + 头像 ---------------- */
.profile-hero {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
  overflow: hidden;
  margin-bottom: var(--ac-spacing-lg);
}
.hero-cover {
  position: relative;
  height: 128px;
  background: radial-gradient(circle at 16% 28%, rgba(255, 255, 255, 0.2), transparent 42%),
    radial-gradient(circle at 84% 72%, rgba(255, 255, 255, 0.12), transparent 46%),
    linear-gradient(120deg, var(--ac-primary) 0%, var(--accent-strong) 100%);
  overflow: hidden;
}
.hero-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
}
.hero-blob--1 {
  width: 180px;
  height: 180px;
  right: -40px;
  top: -70px;
  background: rgba(255, 255, 255, 0.14);
}
.hero-blob--2 {
  width: 120px;
  height: 120px;
  left: 8%;
  bottom: -60px;
  background: rgba(255, 255, 255, 0.1);
}
.hero-lower {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 28px 22px;
  margin-top: -42px;
}
.hero-avatar {
  position: relative;
  flex-shrink: 0;
}
.hero-avatar__img {
  border: 4px solid var(--ac-bg-elevated);
  background: linear-gradient(135deg, var(--ac-primary), var(--accent-strong)) !important;
  color: #fff !important;
  font-size: 34px;
  font-weight: 700;
  box-shadow: var(--ac-shadow-md);
}
.hero-avatar__status {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--ac-success);
  border: 3px solid var(--ac-bg-elevated);
}
.hero-text {
  min-width: 0;
  padding-bottom: 4px;
}
.hero-name {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  letter-spacing: -0.3px;
}
.hero-handle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--ac-text-muted);
}
.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}
.hero-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.hero-stats {
  margin-left: auto;
  display: flex;
  gap: 30px;
  padding-bottom: 4px;
}
.hero-stat {
  text-align: center;
  min-width: 56px;
}
.hero-stat__value {
  font-size: 24px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1.1;
  letter-spacing: -0.5px;
}
.hero-stat__value i {
  font-size: 13px;
  font-style: normal;
  font-weight: 500;
  color: var(--ac-text-muted);
  margin-left: 2px;
}
.hero-stat__label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ac-text-muted);
}

/* ---------------- 网格 ---------------- */
.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr);
  gap: var(--ac-spacing-lg);
  align-items: start;
}
.profile-col {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-lg);
  min-width: 0;
}

/* ---------------- 卡片 ---------------- */
.pc-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
  padding: var(--ac-spacing-lg);
  transition:
    box-shadow var(--ac-transition-normal),
    transform var(--ac-transition-normal);
}
.pc-card:hover {
  box-shadow: var(--ac-shadow-md);
}
.pc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ac-spacing-md);
}
.pc-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.pc-title .el-icon {
  color: var(--ac-primary);
}
.pc-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
.pc-form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}
.pc-submit {
  width: 100%;
  margin-top: 4px;
}

/* ---------------- 我的课程 ---------------- */
.course-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.course-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px;
  border-radius: var(--ac-radius-md);
  cursor: pointer;
  transition:
    background var(--ac-transition-fast),
    transform var(--ac-transition-fast);
}
.course-item:hover {
  background: var(--ac-bg-hover);
  transform: translateX(2px);
}
.course-cover {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
}
.course-main {
  flex: 1;
  min-width: 0;
}
.course-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.course-progress {
  max-width: 100%;
}
.course-pct {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ac-text-secondary);
  width: 42px;
  text-align: right;
}

/* ---------------- 学习动态 ---------------- */
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.activity-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 12px;
  border-radius: var(--ac-radius-md);
  cursor: pointer;
  transition: background var(--ac-transition-fast);
}
.activity-item:hover {
  background: var(--ac-bg-hover);
}
.activity-dot {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.activity-dot .el-icon {
  font-size: 17px;
}
.dot--primary {
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
}
.dot--warning {
  background: var(--ac-warning-bg);
  color: var(--ac-warning);
}
.dot--danger {
  background: var(--ac-danger-bg);
  color: var(--ac-danger);
}
.activity-body {
  flex: 1;
  min-width: 0;
}
.activity-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  color: var(--ac-text-primary);
}
.activity-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.activity-due {
  margin-top: 3px;
  font-size: 12px;
  color: var(--ac-text-muted);
}
.activity-go {
  flex-shrink: 0;
  color: var(--ac-text-muted);
  font-size: 16px;
}

/* ---------------- 偏好设置 ---------------- */
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--ac-border);
}
.setting-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.setting-label {
  font-size: 13.5px;
  color: var(--ac-text-secondary);
}

/* ---------------- 角色标签 ---------------- */
.role-tag {
  border: none;
  font-weight: 600;
}
.role-tag--student {
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
}
.role-tag--teacher {
  background: var(--ac-warning-bg);
  color: var(--ac-warning);
}
.role-tag--admin {
  background: var(--ac-danger-bg);
  color: var(--ac-danger);
}

/* ---------------- 入场动画 ---------------- */
.fade-up {
  animation: fadeUp 0.45s cubic-bezier(0.4, 0, 0.2, 1) both;
}
@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 980px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  .hero-lower {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
    margin-top: -46px;
  }
  .hero-stats {
    margin-left: 0;
    width: 100%;
    justify-content: space-between;
    gap: 12px;
    padding-top: 14px;
    border-top: 1px solid var(--ac-border);
  }
  .hero-stat {
    min-width: 0;
  }
}
</style>
