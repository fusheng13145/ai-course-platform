<template>
  <AuthShell>
    <template #form>
      <header class="av-form-head">
        <h2>找回密码</h2>
        <router-link class="av-head-link" to="/login">返回登录</router-link>
      </header>

      <ol class="av-steps" aria-label="找回密码步骤">
        <li class="av-step" :class="{ 'is-active': step === 1, 'is-done': step > 1 }">
          <NumberBadge :value="1" /><span>邮箱</span>
        </li>
        <li class="av-step" :class="{ 'is-active': step === 2, 'is-done': step > 2 }">
          <NumberBadge :value="2" /><span>验证</span>
        </li>
        <li class="av-step" :class="{ 'is-active': step === 3 }">
          <NumberBadge :value="3" /><span>新密码</span>
        </li>
      </ol>

      <form class="av-form" @submit.prevent="submit">
        <template v-if="step === 1">
          <label class="av-field">
            <span>注册邮箱</span>
            <div class="av-control">
              <Icon name="message" cls="av-control-icon" />
              <input
                v-model="form.email"
                class="av-input"
                type="email"
                name="email"
                autocomplete="email"
                placeholder="用于接收验证码"
                :disabled="sending"
              />
            </div>
          </label>
        </template>

        <template v-else-if="step === 2">
          <label class="av-field">
            <span>验证码</span>
            <div class="av-code-row">
              <div class="av-control">
                <Icon name="shield" cls="av-control-icon" />
                <input
                  v-model="form.code"
                  class="av-input"
                  name="code"
                  inputmode="numeric"
                  autocomplete="one-time-code"
                  placeholder="6 位数字"
                  :disabled="sending"
                />
              </div>
              <button
                class="av-code-btn"
                type="button"
                :disabled="sending || cooldown > 0"
                @click="sendCode"
              >
                {{ cooldown > 0 ? `${cooldown}s` : '重新获取' }}
              </button>
            </div>
          </label>
        </template>

        <template v-else>
          <label class="av-field">
            <span>新密码</span>
            <div class="av-control has-action">
              <Icon name="lock" cls="av-control-icon" />
              <input
                v-model="form.password"
                class="av-input"
                :type="showPassword ? 'text' : 'password'"
                name="new-password"
                autocomplete="new-password"
                placeholder="8–32 位，含大小写、数字、特殊字符"
                :disabled="sending"
              />
              <button
                class="av-reveal"
                type="button"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                {{ showPassword ? '隐藏' : '显示' }}
              </button>
            </div>
          </label>
          <label class="av-field">
            <span>确认新密码</span>
            <div class="av-control">
              <Icon name="lock" cls="av-control-icon" />
              <input
                v-model="form.confirm"
                class="av-input"
                type="password"
                name="confirm-password"
                autocomplete="new-password"
                placeholder="再输入一次"
                :disabled="sending"
              />
            </div>
          </label>
        </template>

        <p v-if="error" class="av-error" role="alert">{{ error }}</p>
        <button class="btn btn-primary btn-block av-submit" type="submit" :disabled="sending">
          {{ sending ? '处理中…' : buttonLabel }}
        </button>
      </form>
    </template>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AuthShell from './AuthShell.vue'
import NumberBadge from '../../components/NumberBadge.vue'
import Icon from '../../components/Icon.vue'
import { useUi } from '../../composables/ui'

const router = useRouter()
const auth = useAuthStore()
const { toast } = useUi()

const step = ref<1 | 2 | 3>(1)
const sending = ref(false)
const cooldown = ref(0)
const showPassword = ref(false)
const error = ref('')
const form = reactive({ email: '', code: '', password: '', confirm: '' })
let timer: number | undefined

const PASSWORD_RE =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[~!@#$%^&*()_+\-={}[\]:;"'<>,.?/\\|`]).{8,32}$/

const buttonLabel = computed(() => {
  if (step.value === 1) return '发送验证码'
  if (step.value === 2) return '验证验证码'
  return '重置密码'
})

function emailValid(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
}

function startCooldown() {
  cooldown.value = 60
  timer = window.setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && timer) {
      window.clearInterval(timer)
      timer = undefined
    }
  }, 1000)
}

async function sendCode() {
  if (sending.value || cooldown.value > 0) return
  if (!emailValid(form.email)) {
    error.value = '请填写有效邮箱'
    return
  }
  sending.value = true
  error.value = ''
  try {
    await auth.sendResetCode(form.email.trim())
    if (step.value === 1) step.value = 2
    startCooldown()
    toast('验证码已发送', 'ok')
  } catch (err) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      '验证码发送失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

async function verifyCode() {
  if (!/^\d{6}$/.test(form.code.trim())) {
    error.value = '请输入 6 位验证码'
    return
  }
  sending.value = true
  error.value = ''
  try {
    await auth.verifyResetCode(form.email.trim(), form.code.trim())
    step.value = 3
    toast('验证通过，请设置新密码', 'ok')
  } catch (err) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      '验证失败，请检查验证码'
  } finally {
    sending.value = false
  }
}

async function resetPassword() {
  if (!PASSWORD_RE.test(form.password)) {
    error.value = '密码需 8–32 位，且同时包含大小写字母、数字和特殊字符'
    return
  }
  if (form.password !== form.confirm) {
    error.value = '两次密码不一致'
    return
  }
  sending.value = true
  error.value = ''
  try {
    await auth.resetPassword(form.email.trim(), form.code.trim(), form.password)
    toast('密码已重置，请用新密码登录', 'ok')
    await router.replace('/login')
  } catch (err) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      '重置失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (sending.value) return
  if (step.value === 1) return sendCode()
  if (step.value === 2) return verifyCode()
  return resetPassword()
}

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>
