<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h2>AI 智能课程平台</h2>
        <p>登录您的账号</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.remember">记住我</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="auth.isLoading" style="width:100%" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>
      <div class="login-footer">
        <router-link to="/register">立即注册</router-link>
        <router-link to="/forgot-password">忘记密码</router-link>
      </div>
      <div class="demo-info" v-if="showError">
        <p style="color:#909399;font-size:12px;margin-top:12px;">演示账号：admin/teacher/student，密码任意</p>
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
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.15);
}
.login-header { text-align: center; margin-bottom: 32px; }
.login-header h2 { font-size: 24px; color: #303133; margin: 0 0 8px; }
.login-header p { font-size: 14px; color: #909399; margin: 0; }
.login-footer { display: flex; justify-content: space-between; margin-top: 16px; }
.login-footer a { font-size: 13px; color: #409eff; }
</style>