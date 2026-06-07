<template>
  <div>
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
    userList.value = res.data?.list || res.data || res || []
  } catch (e) {
    console.error('Failed to load users:', e)
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