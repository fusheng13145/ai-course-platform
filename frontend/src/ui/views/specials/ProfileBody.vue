<template>
  <section class="profile-body">
    <div v-if="loadError" class="callout danger profile-error" role="alert">
      <span>{{ loadError }}</span>
      <button class="btn btn-sm" type="button" @click="loadProfile">重新加载</button>
    </div>

    <section class="card profile-card" :aria-busy="profileLoading">
      <header class="profile-hero">
        <div class="profile-person">
          <span v-if="avatarPreview && !avatarBroken" class="profile-avatar">
            <img :src="avatarPreview" alt="" @error="avatarBroken = true" />
          </span>
          <span v-else class="profile-avatar" aria-hidden="true">{{ avatarText }}</span>

          <div class="profile-person-main">
            <div class="profile-name-row">
              <b class="profile-name">{{ displayName }}</b>
              <span class="chip chip-mute">{{ roleLabel }}</span>
            </div>

            <div class="profile-uid-row">
              <span class="profile-uid mono">UID {{ uidText || '—' }}</span>
              <button
                v-if="uidText"
                class="profile-copy"
                type="button"
                aria-label="复制 UID"
                @click="copyUid"
              >
                复制
              </button>
            </div>

            <p v-if="affiliationText" class="profile-affiliation">{{ affiliationText }}</p>
          </div>
        </div>

        <div class="profile-actions">
          <button class="btn btn-sm" type="button" @click="openPasswordModal">修改密码</button>
          <button class="btn btn-sm btn-primary" type="button" @click="openProfileModal">
            编辑资料
          </button>
        </div>
      </header>

      <div class="profile-detail">
        <section v-for="group in fieldGroups" :key="group.title" class="profile-group">
          <h2>{{ group.title }}</h2>
          <dl class="profile-fields">
            <div v-for="row in group.fields" :key="row.label" class="profile-field">
              <dt>{{ row.label }}</dt>
              <dd :class="{ 'is-empty': !row.value }" :title="row.value || undefined">
                {{ row.value || '—' }}
              </dd>
            </div>
          </dl>
        </section>
      </div>
    </section>

    <Modal v-model:open="profileOpen" title="编辑资料" width="480px" @close="closeProfileModal">
      <div class="profile-avatar-row">
        <span v-if="formAvatarPreview" class="profile-avatar is-sm">
          <img :src="formAvatarPreview" alt="" />
        </span>
        <span v-else class="profile-avatar is-sm" aria-hidden="true">{{ avatarText }}</span>
        <div class="profile-avatar-copy">
          <button
            class="btn btn-sm"
            type="button"
            :disabled="avatarUploading"
            @click="avatarInput?.click()"
          >
            {{ avatarUploading ? '上传中…' : '更换头像' }}
          </button>
          <p>JPG / PNG / WebP，2 MB 以内</p>
        </div>
        <input
          ref="avatarInput"
          class="profile-file"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          data-field="avatar"
          @change="onAvatarChange"
        />
      </div>

      <div class="form-grid">
        <label class="field span-2">
          <span>姓名</span>
          <input
            v-model="form.fullName"
            class="input"
            type="text"
            maxlength="50"
            data-field="fullName"
          />
        </label>

        <div class="field span-2" data-field="gender">
          <span>性别</span>
          <div class="profile-seg" role="group" aria-label="性别">
            <button
              v-for="item in GENDERS"
              :key="item.value"
              class="profile-seg-btn"
              :class="{ 'is-active': form.gender === item.value }"
              type="button"
              :data-gender="item.value"
              @click="form.gender = item.value"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <label class="field">
          <span>手机号</span>
          <input v-model="form.phone" class="input" type="tel" maxlength="11" data-field="phone" />
        </label>

        <label class="field">
          <span>邮箱</span>
          <input v-model="form.email" class="input" type="email" data-field="email" />
        </label>
      </div>

      <p v-if="saveError" class="form-error" role="alert">{{ saveError }}</p>
      <p class="profile-readonly">{{ readonlyHint }}</p>

      <template #footer>
        <button class="btn" type="button" :disabled="saving" @click="closeProfileModal">
          取消
        </button>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="saving || avatarUploading"
          @click="submitProfile"
        >
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <Modal v-model:open="passwordOpen" title="修改密码" width="480px" @close="closePasswordModal">
      <div class="form-grid">
        <label class="field span-2">
          <span>当前密码</span>
          <input
            v-model="password.oldPassword"
            class="input"
            type="password"
            autocomplete="current-password"
            data-field="oldPassword"
          />
        </label>
        <label class="field span-2">
          <span>新密码</span>
          <input
            v-model="password.newPassword"
            class="input"
            type="password"
            autocomplete="new-password"
            data-field="newPassword"
          />
        </label>
        <label class="field span-2">
          <span>确认新密码</span>
          <input
            v-model="password.confirmPassword"
            class="input"
            type="password"
            autocomplete="new-password"
            data-field="confirmPassword"
          />
        </label>
      </div>

      <p class="profile-readonly">8–32 位，需包含大小写字母、数字与特殊字符。</p>
      <p v-if="passwordError" class="form-error" role="alert">{{ passwordError }}</p>

      <template #footer>
        <button class="btn" type="button" :disabled="changing" @click="closePasswordModal">
          取消
        </button>
        <button class="btn btn-primary" type="button" :disabled="changing" @click="submitPassword">
          {{ changing ? '提交中…' : '确认修改' }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Modal from '../../components/Modal.vue'
import { useUi } from '../../composables/ui'
import authApi from '@/api/auth'
import commonApi from '@/api/common'
import userApi from '@/api/user'
import { unwrap } from '@/utils/response'
import safeUrl from '@/utils/safeUrl'

interface ProfileUser {
  id?: number | string | null
  username?: string
  fullName?: string
  email?: string
  phone?: string
  gender?: string
  avatarUrl?: string
  studentId?: string
  teacherId?: string
  major?: string
  majorName?: string
  grade?: string
  department?: string
  collegeName?: string
  schoolName?: string
  adminClassName?: string
  adminScope?: string
  title?: string
  role?: string
}

interface ProfileField {
  label: string
  value: string
}

interface ProfileGroup {
  title: string
  fields: ProfileField[]
}

const GENDERS = [
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' },
  { value: 'OTHER', label: '保密' },
] as const

const ui = useUi()
const router = useRouter()
const { toast } = ui

const cached = ((ui.auth?.user as ProfileUser | null) ?? {}) as ProfileUser
const profile = ref<ProfileUser>({ ...cached })
const form = reactive({
  fullName: '',
  email: '',
  phone: '',
  gender: '',
  avatarUrl: '',
})
const password = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const profileLoading = ref(false)
const profileOpen = ref(false)
const passwordOpen = ref(false)
const saving = ref(false)
const changing = ref(false)
const avatarUploading = ref(false)
const avatarBroken = ref(false)
const avatarPreview = ref(resolveAvatar(cached.avatarUrl))
const formAvatarPreview = ref('')
const avatarInput = ref<HTMLInputElement | null>(null)
const saveError = ref('')
const passwordError = ref('')
const loadError = ref('')

const uiRole = computed(() => {
  // useUi 暴露的 role 可能是 ref，也可能已解包成字符串：两种形态都要取到值。
  const raw = ui.role as unknown
  const val =
    raw !== null && typeof raw === 'object' && 'value' in (raw as Record<string, unknown>)
      ? (raw as { value: unknown }).value
      : raw
  return String(val ?? '').toLowerCase()
})
const profileRole = computed(() => String(profile.value.role ?? '').toUpperCase())
const roleKey = computed<'student' | 'teacher' | 'admin'>(() => {
  const candidate = profileRole.value
  if (candidate === 'TEACHER' || candidate === 'ADMIN') {
    return candidate === 'TEACHER' ? 'teacher' : 'admin'
  }
  if (candidate === 'STUDENT') return 'student'
  if (uiRole.value === 'teacher' || uiRole.value === 'admin') {
    return uiRole.value
  }
  return 'student'
})
const isStudent = computed(() => roleKey.value === 'student')
const isTeacher = computed(() => roleKey.value === 'teacher')

const currentUserId = computed<number | string | null>(
  () => profile.value.id ?? ui.auth?.userId ?? cached.id ?? null,
)
const uidText = computed(() => {
  const id = currentUserId.value
  return id == null || id === '' ? '' : String(id)
})

const displayName = computed(
  () => profile.value.fullName || profile.value.username || cached.fullName || '未登录',
)
const avatarText = computed(() => displayName.value.slice(0, 1))

const roleLabel = computed(() => {
  if (roleKey.value === 'teacher') return '教师'
  if (roleKey.value === 'admin') {
    const scope = String(profile.value.adminScope ?? '').toUpperCase()
    if (scope === 'COLLEGE') return '院系管理员'
    if (scope === 'SCHOOL') return '校级管理员'
    return '管理员'
  }
  return '学生'
})

const collegeText = computed(() => profile.value.collegeName || profile.value.department || '')
const majorText = computed(() => profile.value.majorName || profile.value.major || '')
const adminScopeText = computed(() => {
  const scope = String(profile.value.adminScope ?? '').toUpperCase()
  if (scope === 'SCHOOL') return '校级'
  if (scope === 'COLLEGE') return '院系'
  return ''
})

const affiliationText = computed(() => {
  const parts = isStudent.value
    ? [collegeText.value, majorText.value, profile.value.grade]
    : isTeacher.value
      ? [collegeText.value, profile.value.title]
      : [profile.value.schoolName]
  return parts
    .map((item) => String(item ?? '').trim())
    .filter(Boolean)
    .join(' · ')
})

const fieldGroups = computed<ProfileGroup[]>(() => {
  const groups: ProfileGroup[] = [
    {
      title: '基本资料',
      fields: [
        { label: '性别', value: genderLabel(profile.value.gender) },
        { label: '手机号', value: profile.value.phone ?? '' },
        { label: '邮箱', value: profile.value.email ?? '' },
        { label: '用户名', value: profile.value.username ?? '' },
      ],
    },
    {
      title: '组织信息',
      fields: [
        { label: '所属学校', value: profile.value.schoolName ?? '' },
        { label: '学院', value: collegeText.value },
      ],
    },
  ]

  if (isStudent.value) {
    groups.push({
      title: '学籍信息',
      fields: [
        { label: '学号', value: profile.value.studentId ?? '' },
        { label: '专业', value: majorText.value },
        { label: '年级', value: profile.value.grade ?? '' },
        { label: '行政班', value: profile.value.adminClassName ?? '' },
      ],
    })
  } else if (isTeacher.value) {
    groups.push({
      title: '任职信息',
      fields: [
        { label: '工号', value: profile.value.teacherId ?? '' },
        { label: '职称', value: profile.value.title ?? '' },
      ],
    })
  } else {
    groups.push({
      title: '管理信息',
      fields: [
        { label: '工号', value: profile.value.teacherId ?? '' },
        { label: '管理范围', value: adminScopeText.value },
      ],
    })
  }

  return groups
})

const readonlyHint = computed(() =>
  isStudent.value ? '身份、学校、学院与学号由管理员维护。' : '身份、学校、学院与工号由管理员维护。',
)

watch(
  () => profile.value.avatarUrl,
  (value) => {
    avatarPreview.value = resolveAvatar(value)
    avatarBroken.value = false
  },
)

onMounted(loadProfile)

async function loadProfile() {
  const id = currentUserId.value
  loadError.value = ''
  if (!id) return
  profileLoading.value = true
  try {
    const payload = unwrap<ProfileUser>(await userApi.getProfile(id))
    if (payload) applyProfile(payload)
  } catch {
    loadError.value = '详细资料未能加载，请重试'
  } finally {
    profileLoading.value = false
  }
}

function applyProfile(next: ProfileUser) {
  profile.value = { ...next }
  syncAuthUser(next)
}

function syncAuthUser(next: ProfileUser) {
  if (ui.auth && 'user' in ui.auth) {
    ;(ui.auth as { user?: unknown }).user = next
  }
}

function genderLabel(value?: string) {
  return GENDERS.find((item) => item.value === value)?.label ?? ''
}

function resolveAvatar(raw?: string | null): string {
  if (!raw) return ''
  // 仅接受本页 FileReader 生成的位图预览，不接受任意 data: 内容。
  if (/^data:image\/(png|jpeg|webp);base64,/.test(raw)) return raw
  const safe = safeUrl(raw)
  if (!safe) return ''
  const url = new URL(safe)
  if (url.origin !== window.location.origin) return url.href
  // 开发服务器只代理 /api；后端上传返回 /uploads/**，渲染时补上 /api 前缀。
  if (url.pathname.startsWith('/uploads/')) return `/api${url.pathname}${url.search}`
  return url.href
}

async function copyUid() {
  const text = uidText.value
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const node = document.createElement('textarea')
      node.value = text
      node.style.position = 'fixed'
      node.style.opacity = '0'
      document.body.appendChild(node)
      node.select()
      document.execCommand('copy')
      document.body.removeChild(node)
    }
    toast('已复制 UID', 'ok')
  } catch {
    toast('复制失败，请手动选择 UID', 'warn')
  }
}

