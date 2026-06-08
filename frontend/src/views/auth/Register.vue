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
            <h2>创建账号</h2>
            <p>加入AI智能课程平台，开启智能学习</p>
          </div>
          
          <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="fullName">
              <el-input v-model="form.fullName" placeholder="真实姓名" prefix-icon="UserFilled" />
            </el-form-item>
            <el-form-item prop="phone">
              <el-input v-model="form.phone" placeholder="手机号" prefix-icon="Phone" maxlength="11" />
            </el-form-item>
            <el-form-item prop="role">
              <el-select v-model="form.role" placeholder="选择角色" style="width:100%">
                <el-option label="我是学生" value="STUDENT" />
                <el-option label="我是教师" value="TEACHER" />
              </el-select>
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="密码（至少6位）" prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" prefix-icon="Lock" show-password @keyup.enter="handleRegister" />
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="form.agreement">我已阅读并同意<el-link type="primary">《用户协议》</el-link></el-checkbox>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" style="width:100%" @click="handleRegister">立即注册</el-button>
            </el-form-item>
          </el-form>
          
          <div class="form-footer">
            <span>已有账号？</span>
            <router-link to="/login">立即登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const store = useAuthStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  fullName: '',
  phone: '',
  role: '',
  password: '',
  confirmPassword: '',
  agreement: false
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在3-20个字符', trigger: 'blur' }
  ],
  fullName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效手机号', trigger: 'blur' }
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: (r, v, cb) => v === form.password ? cb() : cb(new Error('两次密码不一致')), trigger: 'blur' }
  ],
  agreement: [{ validator: (r, v, cb) => v ? cb() : cb(new Error('请同意用户协议')), trigger: 'change' }]
}

async function handleRegister() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    loading.value = true
    await store.register(form)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '注册失败')
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
  min-height: 600px;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-xl);
  box-shadow: var(--ac-shadow-xl);
  overflow: hidden;
}

/* 左侧品牌区 */
.brand-section {
  flex: 1;
  background: linear-gradient(135deg, #7A8BA5 0%, #B89A8A 100%);
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

.feature-item:nth-child(1) .el-icon { color: #B89A8A; }
.feature-item:nth-child(2) .el-icon { color: #7A8BA5; }
.feature-item:nth-child(3) .el-icon { color: #CBB58A; }

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

@media (max-width: 768px) {
  .brand-section {
    display: none;
  }
  
  .auth-container {
    max-width: 400px;
  }
}
</style>
