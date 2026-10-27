<template>
  <div class="auth-page">
    <div class="auth-card-single animate-in">
      <div class="theme-row"><ThemeSwitcher /></div>

      <div class="brand-mini">
        <span class="bm-mark">
          <svg
            viewBox="0 0 24 24"
            width="22"
            height="22"
            fill="none"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path
              d="m22 9-10-5L2 9l10 5 10-5zM6 11.5V16c0 1.66 2.69 3 6 3s6-1.34 6-3v-4.5M22 9v5"
            />
          </svg>
        </span>
        <div>
          <div class="bm-name">云枢教学平台</div>
          <div class="bm-sub">安全 · 找回您的账号密码</div>
        </div>
      </div>

      <div class="form-header">
        <h2>找回密码</h2>
        <p>通过注册邮箱验证身份，三步即可重置</p>
      </div>

      <!-- 分步指示器 -->
      <div class="steps">
        <div class="step" :class="{ active: step === 1, done: step > 1 }">
          <span class="s-dot">{{ step > 1 ? '✓' : '1' }}</span> 发送验证码
        </div>
        <div class="step-line"></div>
        <div class="step" :class="{ active: step === 2, done: step > 2 }">
          <span class="s-dot">{{ step > 2 ? '✓' : '2' }}</span> 验证邮箱
        </div>
        <div class="step-line"></div>
        <div class="step" :class="{ active: step === 3 }">
          <span class="s-dot">3</span> 重置密码
        </div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        class="form-wrap"
        scroll-to-error
      >
        <el-form-item prop="email">
          <div class="field">
            <label>注册邮箱</label>
            <el-input
              v-model="form.email"
              placeholder="请输入注册邮箱"
              :disabled="step > 1"
              class="auth-input"
              v-bind="inputA11y('注册邮箱', true)"
            >
              <template #prefix
                ><svg
                  viewBox="0 0 24 24"
                  width="16"
                  height="16"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path
                    d="M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM22 6l-10 7L2 6"
                  /></svg
              ></template>
            </el-input>
          </div>
        </el-form-item>

        <el-form-item v-if="step >= 2" prop="code">
          <div class="field">
            <label>验证码</label>
            <div class="flex gap-8">
              <el-input
                v-model="form.code"
                placeholder="6 位验证码"
                class="auth-input"
                style="flex: 1"
                v-bind="inputA11y('验证码', true)"
              />
              <button
                class="btn btn-plain btn-sm"
                :disabled="codeSent || sending"
                style="flex-shrink: 0; min-width: 104px"
                @click="sendCode"
              >
                {{ codeSent ? countdownText : '重新获取' }}
              </button>
            </div>
          </div>
        </el-form-item>

        <el-form-item v-if="step >= 3" prop="newPwd">
          <div class="field">
            <label>新密码</label>
            <el-input
              v-model="form.newPwd"
              type="password"
              placeholder="至少 6 位"
              show-password
              class="auth-input"
              v-bind="inputA11y('新密码', true)"
            />
          </div>
        </el-form-item>
        <el-form-item v-if="step >= 3" prop="confirmPwd">
          <div class="field">
            <label>确认新密码</label>
            <el-input
              v-model="form.confirmPwd"
              type="password"
              placeholder="再次输入"
              show-password
              class="auth-input"
              v-bind="inputA11y('确认新密码', true)"
            />
          </div>
        </el-form-item>

        <button
          v-if="step === 1"
          class="btn btn-primary btn-lg btn-block"
          :disabled="sending"
          v-bind="buttonA11y('发送验证码', sending)"
          type="button"
          @click="sendCode"
        >
          <span v-if="sending" class="btn-loader-dot"></span
          >{{ sending ? '发送中...' : '发送验证码' }}
        </button>
        <button
          v-if="step === 2"
          class="btn btn-primary btn-lg btn-block"
          :disabled="sending"
          v-bind="buttonA11y('验证并继续', sending)"
          type="button"
          @click="verifyCode"
        >
          <span v-if="sending" class="btn-loader-dot"></span
          >{{ sending ? '验证中...' : '验证并继续' }}
        </button>
        <button
          v-if="step === 3"
          class="btn btn-primary btn-lg btn-block"
          :disabled="sending"
          v-bind="buttonA11y('重置密码', sending)"
          type="button"
          @click="resetPassword"
        >
          <span v-if="sending" class="btn-loader-dot"></span
          >{{ sending ? '重置中...' : '重置密码' }}
        </button>
      </el-form>

      <div class="form-foot"><router-link to="/login">← 返回登录</router-link></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import { inputA11y, buttonA11y } from '@/composables/useA11y'