function openProfileModal() {
  form.fullName = profile.value.fullName ?? ''
  form.email = profile.value.email ?? ''
  form.phone = profile.value.phone ?? ''
  form.gender = profile.value.gender ?? ''
  form.avatarUrl = profile.value.avatarUrl ?? ''
  formAvatarPreview.value = resolveAvatar(form.avatarUrl)
  saveError.value = ''
  profileOpen.value = true
}

function closeProfileModal() {
  if (saving.value || avatarUploading.value) return
  profileOpen.value = false
  saveError.value = ''
}

async function onAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
    saveError.value = '头像仅支持 JPG / PNG / WebP'
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    saveError.value = '头像不能超过 2 MB'
    return
  }

  saveError.value = ''
  const reader = new FileReader()
  reader.onload = () => {
    formAvatarPreview.value = String(reader.result ?? '')
  }
  reader.readAsDataURL(file)

  avatarUploading.value = true
  try {
    const uploaded = unwrap<{ url?: string }>(await commonApi.uploadFile(file))
    if (!uploaded?.url) throw new Error('上传结果缺少地址')
    form.avatarUrl = uploaded.url
    formAvatarPreview.value = resolveAvatar(uploaded.url) || formAvatarPreview.value
  } catch (error) {
    saveError.value = errorMessage(error, '头像上传失败，请重试')
    formAvatarPreview.value = resolveAvatar(form.avatarUrl)
  } finally {
    avatarUploading.value = false
  }
}

