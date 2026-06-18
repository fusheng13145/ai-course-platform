<template>
  <div class="auth-page">
    <div class="auth-container">
      <!-- 左侧品牌区 -->
      <div class="brand-section">
        <div class="brand-content">
          <div class="brand-icon">
            <el-icon :size="48"><School /></el-icon>
          </div>
          <h1 class="brand-title">AI 智能课程平台</h1>
          <p class="brand-subtitle">开启您的智能学习之旅</p>

          <div class="features">
            <div v-for="feat in features" :key="feat.label" class="feature-item">
              <div class="feature-icon-wrap">
                <el-icon><component :is="feat.icon" /></el-icon>
              </div>
              <span>{{ feat.label }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="form-section">
        <div class="form-card">
          <div class="theme-toggle-row"><ThemeSwitcher /></div>

          <div class="form-header">
            <h2>欢迎回来</h2>
            <p>登录您的账号继续学习</p>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="用户名">
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="密码" show-password @keyup.enter="handleLogin">
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item>
              <div class="form-options">
                <el-checkbox v-model="form.remember">记住我</el-checkbox>
                <router-link to="/forgot-password" class="forgot-link">忘记密码？</router-link>
              </div>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" class="login-btn" :loading="auth.isLoading" @click="handleLogin">登 录</el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <span>还没有账号？</span>
            <router-link to="/register" class="switch-link">立即注册</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref(null)

const features = [
  { icon: 'MagicStick', label: 'AI 智能辅导' },
  { icon: 'TrendCharts', label: '学习数据分析' },
  { icon: 'Connection', label: '知识图谱构建' },
]

const form = reactive({
  username: '',
  password: '',
  remember: true,
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    await auth.login({ username: form.username, password: form.password, remember: form.remember })
    ElMessage.success({ message: '登录成功', duration: 1500 })
    const redirect = route.query.redirect || auth.getDefaultRoute()
    setTimeout(() => router.push(redirect), 300)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '登录失败，请检查用户名或密码')
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
  background: linear-gradient(135deg, #7A8BA5 0%, #9B8AA5 50%, #B89A8A 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
}

.brand-content {
  text-align: center;
  color: #fff;
}

.brand-icon {
  width: 88px;
  height: 88px;
  margin: 0 auto 28px;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-title {
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 10px;
  letter-spacing: 1px;
}

.brand-subtitle {
  font-size: 15px;
  opacity: 0.85;
  margin-bottom: 44px;
}

.features {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 13px 20px;
  background: rgba(255, 255, 255, 0.09);
  border-radius: 14px;
  font-size: 14px;
}

.feature-icon-wrap {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: rgba(255,255,255,0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.feature-icon-wrap .el-icon {
  font-size: 17px;
  color: #fff;
}

.form-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
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
  margin-bottom: 32px;
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

.login-btn {
  width: 100%;
  height: 42px;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.forgot-link {
  font-size: 13px;
  color: #7A8BA5;
  text-decoration: none;
}

.forgot-link:hover {
  color: #5D6B82;
}

.form-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
  color: var(--ac-text-secondary);
}

.switch-link {
  color: #7A8BA5;
  text-decoration: none;
  font-weight: 600;
  margin-left: 4px;
}

.switch-link:hover {
  color: #5D6B82;
}

@media (max-width: 768px) {
  .brand-section { display: none; }
  .auth-container { max-width: 420px; }
}
</style>