import { validateFormFields, type ElFormLike } from '@/composables/useFormSubmit'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<ElFormLike | null>(null)
const step = ref(1)
const codeSent = ref(false)
const sending = ref(false)
const countdown = ref(0)
let timer = null
const countdownText = ref('重新获取')

const form = reactive({ email: '', code: '', newPwd: '', confirmPwd: '' })
const rules = {
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  // 与后端统一密码策略（com.aicourse.util.PasswordPolicy）对齐：8-32 位，含大小写字母、数字、特殊字符
  newPwd: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 32, message: '密码长度需在 8-32 个字符之间', trigger: 'blur' },
    {
      pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[~!@#$%^&*()_+\-={}[\]:;"'<>,.?/\\|`]).{8,32}$/,
      message: '密码必须包含大写字母、小写字母、数字和特殊字符，且长度为8-32位',
      trigger: 'blur',
    },
  ],
}

function startCountdown() {
  countdown.value = 60
  countdownText.value = `${countdown.value}s 后重发`
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(timer)
      countdownText.value = '重新获取'
    } else countdownText.value = `${countdown.value}s 后重发`
  }, 1000)
}

async function sendCode() {
  if (sending.value) return
  if (!form.email) {
    ElMessage.warning('请输入邮箱')
    return
  }
  // D2-4：分步校验——失败时 el-form 滚动定位该字段并显示内联错误
  if (!(await validateFormFields(formRef, ['email']))) return
  sending.value = true
  try {
    await auth.sendResetCode(form.email)
    codeSent.value = true
    step.value = 2
    startCountdown()
    ElMessage.success('验证码已发送')
  } catch {
    ElMessage.error('发送失败')
  } finally {
    sending.value = false
  }
}

async function verifyCode() {
  if (sending.value) return
  if (!form.code) {
    ElMessage.warning('请输入验证码')
    return
  }
  if (!(await validateFormFields(formRef, ['code']))) return
  sending.value = true
  try {
    await auth.verifyResetCode(form.email, form.code)
    step.value = 3
    ElMessage.success('验证通过')
  } catch {
    ElMessage.error('验证失败')
  } finally {
    sending.value = false
  }
}

async function resetPassword() {
  if (sending.value) return
  if (!form.newPwd) {
    ElMessage.warning('请输入新密码')
    return
  }
  if (form.newPwd !== form.confirmPwd) {
    ElMessage.warning('两次密码不一致')
    return
  }
  // D2-4：新密码规则（8-32 位 + 大小写/数字/特殊字符）此前仅声明未执行，
  // 现由 validate 强制执行，避免弱密码通过前端提交（与后端 PasswordPolicy 同口径）
  if (!(await validateFormFields(formRef, ['newPwd', 'confirmPwd']))) return
  sending.value = true
  try {
    await auth.resetPassword(form.email, form.code, form.newPwd)
    ElMessage.success('密码已重置')
    router.push('/login')
  } catch {
    ElMessage.error('重置失败')
  } finally {
    sending.value = false
  }
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
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
.auth-card-single {
  max-width: 460px;
  width: 100%;
  border-radius: 20px;
  box-shadow: var(--sh-modal);
  border: 1px solid var(--line);
  background: var(--surface);
  padding: 32px 40px 36px;
}
.theme-row {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 4px;
}
.brand-mini {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}
.bm-mark {
  width: 44px;
  height: 44px;
  border-radius: 13px;
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: var(--surface);
  display: grid;
  place-items: center;
  box-shadow:
    0 6px 18px rgba(193, 59, 48, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.25);
}
.bm-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--ink);
  letter-spacing: 0.3px;
}
.bm-sub {
  font-size: 11.5px;
  color: var(--ink-3);
  margin-top: 1px;
}
.form-header {
  margin-bottom: 20px;
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
  gap: 4px;
}
/* D2-4：el-form-item 包裹后收平默认间距（间距由 .form-wrap gap 承担），
   并让内容区回归块级，避免默认 flex 行布局影响 .field 纵向结构 */
.form-wrap :deep(.el-form-item) {
  margin-bottom: 0;
}
.form-wrap :deep(.el-form-item__content) {
  display: block;
  line-height: inherit;
}
.form-foot {
  text-align: center;
  font-size: 13px;
  margin-top: 22px;
}
.form-foot a {
  color: var(--info);
  font-weight: 500;
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
.btn-loader-dot {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: var(--surface);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  display: inline-block;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 480px) {
  .auth-card-single {
    padding: 24px 20px 28px;
  }
}
</style>
