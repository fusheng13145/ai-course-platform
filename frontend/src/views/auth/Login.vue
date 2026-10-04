<template>
  <div class="auth-page">
    <div class="auth-card animate-in">
      <!-- 左侧品牌区 -->
      <div class="brand-side">
        <div class="brand-glow"></div>
        <div class="brand-inner">
          <div class="brand-mark-lg">
            <svg
              viewBox="0 0 24 24"
              width="40"
              height="40"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                d="m22 9-10-5L2 9l10 5 10-5zM6 11.5V16c0 1.66 2.69 3 6 3s6-1.34 6-3v-4.5M22 9v5"
              />
            </svg>
          </div>
          <h1 class="brand-title">云枢教学平台</h1>
          <p class="brand-sub">知识图谱驱动 · AI 智能助教</p>
          <div class="features">
            <div v-for="(f, i) in features" :key="i" class="feat-item">
              <svg
                viewBox="0 0 24 24"
                width="15"
                height="15"
                fill="none"
                stroke="currentColor"
                stroke-width="2.4"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M20 6 9 17l-5-5" />
              </svg>
              <span>{{ f }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="form-side">
        <div class="theme-row"><ThemeSwitcher /></div>
        <div class="form-header">
          <h2>欢迎回来</h2>
          <p>登录您的账号</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="0"
          size="large"
          class="form-wrap"
        >
          <div class="field">
            <label>用户名</label>
            <el-input
              ref="usernameRef"
              v-model="form.username"
              placeholder="用户名 / 邮箱"
              autocomplete="off"
              name="login-username"
              readonly
              class="auth-input"
              @focus="onInputFocus"
            />
          </div>
          <div class="field">
            <label>密码</label>
            <el-input
              ref="passwordRef"
              v-model="form.password"
              type="password"
              placeholder="密码"
              autocomplete="off"
              name="login-password"
              readonly
              class="auth-input"
              @focus="onInputFocus"
              @keyup.enter="handleLogin"
            />
          </div>
          <div class="flex between mb-12">
            <el-checkbox v-model="form.remember">记住我</el-checkbox>
            <router-link to="/forgot-password" class="forgot">忘记密码？</router-link>
          </div>
          <button
            class="btn btn-primary btn-lg btn-block"
            :disabled="auth.isLoading"
            type="button"
            @click="handleLogin"
          >
            <span v-if="auth.isLoading" class="btn-loader-dot"></span>
            {{ auth.isLoading ? '登录中...' : '登 录' }}
          </button>
        </el-form>

        <div class="form-foot">
          <span>还没有账号？</span>
          <router-link to="/register">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { resolvePostAuthRedirect } from '@/utils/redirect'
import { ElMessage } from 'element-plus'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref(null)
const usernameRef = ref(null)
const passwordRef = ref(null)
const showError = ref(false)

const features = ['AI 智能辅导', '学习数据分析', '知识图谱构建']

const form = reactive({ username: '', password: '', remember: false })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function clearNativeInput(elInputRef) {
  const inputEl = elInputRef?.$el?.querySelector('input')
  if (inputEl) {
    inputEl.value = ''
    inputEl.dispatchEvent(new Event('input', { bubbles: true }))
  }
}

function onInputFocus() {
  // 输入框默认 readonly，focus 时移除只读以允许用户输入；
  // 此组合（autocomplete=off + readonly + 动态移除 readonly）可阻止多数浏览器的自动填充。
  usernameRef.value?.input?.removeAttribute('readonly')
  passwordRef.value?.input?.removeAttribute('readonly')
}

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写用户名和密码')
    showError.value = true
    return
  }
  try {
    await auth.login({
      username: form.username,
      password: form.password,
      rememberMe: form.remember,
    })
    ElMessage.success('登录成功')
    // 缺陷修复（P0-6）：redirect 参数来自 URL、攻击者可控，必须校验为站内路径，
    // 否则 `?redirect=//evil.example` 会在登录成功瞬间把用户送出站点（开放重定向）。
    router.push(resolvePostAuthRedirect(route.query.redirect, auth.getDefaultRoute()))
  } catch (e) {
    showError.value = true
    if (e?.response?.data?.message) ElMessage.error(e.response.data.message)
    else ElMessage.error('登录失败，请检查用户名和密码')
  }
}

onMounted(() => {
  // 已登录（user 存在）访问 /login 自动重定向；token 为 HttpOnly Cookie 内存占位恒空，不可作为判定
  if (auth.isLoggedIn) router.push(auth.getDefaultRoute())
  // 禁止浏览器自动填充：页面刚打开时账号/密码输入框保持空白
  // 浏览器可能在脚本执行后继续回填已保存的凭据，使用延时二次清空兜底
  form.username = ''
  form.password = ''
  clearNativeInput(usernameRef.value)
  clearNativeInput(passwordRef.value)
  setTimeout(() => {
    form.username = ''
    form.password = ''
    clearNativeInput(usernameRef.value)
    clearNativeInput(passwordRef.value)
  }, 50)
})
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(900px 480px at 85% -10%, rgba(193, 59, 48, 0.06), transparent 60%),
    radial-gradient(800px 420px at -8% 6%, rgba(37, 99, 168, 0.05), transparent 58%), var(--bg);
}
.auth-card {
  display: flex;
  max-width: 880px;
  width: 100%;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: var(--sh-modal);
  border: 1px solid var(--line);
  background: var(--surface);
  min-height: 520px;
}

/* 左侧品牌 */
.brand-side {
  width: 380px;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
  background: #c13b30;
  color: var(--surface);
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand-glow {
  position: absolute;
  top: -60%;
  right: -40%;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.18), transparent 70%);
}
.brand-inner {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 40px 32px;
}
.brand-mark-lg {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  margin: 0 auto 20px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  display: grid;
  place-items: center;
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
}
.brand-title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
  margin-bottom: 6px;
}
.brand-sub {
  font-size: 13px;
  opacity: 0.78;
  margin-bottom: 28px;
}
.features {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}
.feat-item {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
  opacity: 0.88;
}
.feat-item svg {
  color: var(--ok);
}

/* 右侧表单 */
.form-side {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 40px 44px;
}
.theme-row {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}
.form-header {
  margin-bottom: 24px;
}
.form-header h2 {
  font-size: 22px;
  font-weight: 700;
}
.form-header p {
  font-size: 13px;
  color: var(--ink-3);
  margin-top: 4px;
}
.form-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}
.form-foot {
  text-align: center;
  font-size: 13px;
  margin-top: 16px;
  color: var(--ink-3);
}
.form-foot a {
  color: var(--accent);
  font-weight: 600;
  margin-left: 4px;
}
.forgot {
  font-size: 12.5px;
  color: var(--ink-3);
}
.btn-loader-dot {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: var(--surface);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.auth-input :deep(.el-input__wrapper) {
  border-radius: 8px !important;
  border-color: var(--line-2) !important;
  box-shadow: none !important;
  padding: 8px 12px !important;
}
.auth-input :deep(.el-input__wrapper:hover) {
  border-color: var(--line-strong) !important;
}
.auth-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--accent) !important;
  box-shadow: 0 0 0 3px var(--accent-softer) !important;
}

@media (max-width: 768px) {
  .auth-card {
    flex-direction: column;
    max-width: 420px;
  }
  .brand-side {
    width: 100%;
    min-height: 180px;
  }
  .brand-inner {
    padding: 24px;
  }
  .form-side {
    padding: 28px 24px;
  }
}
</style>
