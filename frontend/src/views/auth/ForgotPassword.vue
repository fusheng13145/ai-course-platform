<template>
  <div class="auth-page">
    <div class="auth-container">
      <div class="brand-section">
        <div class="brand-content">
          <div class="brand-icon"><el-icon :size="44"><Lock /></el-icon></div>
          <h1 class="brand-title">找回密码</h1>
          <p class="brand-subtitle">安全重置您的密码</p>

          <div class="tips-list">
            <div v-for="tip in tips" :key="tip.text" class="tip-item">
              <div class="tip-icon"><el-icon><component :is="tip.icon" /></el-icon></div>
              <span>{{ tip.text }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="form-section">
        <div class="form-card">
          <div class="theme-toggle-row"><ThemeSwitcher /></div>
          <div class="form-header">
            <h2>找回密码</h2>
            <p>通过邮箱验证码安全重置</p>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
            <el-form-item prop="email">
              <el-input v-model="form.email" placeholder="注册邮箱" />
            </el-form-item>
            <el-form-item prop="code">
              <div class="code-wrapper">
                <el-input v-model="form.code" placeholder="验证码" style="flex:1" />
                <el-button type="primary" plain :disabled="countdown > 0 || sending" @click="sendCode" class="code-btn">
                  {{ countdown > 0 ? countdown + 's' : (sending ? '发送中' : '获取验证码') }}
                </el-button>
              </div>
            </el-form-item>
            <el-form-item prop="newPassword">
              <el-input v-model="form.newPassword" type="password" placeholder="新密码（至少6位）" show-password />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input v-model="form.confirmPassword" type="password" placeholder="确认新密码" show-password @keyup.enter="handleSubmit" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" class="submit-btn" :loading="loading" @click="handleSubmit">重置密码</el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <span>想起密码了？</span><router-link to="/login" class="switch-link">返回登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import authApi from '@/api/auth'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
let timer = null

const tips = [
  { icon: 'Message', text: '输入您注册时使用的邮箱地址' },
  { icon: 'Key', text: '查收邮箱中的6位数字验证码' },
  { icon: 'Lock', text: '设置一个安全的新密码' },
]

const form = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})

const rules = {
  email: [
    { required: true, type: 'email', message: '请输入有效邮箱', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: (r, v, cb) => v === form.newPassword ? cb() : cb(new Error('两次密码不一致')), trigger: 'blur' }
  ],
}

async function sendCode() {
  if (!form.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    ElMessage.warning('请输入有效的邮箱地址')
    return
  }
  sending.value = true
  try {
    await authApi.sendVerificationCode({ email: form.email, type: 'FORGOT_PASSWORD' })
    ElMessage.success('验证码已发送到邮箱')
    startCountdown()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '发送验证码失败')
  } finally {
    sending.value = false
  }
}

function startCountdown() {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!form.code) {
    ElMessage.warning('请输入验证码')
    return
  }
  loading.value = true
  try {
    await authApi.resetPassword({
      email: form.email,
      code: form.code,
      newPassword: form.newPassword,
    })
    ElMessage.success({ message: '密码重置成功，请登录', duration: 1500 })
    setTimeout(() => router.push('/login'), 500)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '密码重置失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ac-bg);
  padding: 20px;
}

.auth-container {
  display: flex;
  width: 100%;
  max-width: 1000px;
  min-height: 560px;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-xl);
  box-shadow: var(--ac-shadow-xl);
  overflow: hidden;
}

.brand-section {
  flex: 1;
  background: linear-gradient(145deg, #B89A8A 0%, #9B8B82 40%, #7A8BA5 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 36px;
}

.brand-content {
  text-align: center;
  color: #fff;
}

.brand-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 28px;
  background: rgba(255, 255, 255, 0.16);
  border-radius: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-title {
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 10px;
  letter-spacing: 2px;
}

.brand-subtitle {
  font-size: 15px;
  opacity: 0.85;
  margin-bottom: 32px;
}

.tips-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 280px;
  margin: 0 auto;
  text-align: left;
}

.tip-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.09);
  border-radius: 12px;
  font-size: 13.5px;
}

.tip-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: rgba(255,255,255,0.18);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
  font-size: 15px;
}

.form-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 36px;
}

.theme-toggle-row {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.form-card {
  width: 100%;
  max-width: 370px;
}

.form-header {
  text-align: center;
  margin-bottom: 28px;
}

.form-header h2 {
  font-size: 25px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin-bottom: 8px;
}

.form-header p {
  font-size: 14px;
  color: var(--ac-text-secondary);
}

.code-wrapper {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.code-btn {
  height: 38px;
  flex-shrink: 0;
  white-space: nowrap;
  min-width: 110px;
}

.submit-btn {
  width: 100%;
  height: 42px;
}

.form-footer {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: var(--ac-text-secondary);
}

.switch-link {
  color: #B89A8A;
  text-decoration: none;
  font-weight: 600;
  margin-left: 4px;
}

.switch-link:hover {
  color: #987A6A;
}

@media (max-width: 768px) {
  .brand-section { display: none; }
  .auth-container { max-width: 420px; }
}
</style>
