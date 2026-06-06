<template>
  <div class="forgot-page">
    <div class="forgot-card">
      <h2>找回密码</h2>
      <p style="color:#909399;margin-bottom:24px;">输入您的邮箱，我们将发送重置链接</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="注册邮箱" prefix-icon="Message" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width:100%" @click="handleSubmit">发送重置链接</el-button>
        </el-form-item>
      </el-form>
      <div style="text-align:center;">
        <router-link to="/login" style="font-size:13px;color:#409eff;">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ email: '' })
const rules = { email: [{ required: true, type: 'email', message: '请输入有效邮箱', trigger: 'blur' }] }

function handleSubmit() {
  ElMessage.success('演示模式：重置链接已发送（实际需后端支持）')
}
</script>

<style scoped>
.forgot-page { height:100vh; display:flex; align-items:center; justify-content:center; background:linear-gradient(135deg,#667eea 0%,#764ba2 100%); }
.forgot-card { width:400px; padding:40px; background:#fff; border-radius:12px; box-shadow:0 8px 32px rgba(0,0,0,0.15); text-align:center; }
.forgot-card h2 { font-size:24px; color:#303133; margin:0 0 8px; }
</style>