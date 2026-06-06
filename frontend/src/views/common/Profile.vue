<template>
  <div>
    <div class="content-card" style="max-width:600px;margin:0 auto">
      <div style="text-align:center;margin-bottom:32px">
        <el-avatar :size="80" style="margin-bottom:12px"><el-icon :size="40"><User /></el-icon></el-avatar>
        <h3 style="margin:0">{{ user.fullName || user.username }}</h3>
        <el-tag :type="user.role === 'STUDENT' ? 'warning' : user.role === 'TEACHER' ? 'success' : 'danger'" size="small">{{ user.role }}</el-tag>
      </div>
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名"><el-input v-model="form.username" disabled /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.fullName" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item><el-button type="primary">保存修改</el-button></el-form-item>
      </el-form>
      <el-divider />
      <h4 style="margin:0 0 16px">修改密码</h4>
      <el-form label-width="80px">
        <el-form-item label="当前密码"><el-input v-model="passwordForm.oldPassword" type="password" /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" /></el-form-item>
        <el-form-item label="确认密码"><el-input v-model="passwordForm.confirmPassword" type="password" /></el-form-item>
        <el-form-item><el-button type="primary">修改密码</el-button></el-form-item>
      </el-form>
    </div>
  </div>
</template>
<script setup>
import { reactive } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const user = auth.user || {}
const form = reactive({ username: user.username || '', fullName: user.fullName || '', email: user.email || '', phone: user.phone || '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
</script>