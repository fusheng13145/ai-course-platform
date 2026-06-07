<template>
  <div>
    <div class="content-card" style="max-width:600px;margin:0 auto">
      <div style="text-align:center;margin-bottom:32px">
        <el-avatar :size="80" style="margin-bottom:12px"><el-icon :size="40"><User /></el-icon></el-avatar>
        <h3 style="margin:0">{{ form.fullName || form.username }}</h3>
        <el-tag :type="user.role === 'STUDENT' ? 'warning' : user.role === 'TEACHER' ? 'success' : 'danger'" size="small">{{ user.role }}</el-tag>
      </div>
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名"><el-input v-model="form.username" disabled /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.fullName" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item><el-button type="primary" @click="saveProfile">保存修改</el-button></el-form-item>
      </el-form>
      <el-divider />
      <h4 style="margin:0 0 16px">修改密码</h4>
      <el-form label-width="80px">
        <el-form-item label="当前密码"><el-input v-model="passwordForm.oldPassword" type="password" /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" /></el-form-item>
        <el-form-item label="确认密码"><el-input v-model="passwordForm.confirmPassword" type="password" /></el-form-item>
        <el-form-item><el-button type="primary" @click="changePassword">修改密码</el-button></el-form-item>
      </el-form>
    </div>
  </div>
</template>
<script setup>
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import authApi from '@/api/auth'

const auth = useAuthStore()
const user = auth.user || {}
const form = reactive({ username: user.username || '', fullName: user.fullName || '', email: user.email || '', phone: user.phone || '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

async function saveProfile() {
  try {
    await authApi.updateCurrentUser({ fullName: form.fullName, email: form.email, phone: form.phone })
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function changePassword() {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  try {
    await authApi.changePassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword })
    ElMessage.success('密码修改成功')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } catch (e) {
    ElMessage.error('密码修改失败')
  }
}
</script>
