<template>
  <div class="auth-page">
    <div class="auth-container">
      <!-- 左侧品牌区 -->
      <div class="brand-section">
        <div class="brand-content">
          <div class="brand-icon">
            <el-icon :size="48"><School /></el-icon>
          </div>
          <h1>AI 智能课程平台</h1>
          <p>开启您的智能学习之旅</p>
          <div class="features">
            <div class="feature-item">
              <el-icon><MagicStick /></el-icon>
              <span>AI 智能辅导</span>
            </div>
            <div class="feature-item">
              <el-icon><TrendCharts /></el-icon>
              <span>学习数据分析</span>
            </div>
            <div class="feature-item">
              <el-icon><Connection /></el-icon>
              <span>知识图谱构建</span>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 右侧表单区 -->
      <div class="form-section">
        <div class="form-card">
          <div class="form-header">
            <h2>欢迎回来</h2>
            <p>登录您的账号继续学习</p>
          </div>
          
          <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
            </el-form-item>
            <el-form-item>
              <div class="form-options">
                <el-checkbox v-model="form.remember">记住我</el-checkbox>
                <router-link to="/forgot-password" class="forgot-link">忘记密码？</router-link>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="auth.isLoading" style="width:100%" @click="handleLogin">登 录</el-button>
            </el-form-item>
          </el-form>
          
          <div class="form-footer">
            <span>还没有账号？</span>
            <router-link to="/register">立即注册</router-link>
          </div>
          
          <div class="demo-accounts" v-if="showError">
            <p>演示账号</p>
            <div class="demo-list">
              <span @click="fillDemo('admin', 'admin123')">管理员</span>
              <span @click="fillDemo('teacher1', 'teacher123')">教师</span>
              <span @click="fillDemo('student1', 'student123')">学生</span>
            </div>
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

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref(null)
const showError = ref(false)

const form = reactive({
  username: '',
  password: '',
  remember: true
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function fillDemo(username, password) {
  form.username = username
  form.password = password
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await auth.login({ username: form.username, password: form.password, remember: form.remember })
      ElMessage.success('登录成功')
      const redirect = route.query.redirect || auth.getDefaultRoute()
      router.push(redirect)
    } catch (e) {
      showError.value = true
    }
  })
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
  min-height: 600px;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-xl);
  box-shadow: var(--ac-shadow-xl);
  overflow: hidden;
}

/* 左侧品牌区 */
.brand-section {
  flex: 1;
  background: var(--ac-gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  position: relative;
}

.brand-section::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%23ffffff' fill-opacity='0.05'%3E%3Cpath d='M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E");
  pointer-events: none;
}

.brand-content {
  text-align: center;
  color: #fff;
  position: relative;
  z-index: 1;
}

.brand-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 24px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(10px);
}

.brand-content h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  letter-spacing: -0.5px;
}

.brand-content > p {
  font-size: 16px;
  opacity: 0.9;
  margin-bottom: 40px;
}

.features {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: var(--ac-radius-md);
  backdrop-filter: blur(10px);
  font-size: 14px;
}

.feature-item .el-icon {
  font-size: 20px;
}

/* 右侧表单区 */
.form-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.form-card {
  width: 100%;
  max-width: 360px;
}

.form-header {
  text-align: center;
  margin-bottom: 32px;
}

.form-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin-bottom: 8px;
}

.form-header p {
  font-size: 14px;
  color: var(--ac-text-secondary);
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.forgot-link {
  font-size: 13px;
  color: var(--ac-primary);
  text-decoration: none;
  transition: color var(--ac-transition-fast);
}

.forgot-link:hover {
  color: var(--ac-primary-light);
}

.form-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
  color: var(--ac-text-secondary);
}

.form-footer a {
  color: var(--ac-primary);
  text-decoration: none;
  font-weight: 500;
  margin-left: 4px;
  transition: color var(--ac-transition-fast);
}

.form-footer a:hover {
  color: var(--ac-primary-light);
}

.demo-accounts {
  margin-top: 24px;
  padding: 16px;
  background: var(--ac-primary-bg);
  border-radius: var(--ac-radius-md);
  text-align: center;
}

.demo-accounts p {
  font-size: 12px;
  color: var(--ac-text-secondary);
  margin-bottom: 8px;
}

.demo-list {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.demo-list span {
  font-size: 13px;
  color: var(--ac-primary);
  cursor: pointer;
  padding: 4px 12px;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-sm);
  transition: all var(--ac-transition-fast);
}

.demo-list span:hover {
  background: var(--ac-primary);
  color: #fff;
}

@media (max-width: 768px) {
  .brand-section {
    display: none;
  }
  
  .auth-container {
    max-width: 400px;
  }
}
</style>
