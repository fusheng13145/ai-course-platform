<template>
  <V2AuthShell>
    <template #form>
      <header class="av-form-head">
        <p class="page-kicker">TEACHER / STUDENT / ADMIN · 2026 秋</p>
        <h2>欢迎回来</h2>
        <p>用你的平台账号进入记录工作台。登录后按角色进入对应端。</p>
      </header>

      <form class="av-form" @submit.prevent="submit">
        <label class="av-field">
          <span>用户名</span>
          <input
            v-model="form.username"
            class="av-input"
            name="username"
            autocomplete="username"
            placeholder="用户名 / 邮箱"
            :disabled="submitting"
          />
        </label>
        <label class="av-field">
          <span>密码</span>
          <input
            v-model="form.password"
            class="av-input"
            type="password"
            name="password"
            autocomplete="current-password"
            placeholder="密码"
            :disabled="submitting"
          />
        </label>
        <div class="av-row">
          <label class="av-check">
            <input v-model="form.remember" type="checkbox" />
            <span>记住我</span>
          </label>
          <router-link class="av-link" to="/forgot-password">忘记密码？</router-link>
        </div>
        <p v-if="error" class="av-error" role="alert">{{ error }}</p>
        <button class="btn btn-primary btn-block av-submit" type="submit" :disabled="submitting">
          {{ submitting ? '登录中…' : '登 录' }}
        </button>
      </form>

      <p class="av-switch">还没有账号？<router-link to="/register">立即注册</router-link></p>
      <p class="av-note">登录凭据由后端校验；会话令牌保存在 HttpOnly Cookie 中，前端不落盘。</p>
    </template>
  </V2AuthShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { isSafeInternalRedirect } from '@/utils/redirect'
import V2AuthShell from './V2AuthShell.vue'
import { clearCourses } from '../../data/api'
import { useV2Ui } from '../../composables/ui'
import { ROLE_HOME, type V2Role } from '../../types'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const { toast } = useV2Ui()

const form = reactive({ username: '', password: '', remember: false })
const submitting = ref(false)
const error = ref('')

function v2Home(): string {
  const role = String(auth.effectiveRole || auth.userRole || 'STUDENT').toLowerCase()
  return ROLE_HOME[role as V2Role] || ROLE_HOME.student
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
  return v2Home()
}

async function submit() {
  if (submitting.value) return
  if (!form.username.trim() || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await auth.login({
      username: form.username.trim(),
      password: form.password,
      remember: form.remember,
      rememberMe: form.remember,
    })
    clearCourses()
    toast('登录成功', 'ok')
    await router.replace(targetAfterAuth())
  } catch (err) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      '登录失败，请检查用户名和密码'
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (auth.isLoggedIn) router.replace(targetAfterAuth())
})
</script>
