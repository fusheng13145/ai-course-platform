<template>
  <!-- 学生个人中心：基本资料 / 安全 / 偏好 + 扩展三面：数据概览 / 我的课程 / 学习动态 -->
  <section class="profile-body">
    <div v-if="loadError" class="callout danger" role="alert">
      <span>ERROR / 资料</span>
      <p>{{ loadError }}</p>
      <button class="btn btn-sm" :disabled="profileLoading" @click="loadProfile">重新加载</button>
    </div>
    <div v-else-if="notice" class="callout" role="status">
      <span>INFO / 资料</span>
      <p>{{ notice }}</p>
      <button class="btn btn-sm" :disabled="profileLoading" @click="loadProfile">重新加载</button>
    </div>

    <div class="profile-grid">
      <section class="card profile-card basic-card">
        <header class="card-head">
          <div>
            <p class="card-kicker">PROFILE / BASIC</p>
            <h2>基本资料</h2>
          </div>
          <span v-if="roleLabel" class="chip chip-mute">{{ roleLabel }}</span>
        </header>

        <div class="profile-identity">
          <span class="profile-avatar" aria-hidden="true">{{ avatarText }}</span>
          <div class="profile-identity-main">
            <b>{{ displayName }}</b>
            <p>{{ profile.username || '—' }}</p>
          </div>
        </div>

        <dl class="profile-static">
          <div>
            <dt>用户名</dt>
            <dd>{{ profile.username || '—' }}</dd>
          </div>
          <div v-if="isStudent">
            <dt>学号</dt>
            <dd>{{ profile.studentId || '—' }}</dd>
          </div>
          <div v-if="profile.major">
            <dt>专业</dt>
            <dd>{{ profile.major }}</dd>
          </div>
          <div v-if="profile.grade">
            <dt>年级</dt>
            <dd>{{ profile.grade }}</dd>
          </div>
          <div v-if="profile.department">
            <dt>院系</dt>
            <dd>{{ profile.department }}</dd>
          </div>
        </dl>

        <p class="profile-hint">
          <template v-if="isStudent">用户名、学号由管理员维护；</template>
          <template v-else>用户名由管理员维护；</template>
          姓名、邮箱、手机号可自助修改，留空表示不修改。
        </p>

        <div class="form-grid">
          <label class="field">
            <span>姓名</span>
            <input
              v-model="form.fullName"
              class="input"
              data-field="fullName"
              maxlength="50"
              autocomplete="name"
            />
          </label>
          <label class="field">
            <span>邮箱</span>
            <input
              v-model="form.email"
              class="input"
              data-field="email"
              type="email"
              autocomplete="email"
            />
          </label>
          <label class="field span-2">
            <span>手机号</span>
            <input
              v-model="form.phone"
              class="input"
              data-field="phone"
              maxlength="11"
              autocomplete="tel"
              placeholder="11 位手机号"
            />
          </label>
        </div>
        <p v-if="saveError" class="form-error" role="alert">{{ saveError }}</p>
        <div class="profile-actions">
          <button class="btn btn-primary" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存修改' }}
          </button>
        </div>
      </section>

      <div class="profile-col">
        <section class="card profile-card security-card">
          <header class="card-head">
            <div>
              <p class="card-kicker">PROFILE / SECURITY</p>
              <h2>安全</h2>
            </div>
          </header>
          <p class="profile-hint">
            修改密码需验证当前密码；新密码 8–32 位且同时包含大写字母、小写字母、数字和特殊字符。
            修改成功后当前会话会失效，需要重新登录。
          </p>
          <div class="form-grid">
            <label class="field span-2">
              <span>当前密码</span>
              <input
                v-model="password.oldPassword"
                class="input"
                data-field="oldPassword"
                type="password"
                autocomplete="current-password"
              />
            </label>
            <label class="field">
              <span>新密码</span>
              <input
                v-model="password.newPassword"
                class="input"
                data-field="newPassword"
                type="password"
                autocomplete="new-password"
              />
            </label>
            <label class="field">
              <span>确认新密码</span>
              <input
                v-model="password.confirmPassword"
                class="input"
                data-field="confirmPassword"
                type="password"
                autocomplete="new-password"
              />
            </label>
          </div>
          <p v-if="passwordError" class="form-error" role="alert">{{ passwordError }}</p>
          <div class="profile-actions">
            <button class="btn btn-primary" :disabled="changing" @click="changePassword">
              {{ changing ? '提交中…' : '修改密码' }}
            </button>
          </div>
          <p class="profile-meta">上次登录：{{ lastLoginText }}</p>
        </section>

        <section class="card profile-card preference-card">
          <header class="card-head">
            <div>
              <p class="card-kicker">PROFILE / PREFERENCE</p>
              <h2>偏好</h2>
            </div>
          </header>
          <p class="profile-hint">主题跟随新版界面的主题开关，保存在本机浏览器。</p>
          <div class="theme-switch" role="group" aria-label="主题">
            <button
              type="button"
              :class="{ 'is-active': theme === 'light' }"
              :aria-pressed="theme === 'light'"
              @click="setTheme('light')"
            >
              浅色
            </button>
            <button
              type="button"
              :class="{ 'is-active': theme === 'dark' }"
              :aria-pressed="theme === 'dark'"
              @click="setTheme('dark')"
            >
              深色
            </button>
          </div>
          <p class="profile-meta">当前主题：{{ theme === 'dark' ? '深色' : '浅色' }}</p>
        </section>
      </div>
    </div>

    <!-- 扩展三面（M-06）：数据概览 / 我的课程 / 学习动态 -->
    <div class="profile-extra">
      <section class="card profile-card stats-card">
        <header class="card-head">
          <div>
            <p class="card-kicker">PROFILE / OVERVIEW</p>
            <h2>数据概览</h2>
          </div>
        </header>
        <div class="stat-grid">
          <button type="button" class="profile-stat" @click="openCourses">
            <span class="profile-stat-num">{{ courseList.length }}</span>
            <span class="profile-stat-label">在学课程</span>
          </button>
          <button type="button" class="profile-stat" @click="openAssignments">
            <span class="profile-stat-num">{{ pendingAssignments.length }}</span>
            <span class="profile-stat-label">待办作业</span>
          </button>
          <button type="button" class="profile-stat" @click="openNotifications">
            <span class="profile-stat-num">{{ unreadCount }}</span>
            <span class="profile-stat-label">未读通知</span>
          </button>
          <button type="button" class="profile-stat" @click="openAssignments">
            <span class="profile-stat-num">{{ completedAssignments.length }}</span>
            <span class="profile-stat-label">已完成</span>
          </button>
        </div>
      </section>

      <section class="card profile-card courses-card">
        <header class="card-head">
          <div>
            <p class="card-kicker">PROFILE / COURSES</p>
            <h2>我的课程</h2>
          </div>
          <button type="button" class="btn btn-sm" @click="openCourses">查看全部</button>
        </header>
        <div v-if="coursesLoading" class="profile-loading">加载中…</div>
        <p v-else-if="courseList.length === 0" class="profile-empty">还没有在学课程</p>
        <ul v-else class="profile-course-list">
          <li
            v-for="c in courseList"
            :key="c.id"
            class="profile-course-item"
            role="button"
            tabindex="0"
            @click="openCourse(c)"
            @keyup.enter="openCourse(c)"
          >
            <span class="course-cover" :data-tone="c.tone">{{ c.short }}</span>
            <div class="profile-course-info">
              <div class="course-name">{{ c.name }}</div>
              <span class="course-bar"
                ><i
                  :style="{
                    width: clampPct(c.progress) + '%',
                    background: progressColor(c.progress),
                  }"
                ></i
              ></span>
            </div>
            <span class="course-progress">{{ clampPct(c.progress) }}<small>%</small></span>
          </li>
        </ul>
      </section>

      <section class="card profile-card activity-card">
        <header class="card-head">
          <div>
            <p class="card-kicker">PROFILE / ACTIVITY</p>
            <h2>学习动态</h2>
          </div>
          <button type="button" class="btn btn-sm" @click="openAssignments">全部待办</button>
        </header>
        <div v-if="feedLoading" class="profile-loading">加载中…</div>
        <p v-else-if="activityFeed.length === 0" class="profile-empty">暂无学习动态</p>
        <ul v-else class="profile-feed-list">
          <li
            v-for="(a, i) in activityFeed"
            :key="i"
            class="profile-feed-item"
            :class="{ clickable: a.courseId != null }"
            role="button"
            tabindex="0"
            @click="a.courseId != null && openCourseById(a.courseId)"
            @keyup.enter="a.courseId != null && openCourseById(a.courseId)"
          >
            <span class="feed-dot" :style="{ background: feedColor(a.color) }"></span>
            <div class="profile-feed-main">
              <div class="profile-feed-title">
                <span class="chip" :class="'chip-' + a.color">{{ a.type }}</span>
                <span class="profile-feed-name">{{ a.title }}</span>
              </div>
              <div v-if="a.due" class="profile-feed-due">截止 {{ a.due }}</div>
            </div>
          </li>
        </ul>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUi } from '../../composables/ui'
