<template>
  <AuthShell>
    <template #form>
      <header class="av-form-head">
        <p class="page-kicker">NEW ACCOUNT / 2026 秋</p>
        <h2>创建账号</h2>
        <p>学生账号可自助注册；教师与管理员账号由学校统一分配。</p>
      </header>

      <form class="av-form" @submit.prevent="submit">
        <div class="av-grid2">
          <label class="av-field">
            <span>用户名</span>
            <input
              v-model="form.username"
              class="av-input"
              name="username"
              autocomplete="username"
              placeholder="3–20 位字母/数字"
              :disabled="submitting"
            />
          </label>
          <label class="av-field">
            <span>姓名</span>
            <input
              v-model="form.fullName"
              class="av-input"
              name="fullName"
              autocomplete="name"
              placeholder="真实姓名"
              :disabled="submitting"
            />
          </label>
        </div>

        <label class="av-field">
          <span>邮箱</span>
          <div class="av-code-row">
            <input
              v-model="form.email"
              class="av-input"
              type="email"
              name="email"
              autocomplete="email"
              placeholder="用于接收注册验证码"
              :disabled="submitting"
            />
            <button
              class="btn btn-ghost av-code-btn"
              type="button"
              :disabled="sending || cooldown > 0 || submitting"
              @click="sendCode"
            >
              {{ sending ? '发送中…' : cooldown > 0 ? `${cooldown}s` : '发送验证码' }}
            </button>
          </div>
        </label>

        <div class="av-grid2">
          <label class="av-field">
            <span>验证码</span>
            <input
              v-model="form.code"
              class="av-input"
              name="code"
              inputmode="numeric"
              autocomplete="one-time-code"
              placeholder="6 位数字"
              :disabled="submitting"
            />
          </label>
          <label class="av-field">
            <span>手机号（可选）</span>
            <input
              v-model="form.phone"
              class="av-input"
              name="phone"
              autocomplete="tel"
              placeholder="11 位手机号"
              :disabled="submitting"
            />
          </label>
        </div>

        <div class="av-grid2">
          <label class="av-field">
            <span>密码</span>
            <input
              v-model="form.password"
              class="av-input"
              type="password"
              name="password"
              autocomplete="new-password"
              placeholder="6–30 位，含字母和数字"
              :disabled="submitting"
            />
          </label>
          <label class="av-field">
            <span>确认密码</span>
            <input
              v-model="form.confirm"
              class="av-input"
              type="password"
              name="confirm-password"
              autocomplete="new-password"
              placeholder="再输入一次"
              :disabled="submitting"
            />
          </label>
        </div>

        <label class="av-check av-agree">
          <input v-model="form.agree" type="checkbox" />
          <span>
            我已阅读并同意
            <router-link class="av-link" to="/agreement" target="_blank">用户服务协议</router-link>
            与
            <router-link class="av-link" to="/privacy" target="_blank">隐私政策</router-link>
          </span>
        </label>

        <p v-if="error" class="av-error" role="alert">{{ error }}</p>
        <button class="btn btn-primary btn-block av-submit" type="submit" :disabled="submitting">
          {{ submitting ? '注册中…' : '注 册' }}
        </button>
      </form>

      <p class="av-switch">已有账号？<router-link to="/login">返回登录</router-link></p>
      <p class="av-note">注册需邮箱验证码，5 分钟内有效；注册成功即建立学生登录态。</p>
    </template>
  </AuthShell>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import authApi from '@/api/auth'
import { isSafeInternalRedirect } from '@/utils/redirect'
import AuthShell from './AuthShell.vue'
import { clearCourses } from '../../data/api'
import { useUi } from '../../composables/ui'
import { ROLE_HOME, type Role } from '../../types'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const { toast } = useUi()

const form = reactive({
  username: '',
  fullName: '',
  email: '',
  code: '',
  phone: '',
  password: '',
  confirm: '',
  agree: false,
})
const submitting = ref(false)
const sending = ref(false)
const cooldown = ref(0)
const error = ref('')
let timer: number | undefined

function home(): string {
  const role = String(auth.effectiveRole || auth.userRole || 'STUDENT').toLowerCase()
  return ROLE_HOME[role as Role] || ROLE_HOME.student
}

function targetAfterAuth(): string {
  const raw = route.query.redirect
  const value = String(raw || '')
  if (
    isSafeInternalRedirect(raw) &&
    value.startsWith('/') &&
    !value.startsWith('/login') &&
    !value.startsWith('/register')
  ) {
    return value
  }
  return home()
}

function emailValid(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
}

async function sendCode() {
  if (sending.value || cooldown.value > 0) return
  if (!emailValid(form.email)) {
    error.value = '请先填写有效邮箱'
    return
  }
  sending.value = true
  error.value = ''
  try {
    await authApi.sendVerificationCode({ email: form.email.trim(), type: 'REGISTER' })
    toast('验证码已发送，请查收邮箱', 'ok')
    cooldown.value = 60
    timer = window.setInterval(() => {
      cooldown.value -= 1
      if (cooldown.value <= 0 && timer) {
        window.clearInterval(timer)
        timer = undefined
      }
    }, 1000)
  } catch (err) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      '验证码发送失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

function validate(): string {
  if (!/^[a-zA-Z0-9_]{3,20}$/.test(form.username.trim()))
    return '用户名需 3–20 位字母、数字或下划线'
  if (!form.fullName.trim()) return '请输入姓名'
  if (!emailValid(form.email)) return '请输入有效邮箱'
  if (!/^\d{6}$/.test(form.code.trim())) return '请输入 6 位验证码'
  if (form.phone.trim() && !/^1[3-9]\d{9}$/.test(form.phone.trim())) return '手机号格式不正确'
  if (form.password.length < 6 || form.password.length > 30) return '密码长度需在 6–30 位'
  if (!/^(?=.*[a-zA-Z])(?=.*\d).+$/.test(form.password)) return '密码必须同时包含字母和数字'
  if (form.password !== form.confirm) return '两次密码不一致'
  if (!form.agree) return '请阅读并同意用户协议和隐私政策'
  return ''
}

async function submit() {
  if (submitting.value) return
  const message = validate()
  if (message) {
    error.value = message
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const result = await auth.register({
      username: form.username.trim(),
      fullName: form.fullName.trim(),
      email: form.email.trim(),
      code: form.code.trim(),
      phone: form.phone.trim() || undefined,
      password: form.password,
      confirmPassword: form.confirm,
      role: 'STUDENT',
      agreement: true,
    })
    if (!result?.success) {
      error.value = '注册响应未建立登录态，请稍后重试或联系管理员'
      return
    }
    clearCourses()
    toast('注册成功', 'ok')
    await router.replace(targetAfterAuth())
  } catch (err) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      '注册失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (auth.isLoggedIn) router.replace(targetAfterAuth())
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.av-agree {
  align-items: flex-start;
  line-height: 1.7;
}
.av-agree input {
  margin-top: 2px;
  flex: none;
}
.av-grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.av-code-row {
  display: flex;
  gap: 8px;
}
.av-code-row .av-input {
  flex: 1;
  min-width: 0;
}
.av-code-btn {
  flex: none;
  height: 44px;
  padding: 0 12px;
  font-size: 11px;
}
@media (max-width: 520px) {
  .av-grid2 {
    grid-template-columns: 1fr;
  }
}
</style>