async function submitProfile() {
  if (avatarUploading.value) {
    saveError.value = '头像还在上传，请稍候'
    return
  }

  const fullName = form.fullName.trim()
  if (!fullName) {
    saveError.value = '姓名不能为空'
    return
  }
  const phone = form.phone.trim()
  if (phone && !/^1[3-9]\d{9}$/.test(phone)) {
    saveError.value = '手机号格式不正确'
    return
  }
  const email = form.email.trim()
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    saveError.value = '邮箱格式不正确'
    return
  }

  const payload: Record<string, string> = { fullName }
  if (phone) payload.phone = phone
  if (email) payload.email = email
  if (form.gender && form.gender !== (profile.value.gender ?? '')) payload.gender = form.gender
  if (form.avatarUrl && form.avatarUrl !== (profile.value.avatarUrl ?? '')) {
    payload.avatar = form.avatarUrl
  }

  saving.value = true
  saveError.value = ''
  try {
    const updated = unwrap<ProfileUser>(await authApi.updateCurrentUser(payload))
    if (!updated) throw new Error('更新结果为空')
    applyProfile({ ...profile.value, ...updated })
    profileOpen.value = false
    toast('资料已更新', 'ok')
  } catch (error) {
    saveError.value = errorMessage(error, '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function openPasswordModal() {
  password.oldPassword = ''
  password.newPassword = ''
  password.confirmPassword = ''
  passwordError.value = ''
  passwordOpen.value = true
}

function closePasswordModal() {
  if (changing.value) return
  passwordOpen.value = false
  passwordError.value = ''
}

async function submitPassword() {
  if (!password.oldPassword || !password.newPassword || !password.confirmPassword) {
    passwordError.value = '请填写完整的密码信息'
    return
  }
  if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,32}$/.test(password.newPassword)) {
    passwordError.value = '新密码需 8–32 位，且包含大小写字母、数字与特殊字符'
    return
  }
  if (password.newPassword === password.oldPassword) {
    passwordError.value = '新密码不能与当前密码相同'
    return
  }
  if (password.newPassword !== password.confirmPassword) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }

  changing.value = true
  passwordError.value = ''
  try {
    await authApi.changePassword({
      oldPassword: password.oldPassword,
      newPassword: password.newPassword,
      confirmPassword: password.confirmPassword,
    })
    passwordOpen.value = false
    toast('密码已修改，请重新登录', 'ok')
    // 后端改密即吊销会话：本地登录态必须清空并回登录页。
    ui.auth?.resetAuthState?.()
    router.push('/login')
  } catch (error) {
    passwordError.value = errorMessage(error, '修改失败，请稍后重试')
  } finally {
    changing.value = false
  }
}