import authApi from '@/api/auth'
import userApi from '@/api/user'
import studentApi from '@/api/student'
import { unwrap, pickList } from '@/utils/response'
import logger from '@/utils/logger'
import { loadCourses, loadNotifications, type CourseCard, type Notification } from '../../data/api'
import type { Role } from '../../nav'

interface ProfileUser {
  id?: number | string | null
  username?: string
  fullName?: string
  email?: string
  phone?: string
  studentId?: string
  major?: string
  grade?: string
  department?: string
  className?: string
  role?: string
  lastLogin?: string
  createdAt?: string
}

const ui = useUi()
const router = useRouter()
const { toast, theme, setTheme } = ui

const cached = ((ui.auth?.user as ProfileUser | null) ?? {}) as ProfileUser
const profile = ref<ProfileUser>({ ...cached })
const form = reactive({
  fullName: cached.fullName ?? '',
  email: cached.email ?? '',
  phone: cached.phone ?? '',
})
const password = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const saving = ref(false)
const changing = ref(false)
const profileLoading = ref(false)
const saveError = ref('')
const passwordError = ref('')
const notice = ref('')
const loadError = ref('')

// —— 扩展三面（M-06）：数据概览 / 我的课程 / 学习动态 ——
const courseList = ref<CourseCard[]>([])
const pendingAssignments = ref<Task[]>([])
const completedAssignments = ref<Task[]>([])
const notifications = ref<Notification[]>([])
const coursesLoading = ref(false)
const feedLoading = ref(false)

