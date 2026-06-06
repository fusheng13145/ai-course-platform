<template>
  <div class="register-page">
    <div class="register-card">
      <div class="register-header">
        <h2>注册账号</h2>
        <p>创建您的 AI 学习平台账号</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="fullName">
          <el-input v-model="form.fullName" placeholder="姓名" prefix-icon="Edit" />
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="邮箱" prefix-icon="Message" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="role">
          <el-select v-model="form.role" placeholder="选择角色" style="width:100%">
            <el-option label="学生" value="STUDENT" />
            <el-option label="教师" value="TEACHER" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width:100%" @click="handleRegister">注 册</el-button>
        </el-form-item>
      </el-form>
      <div style="text-align:center;margin-top:16px;">
        <router-link to="/login" style="font-size:13px;color:#409eff;">已有账号？立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '', fullName: '', email: '', password: '', confirmPassword: '', role: 'STUDENT'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  fullName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ required: true, type: 'email', message: '请输入有效邮箱', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少6位', trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: (r, v, cb) => v === form.password ? cb() : cb(new Error('密码不一致')), trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

async function handleRegister() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await auth.register({ username: form.username, fullName: form.fullName, email: form.email, password: form.password, role: form.role })
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } catch (e) {
      ElMessage.error(e.message || '注册失败')
    } finally { loading.value = false }
  })
}
</script>

<style scoped>
.register-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.register-card {
  width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.15);
}
.register-header { text-align: center; margin-bottom: 32px; }
.register-header h2 { font-size: 24px; color: #303133; margin: 0 0 8px; }
.register-header p { font-size: 14px; color: #909399; margin: 0; }
</style>