function errorMessage(error: unknown, fallback: string) {
  const message = (error as { response?: { data?: { message?: unknown } } })?.response?.data
    ?.message
  return typeof message === 'string' && message.trim() ? message : fallback
}
</script>

<style scoped>
.profile-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.profile-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin: 0;
  border-left-color: var(--danger);
}

.profile-error span {
  color: var(--danger);
}

.profile-error .btn {
  flex: none;
}

.profile-card {
  padding: 0;
  overflow: hidden;
}

.profile-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-width: 0;
  padding: 24px 26px;
  background: var(--surface-2);
  border-bottom: 1px solid var(--line);
}

.profile-person {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}

.profile-person-main {
  min-width: 0;
}

.profile-avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 96px;
  height: 96px;
  overflow: hidden;
  border-radius: var(--r-circle);
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-family: var(--font);
  font-size: 32px;
  font-weight: 600;
  line-height: 1;
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-avatar.is-sm {
  width: 56px;
  height: 56px;
  font-size: 20px;
}

.profile-name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.profile-name {
  overflow: hidden;
  max-width: 100%;
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: -0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-uid-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.profile-uid {
  font-size: 12px;
  color: var(--ink-3);
}

.profile-copy {
  padding: 0;
  border: 0;
  background: none;
  color: var(--ink-3);
  font: inherit;
  font-size: 12px;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
}

.profile-copy:hover {
  color: var(--accent-strong);
}

.profile-affiliation {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.65;
  color: var(--ink-3);
}

.profile-actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.profile-detail {
  min-width: 0;
  padding: 24px 26px 26px;
}

.profile-group + .profile-group {
  margin-top: 20px;
}

.profile-group h2 {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--ink-2);
}