interface Task {
  id?: number | string
  title?: string
  taskType?: string
  courseId?: number | string | null
  dueDate?: string
  status?: string
}

const roleText = computed(() => String(profile.value.role ?? '').toUpperCase())
const isStudent = computed(() => roleText.value === '' || roleText.value === 'STUDENT')
const displayName = computed(() => profile.value.fullName || profile.value.username || '未登录')
const avatarText = computed(() => displayName.value.trim().slice(0, 1) || '云')
const roleLabel = computed(() => {
  const raw = roleText.value
  if (raw === 'ADMIN') return '管理员'
  if (raw === 'TEACHER') return '教师'
  if (raw === 'STUDENT') return '学生'
  return ''
})
const lastLoginText = computed(() => fmtTime(profile.value.lastLogin))

function fmtTime(value: unknown): string {
  const text = String(value ?? '')
  return text ? text.replace('T', ' ').slice(0, 16) : '—'
}

function currentUserId(): number | string | null {
  const raw = ui.auth?.userId ?? profile.value.id ?? cached.id
  return raw == null || raw === '' ? null : (raw as number | string)
}

function applyProfile(next: ProfileUser) {
  // 资料接口是权威来源：不把登录缓存的旧值补进已下发为空的字段
  profile.value = { ...next }
  form.fullName = profile.value.fullName ?? ''
  form.email = profile.value.email ?? ''
  form.phone = profile.value.phone ?? ''
}

