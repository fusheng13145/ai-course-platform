import { computed, reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { ROLE_LABEL, type V2Role } from '../types'

export interface V2Toast {
  id: number
  message: string
  tone: 'info' | 'ok' | 'warn' | 'danger'
}

const theme = ref<'light' | 'dark'>('light')
const sidebarCollapsed = ref(false)
const mobileNavOpen = ref(false)
const paletteOpen = ref(false)
const paletteQuery = ref('')
const notifOpen = ref(false)
const assistantOpen = ref(false)
const unread = ref(0)
const toasts = ref<V2Toast[]>([])
const courseContext = reactive<{
  id: number | null
  name: string
  code: string
  semester: string
  credits: number | null
  teacher: string
  status: string
}>({
  id: null,
  name: '',
  code: '',
  semester: '',
  credits: null,
  teacher: '',
  status: '',
})

let toastSeq = 0

function readStoredTheme(): 'light' | 'dark' {
  const stored = localStorage.getItem('v2-theme')
  return stored === 'dark' ? 'dark' : 'light'
}

theme.value = readStoredTheme()

function setTheme(next: 'light' | 'dark') {
  theme.value = next
  localStorage.setItem('v2-theme', next)
}

function toggleTheme() {
  setTheme(theme.value === 'dark' ? 'light' : 'dark')
}

export function v2Toast(message: string, tone: V2Toast['tone'] = 'info') {
  toast(message, tone)
}

function toast(message: string, tone: V2Toast['tone'] = 'info') {
  const id = ++toastSeq
  toasts.value.push({ id, message, tone })
  window.setTimeout(() => {
    toasts.value = toasts.value.filter((item) => item.id !== id)
  }, 3200)
}

function dismissToast(id: number) {
  toasts.value = toasts.value.filter((item) => item.id !== id)
}

function setCourseContext(patch: Partial<typeof courseContext> | null) {
  if (!patch) {
    Object.assign(courseContext, {
      id: null,
      name: '',
      code: '',
      semester: '',
      credits: null,
      teacher: '',
      status: '',
    })
    return
  }
  Object.assign(courseContext, patch)
}

export function useV2Ui() {
  const auth = useAuthStore()

  const role = computed<V2Role>(() => {
    const raw = String(auth.effectiveRole || auth.userRole || 'STUDENT').toUpperCase()
    if (raw === 'TEACHER') return 'teacher'
    if (raw === 'ADMIN') return 'admin'
    return 'student'
  })

  const displayName = computed(() => auth.userName || ROLE_LABEL[role.value])
  const avatar = computed(() => displayName.value.slice(0, 1) || '云')
  const roleLabel = computed(() => ROLE_LABEL[role.value])
  const userMeta = computed(() => {
    const username = auth.user?.username
    return username ? `${roleLabel.value} · ${username}` : roleLabel.value
  })

  return {
    auth,
    role,
    displayName,
    avatar,
    roleLabel,
    userMeta,
    theme,
    setTheme,
    toggleTheme,
    sidebarCollapsed,
    mobileNavOpen,
    paletteOpen,
    paletteQuery,
    notifOpen,
    assistantOpen,
    unread,
    toasts,
    toast,
    dismissToast,
    courseContext,
    setCourseContext,
  }
}