.profile-group h2::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--line);
}

.profile-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 40px;
  margin: 0;
  padding: 0;
  border: 0;
}

.profile-field {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
  min-height: 40px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}

.profile-field dt {
  flex: none;
  font-size: 12px;
  color: var(--ink-3);
}

.profile-field dd {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  color: var(--ink);
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-field dd.is-empty {
  color: var(--ink-3);
}

.profile-avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.profile-avatar-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
}

.profile-avatar-copy p {
  margin: 0;
  font-size: 12px;
  color: var(--ink-3);
}

.profile-file {
  display: none;
}

.profile-seg {
  display: flex;
  height: 34px;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-ctl);
  background: var(--surface-2);
}

.profile-seg-btn {
  flex: 1;
  border: 0;
  border-left: 1px solid var(--line);
  background: transparent;
  color: var(--ink-3);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.profile-seg-btn:first-child {
  border-left: 0;
}

.profile-seg-btn.is-active {
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-weight: 500;
}

.profile-readonly {
  margin: 16px 0 0;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
}

@media (max-width: 980px) {
  .profile-hero {
    gap: 18px;
    padding: 22px;
  }

  .profile-avatar {
    width: 88px;
    height: 88px;
    font-size: 29px;
  }

  .profile-name {
    font-size: 26px;
  }

  .profile-detail {
    padding: 22px 22px 24px;
  }
}

@media (max-width: 820px) {
  .profile-hero {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }

  .profile-actions {
    width: 100%;
  }

  .profile-actions .btn {
    flex: 1;
  }

  .profile-fields {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }
}

@media (max-width: 620px) {
  .profile-hero {
    padding: 20px 18px;
  }

  .profile-detail {
    padding: 18px 16px 20px;
  }

  .profile-avatar {
    width: 76px;
    height: 76px;
    font-size: 26px;
  }

  .profile-name {
    font-size: 23px;
  }
}
</style>