/** 资料写成功后同步会话缓存，避免刷新后侧栏仍显示旧姓名。 */
function syncAuthUser(next: ProfileUser) {
  const auth = ui.auth
  if (!auth) return
  auth.user = next
  try {
    if (window.localStorage.getItem('user')) {
      window.localStorage.setItem('user', JSON.stringify(next))
    }
    if (window.sessionStorage.getItem('user')) {
      window.sessionStorage.setItem('user', JSON.stringify(next))
    }
  } catch {
    // 存储不可用时只保留内存态，不影响已提交成功的资料
  }
}

async function loadProfile() {
  notice.value = ''
  loadError.value = ''
  const id = currentUserId()
  if (id == null) {
    notice.value = '未获取到当前账号标识，以下展示登录会话中的资料。'
    return
  }
  profileLoading.value = true
  try {
    const payload = unwrap<ProfileUser | null>(await userApi.getProfile(id))
    if (payload && typeof payload === 'object') applyProfile(payload)
  } catch (reason) {
    logger.error('加载个人信息失败', reason)
    loadError.value = '详细资料未能加载，以下展示登录会话中的资料。'
  } finally {
    profileLoading.value = false
  }
}

async function save() {
  saveError.value = ''
  const fullName = form.fullName.trim()
  const email = form.email.trim()
  const phone = form.phone.trim()
  if (!fullName) {
    saveError.value = '请填写姓名'
    return
  }
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    saveError.value = '邮箱格式不正确'
    return
  }
  if (phone && !/^1[3-9]\d{9}$/.test(phone)) {
    saveError.value = '手机号格式不正确'
    return
  }
  saving.value = true
  try {
    // 后端 UserUpdateDTO 白名单：fullName / phone / email / avatar / bio。
    // 留空表示“不修改”，不能把空字符串当清空下发。
    const payload: Record<string, string> = { fullName }
    if (email) payload.email = email
    if (phone) payload.phone = phone
    const updated = unwrap<ProfileUser | null>(await authApi.updateCurrentUser(payload))
    const patch: ProfileUser = { fullName }
    if (email) patch.email = email
    if (phone) patch.phone = phone
    const merged: ProfileUser = {
      ...profile.value,
      ...(updated && typeof updated === 'object' ? updated : {}),
      ...patch,
    }
    profile.value = merged
    form.email = merged.email ?? ''
    syncAuthUser(merged)
    toast('资料已更新', 'ok')
  } catch (reason) {
    logger.error('保存个人信息失败', reason)
    saveError.value = '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

async function changePassword() {
  passwordError.value = ''
  const oldPassword = password.oldPassword
  const newPassword = password.newPassword
  if (!oldPassword) {
    passwordError.value = '请输入当前密码'
    return
  }
  if (newPassword.length < 8 || newPassword.length > 32) {
    passwordError.value = '新密码需为 8–32 位'
    return
  }
  if (
    !/[a-z]/.test(newPassword) ||
    !/[A-Z]/.test(newPassword) ||
    !/\d/.test(newPassword) ||
    !/[~!@#$%^&*()_+\-={}[\]:;"'<>,.?/\\|`]/.test(newPassword)
  ) {
    passwordError.value = '新密码需同时包含大小写字母、数字和特殊字符'
    return
  }
  if (newPassword !== password.confirmPassword) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }
  changing.value = true
  try {
    // 后端 ChangePasswordDTO 三个字段都必填（confirmPassword 同步下发做服务端二次校验）
    await authApi.changePassword({
      oldPassword,
      newPassword,
      confirmPassword: password.confirmPassword,
    })
    password.oldPassword = ''
    password.newPassword = ''
    password.confirmPassword = ''
    toast('密码已修改，请重新登录', 'ok')
    // 后端改密成功即吊销全部会话：本地登录态必须同步清空，否则页面仍显示已登录。
    ui.auth?.resetAuthState?.()
    router.push('/login')
  } catch (reason) {
    logger.error('修改密码失败', reason)
    passwordError.value = '密码修改失败，请核对当前密码后重试'
  } finally {
    changing.value = false
  }
}

// —— M-06 扩展三面的数据加载与导航 ——
function clampPct(value: unknown): number {
  const n = Number(value)
  if (!Number.isFinite(n)) return 0
  return Math.max(0, Math.min(100, Math.round(n)))
}

function progressColor(pct: number): string {
  if (pct >= 80) return 'var(--ok)'
  if (pct >= 40) return 'var(--warn)'
  return 'var(--danger)'
}

function feedColor(color: string): string {
  if (color === 'ok') return 'var(--ok)'
  if (color === 'warning') return 'var(--warn)'
  if (color === 'danger') return 'var(--danger)'
  if (color === 'info') return 'var(--info)'
  return 'var(--accent)'
}

function notifyColor(kind: string): ActivityColor {
  const k = String(kind || '').toLowerCase()
  if (k.includes('exam') || k.includes('考试')) return 'danger'
  if (k.includes('assignment') || k.includes('作业') || k.includes('任务')) return 'warning'
  if (k.includes('grade') || k.includes('成绩')) return 'ok'
  return 'accent'
}

function normalizeTask(raw: unknown): Task {
  const r = (raw ?? {}) as Record<string, unknown>
  return {
    id: (r.id as number) ?? undefined,
    title: String(r.title ?? r.name ?? '未命名'),
    taskType: String(r.taskType ?? r.type ?? ''),
    courseId: (r.courseId ?? r.course_id ?? null) as number | string | null,
    dueDate: String(r.dueDate ?? r.due_date ?? r.deadline ?? ''),
    status: String(r.status ?? ''),
  }
}

type ActivityColor = 'ok' | 'warning' | 'danger' | 'info' | 'accent'

interface ActivityItem {
  courseId: number | null
  color: ActivityColor
  type: string
  title: string
  due: string
}

const unreadCount = computed(() => notifications.value.filter((n) => n.unread).length)

const activityFeed = computed<ActivityItem[]>(() => {
  const items: ActivityItem[] = []
  for (const a of pendingAssignments.value.slice(0, 6)) {
    items.push({
      courseId: a.courseId != null ? Number(a.courseId) : null,
      color: 'warning',
      type: '待办作业',
      title: a.title || '未命名作业',
      due: fmtTime(a.dueDate),
    })
  }
  for (const n of notifications.value.slice(0, 6)) {
    items.push({
      courseId: null,
      color: notifyColor(n.kind),
      type: n.kind || '通知',
      title: n.title,
      due: fmtTime(n.time),
    })
  }
  return items.slice(0, 8)
})

function openCourses() {
  router.push('/student/courses')
}
function openCourse(c: CourseCard) {
  router.push(`/student/courses/${c.id}`)
}
function openCourseById(id: number) {
  router.push(`/student/courses/${id}`)
}
function openAssignments() {
  router.push('/student/tasks')
}
function openNotifications() {
  router.push('/student/messages')
}

async function loadExtra() {
  const roleForCourses: Role = isStudent.value
    ? 'student'
    : roleText.value === 'TEACHER'
      ? 'teacher'
      : 'admin'
  coursesLoading.value = true
  feedLoading.value = true
  try {
    const rows = await loadCourses(roleForCourses)
    courseList.value = rows.slice(0, 6)
  } catch {
    courseList.value = []
  } finally {
    coursesLoading.value = false
  }
  const [pending, completed, notes] = await Promise.allSettled([
    studentApi.getPendingAssignments(),
    studentApi.getCompletedAssignments(),
    loadNotifications(),
  ])
  // 分路归因：单路失败不应把其它路也清成空（与 loadAdminOverview 同判据）
  if (pending.status === 'fulfilled') {
    pendingAssignments.value = pickList<Record<string, unknown>>(unwrap(pending.value)).map(
      normalizeTask,
    )
  }
  if (completed.status === 'fulfilled') {
    completedAssignments.value = pickList<Record<string, unknown>>(unwrap(completed.value)).map(
      normalizeTask,
    )
  }
  if (notes.status === 'fulfilled') {
    notifications.value = notes.value as Notification[]
  }
  feedLoading.value = false
}

onMounted(() => {
  loadProfile()
  loadExtra()
})
</script>

<style scoped>
.profile-body {
  display: grid;
  gap: 14px;
}
.callout.danger {
  border-left-color: var(--danger);
}
.callout.danger > span {
  color: var(--danger);
}
.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}
.profile-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}
.profile-identity {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.profile-avatar {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: var(--r-circle);
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-family: 'Noto Serif SC', Georgia, serif;
  font-size: 18px;
  font-weight: 600;
}
.profile-identity-main b {
  display: block;
  font-size: 14px;
  font-weight: 650;
}
.profile-identity-main p {
  margin: 3px 0 0;
  font-family: var(--font-mono);
  font-size: 9.5px;
  color: var(--ink-3);
}
.profile-static {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
  margin: 0 0 10px;
}
.profile-static dt {
  font-size: 8.5px;
  letter-spacing: 0.04em;
  color: var(--ink-3);
}
.profile-static dd {
  margin: 2px 0 0;
  font-size: 11.5px;
  color: var(--ink);
  word-break: break-all;
}
.profile-hint {
  margin: 0 0 4px;
  font-size: 10px;
  line-height: 1.7;
  color: var(--ink-3);
}
.profile-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.profile-meta {
  margin: 10px 0 0;
  font-size: 9.5px;
  color: var(--ink-3);
}
.theme-switch {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--line);
  border-radius: var(--r-ctl);
  background: var(--surface);
}
.theme-switch button {
  border: 0;
  border-radius: var(--r-xs);
  padding: 5px 12px;
  background: none;
  color: var(--ink-3);
  font-size: 10px;
}
.theme-switch button.is-active {
  background: var(--surface-2);
  color: var(--ink);
  font-weight: 600;
}
@media (max-width: 980px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}

