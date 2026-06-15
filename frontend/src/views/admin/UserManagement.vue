﻿﻿﻿<template>
  <div class="fade-in user-management">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">用户管理</h2>
        <p class="page-desc">平台用户账户管理与权限控制</p>
      </div>
    </div>

    <!-- 统计概览 -->
    <div class="stats-row">
      <div v-for="(stat, idx) in userStats" :key="idx" class="stat-mini-card" :class="stat.class">
        <span class="label">{{ stat.label }}</span>
        <span class="value">{{ stat.value }}</span>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="showAdd = true"><el-icon><Plus /></el-icon>添加用户</el-button>
      </div>
      <div class="toolbar-right">
        <el-input v-model="search" placeholder="搜索用户名/邮箱" clearable style="width:220px" />
      </div>
    </div>
    <div class="content-card">
      <el-table :data="filteredList" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="fullName" label="姓名" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : row.role === 'TEACHER' ? 'success' : 'warning'" size="small">{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isActive" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small">{{ row.isActive ? '正常' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="editUser(row)">编辑</el-button>
            <el-button text type="warning" @click="toggleUser(row)">{{ row.isActive ? '禁用' : '启用' }}</el-button>
            <el-button text type="danger" @click="deleteUser(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showAdd" :title="editing ? '编辑用户' : '添加用户'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="姓名" prop="fullName"><el-input v-model="form.fullName" /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width:100%">
            <el-option label="管理员" value="ADMIN" /><el-option label="教师" value="TEACHER" /><el-option label="学生" value="STUDENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!editing"><el-input v-model="form.password" type="password" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import userApi from '@/api/user'

// 用户统计数据
const userStats = ref([
  { label: '总用户数', value: 0, class: '' },
  { label: '管理员', value: 0, class: 'admin' },
  { label: '教师', value: 0, class: 'teacher' },
  { label: '学生', value: 0, class: 'student' }
])

const userList = ref([])
const search = ref('')
const showAdd = ref(false)
const editing = ref(false)
const formRef = ref(null)

const form = reactive({ username: '', fullName: '', email: '', role: 'STUDENT', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  fullName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ required: true, type: 'email', message: '请输入有效邮箱', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const filteredList = computed(() => {
  if (!search.value) return userList.value
  const q = search.value.toLowerCase()
  return userList.value.filter(u => u.username?.toLowerCase().includes(q) || u.email?.toLowerCase().includes(q))
})

onMounted(async () => {
  try {
    const res = await userApi.getUserList()
        const rawData = res.data?.records || res.data?.list || res.data || res
    userList.value = Array.isArray(rawData) ? rawData : []

    // 更新统计数据
    userStats.value[0].value = userList.value.length
    userStats.value[1].value = userList.value.filter(u => u.role === 'ADMIN').length
    userStats.value[2].value = userList.value.filter(u => u.role === 'TEACHER').length
    userStats.value[3].value = userList.value.filter(u => u.role === 'STUDENT').length
  } catch (e) {
    console.error('Failed to load users:', e)
    userList.value = []
  }
})

function editUser(row) {
  editing.value = true
  Object.assign(form, { username: row.username, fullName: row.fullName, email: row.email, role: row.role, password: '' })
  form._id = row.id
  showAdd.value = true
}

function toggleUser(row) {
  const action = row.isActive ? '禁用' : '启用'
  ElMessageBox.confirm(`确定${action}用户 ${row.username}？`, '提示').then(async () => {
    try {
      await userApi.updateUserStatus(row.id, !row.isActive)
      row.isActive = !row.isActive
      ElMessage.success(`${action}成功`)
    } catch (e) { ElMessage.error('操作失败') }
  }).catch(() => {})
}

function deleteUser(row) {
  ElMessageBox.confirm(`确定删除用户 ${row.username}？`, '提示', { type: 'warning' }).then(async () => {
    try {
      await userApi.deleteUser(row.id)
      userList.value = userList.value.filter(u => u.id !== row.id)
      ElMessage.success('删除成功')
    } catch (e) { ElMessage.error('删除失败') }
  }).catch(() => {})
}

async function saveUser() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (editing.value) {
        await userApi.updateUser(form._id, { username: form.username, fullName: form.fullName, email: form.email, role: form.role })
        const u = userList.value.find(u => u.id === form._id)
        if (u) { Object.assign(u, { username: form.username, fullName: form.fullName, email: form.email, role: form.role }) }
      } else {
        const res = await userApi.createUser({ ...form })
        userList.value.push(res.data || res)
      }
      showAdd.value = false; editing.value = false
      ElMessage.success('保存成功')
    } catch (e) { ElMessage.error('保存失败') }
  })
}
</script>

<style scoped>
.user-management {
  max-width: 1400px;
  margin: 0 auto;
}

/* 页面头部 */
.page-header {
  margin-bottom: var(--ac-spacing-lg);
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  letter-spacing: -0.3px;
}

.page-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-top: 4px;
}

/* 统计行 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}

.stat-mini-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: all var(--ac-transition-normal);
}

.stat-mini-card:hover {
  box-shadow: var(--ac-shadow-md);
  transform: translateY(-2px);
}

.stat-mini-card .label {
  font-size: 13px;
  color: var(--ac-text-muted);
  font-weight: 500;
}

.stat-mini-card .value {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
}

.stat-mini-card.admin .value { color: var(--ac-danger); }
.stat-mini-card.teacher .value { color: var(--ac-success); }
.stat-mini-card.student .value { color: var(--ac-primary); }

/* 响应式 */
@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .table-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>