<template>
  <div class="auth-page">
    <div class="auth-container">
      <!-- 左侧品牌区 -->
      <div class="brand-section">
        <div class="brand-content">
          <div class="brand-icon">
            <el-icon :size="44"><School /></el-icon>
          </div>
          <h1 class="brand-title">AI 智能课程平台</h1>
          <p class="brand-subtitle">加入AI智能课程平台</p>

          <div class="features">
            <div v-for="feat in features" :key="feat.label" class="feature-item">
              <div class="feature-num">{{ features.indexOf(feat) + 1 }}</div>
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
            <h2>创建账号</h2>
            <p>填写以下信息完成注册</p>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large" @submit.prevent="handleRegister">
            <el-form-item prop="role">
              <el-radio-group v-model="form.role" class="role-group">
                <el-radio-button value="STUDENT">学生</el-radio-button>
                <el-radio-button value="TEACHER">教师</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="用户名" />
            </el-form-item>
            <el-form-item prop="fullName">
              <el-input v-model="form.fullName" placeholder="真实姓名" />
            </el-form-item>
            <el-form-item prop="phone">
              <el-input v-model="form.phone" placeholder="手机号" maxlength="11" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="密码（至少6位）" show-password />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" show-password @keyup.enter="handleRegister" />
            </el-form-item>

            <el-form-item>
              <el-checkbox v-model="form.agreement" class="agree-check">
                我已阅读并同意<a href="javascript:;" class="agree-link">《用户协议》</a>
              </el-checkbox>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" class="register-btn" :loading="loading" :disabled="!form.agreement" @click="handleRegister">立即注册</el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <span>已有账号？</span><router-link to="/login" class="switch-link">立即登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

const router = useRouter()
const store = useAuthStore()
const formRef = ref(null)
const loading = ref(false)

const features = [
  { label: '个性化学习' },
  { label: '实时数据分析' },
  { label: '知识图谱构建' },
]

const form = reactive({
  username: '',
  fullName: '',
  phone: '',
  role: 'STUDENT',
  password: '',
  confirmPassword: '',
  agreement: false,
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 3, max: 20, message: '长度在3-20个字符', trigger: 'blur' }],
  fullName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, { pattern: /^1[3-9]\d{9}$/, message: '请输入有效手机号', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码至少6位', trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请确认密码', trigger: 'blur' }, { validator: (r, v, cb) => v === form.password ? cb() : cb(new Error('两次密码不一致')), trigger: 'blur' }],
}

async function handleRegister() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await store.register(form)
    ElMessage.success({ message: '注册成功，请登录', duration: 1500 })
    setTimeout(() => router.push('/login'), 500)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '注册失败')
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
  background: linear-gradient(135deg, #8BA888 0%, #7A8BA5 40%, #B89A8A 100%);
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
  background: rgba(255, 255, 255, 0.18);
  border-radius: 22px;
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
  margin-bottom: 28px;
}

.features {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 18px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 13px;
  font-size: 14px;
}

.feature-num {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: rgba(255,255,255,0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
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
  max-width: 380px;
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

.role-group {
  width: 100%;
  display: flex;
}

.role-group :deep(.el-radio-button) {
  flex: 1;
}

.role-group :deep(.el-radio-button__inner) {
  width: 100%;
  text-align: center;
}

.agree-check {
  font-size: 13px;
  color: var(--ac-text-secondary);
}

.agree-link {
  color: #8BA888;
  text-decoration: none;
  margin: 0 2px;
}

.agree-link:hover {
  text-decoration: underline;
}

.register-btn {
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
  color: #8BA888;
  text-decoration: none;
  font-weight: 600;
  margin-left: 4px;
}

.switch-link:hover {
  color: #6B8868;
}

@media (max-width: 768px) {
  .brand-section { display: none; }
  .auth-container { max-width: 420px; }
}
</style>