/* —— M-06 扩展三面 —— */
.profile-extra {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr) minmax(0, 1.4fr);
  gap: 14px;
  align-items: start;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.profile-stat {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  background: var(--surface);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    transform 0.15s ease,
    background 0.15s ease;
}
.profile-stat:hover {
  border-color: var(--accent);
  background: var(--surface-2);
  transform: translateY(-1px);
}
.profile-stat-num {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1;
  color: var(--ink);
}
.profile-stat-label {
  font-size: 10px;
  color: var(--ink-3);
}
.profile-loading,
.profile-empty {
  margin: 0;
  padding: 18px 4px;
  font-size: 11px;
  color: var(--ink-3);
  text-align: center;
}
.profile-course-list,
.profile-feed-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.profile-course-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  border-top: 1px solid var(--line);
  border-radius: var(--r-xs);
  cursor: pointer;
  transition: background 0.15s ease;
}
.profile-course-item:first-child {
  border-top: 0;
}
.profile-course-item:hover {
  background: var(--surface-2);
}
.profile-course-info {
  flex: 1;
  min-width: 0;
}
.profile-course-info .course-name {
  margin-bottom: 6px;
}
.profile-feed-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 10px 4px;
  border-top: 1px solid var(--line);
  border-radius: var(--r-xs);
  transition: background 0.15s ease;
}
.profile-feed-item:first-child {
  border-top: 0;
}
.profile-feed-item.clickable {
  cursor: pointer;
}
.profile-feed-item.clickable:hover {
  background: var(--surface-2);
}
.profile-feed-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.profile-feed-title {
  display: flex;
  align-items: center;
  gap: 7px;
}
.profile-feed-name {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.profile-feed-due {
  font-size: 9.5px;
  color: var(--ink-3);
}
@media (max-width: 1180px) {
  .profile-extra {
    grid-template-columns: 1fr 1fr;
  }
  .stats-card {
    grid-column: 1 / -1;
  }
}
@media (max-width: 760px) {
  .profile-extra {
    grid-template-columns: 1fr;
  }
}
</style>
