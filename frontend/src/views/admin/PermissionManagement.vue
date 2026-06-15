﻿﻿﻿<template>
  <div class="fade-in permission-management">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">权限管理</h2>
        <p class="page-desc">系统角色权限配置与菜单按钮控制</p>
      </div>
    </div>

    <!-- 统计概览 -->
    <div class="stats-row">
      <div v-for="(stat, idx) in permissionStats" :key="idx" class="stat-mini-card" :class="stat.class">
        <span class="label">{{ stat.label }}</span>
        <span class="value">{{ stat.value }}</span>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="openAddDialog"><el-icon><Plus /></el-icon>添加权限</el-button>
      </div>
      <div class="toolbar-right">
        <el-select v-model="roleFilter" placeholder="角色筛选" clearable style="width:140px">
          <el-option label="全部角色" value="" />
          <el-option label="管理员" value="ADMIN" />
          <el-option label="教师" value="TEACHER" />
          <el-option label="学生" value="STUDENT" />
        </el-select>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索菜单/按钮标识"
          clearable
          style="width:220px"
          :prefix-icon="Search"
        />
      </div>
    </div>

    <!-- 权限列表表格 -->
    <div class="content-card" v-loading="loading">
      <el-table
        :data="filteredList"
        style="width:100%"
        empty-text="暂无权限数据"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="role" label="角色" width="110">
          <template #default="{ row }">
            <el-tag
              :type="roleTagType(row.role)"
              size="small"
              effect="light"
            >{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="menuKey" label="菜单权限名" min-width="160">
          <template #default="{ row }">
            <span class="menu-key-text">{{ row.menuKey || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="buttonKey" label="按钮权限标识" min-width="150">
          <template #default="{ row }">
            <code class="button-key-code">{{ row.buttonKey || '-' }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="isActive" label="状态" width="90">
          <template #default="{ row }">
            <el-tag
              :type="row.isActive ? 'success' : 'info'"
              size="small"
              effect="light"
            >{{ row.isActive ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button
              text
              :type="row.isActive ? 'warning' : 'success'"
              @click="handleToggle(row)"
            >{{ row.isActive ? '停用' : '启用' }}</el-button>
            <el-button text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <!-- 空状态 -->
        <template #empty><el-empty description="暂无权限数据" /></template>
      </el-table>
    </div>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑权限' : '添加权限'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" placeholder="请选择角色" style="width:100%">
            <el-option label="管理员 (ADMIN)" value="ADMIN" />
            <el-option label="教师 (TEACHER)" value="TEACHER" />
            <el-option label="学生 (STUDENT)" value="STUDENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单权限 Key" prop="menuKey">
          <el-input v-model="form.menuKey" placeholder="如：course-manage、exam-list" />
        </el-form-item>
        <el-form-item label="按钮权限 Key" prop="buttonKey">
          <el-input v-model="form.buttonKey" placeholder="如：create、edit、delete（可选）" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="权限描述（可选）"
          />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="form.isActive" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import permissionApi from '@/api/permission'

/* ==================== 数据定义 ==================== */

// 权限列表原始数据
const permissionList = ref([])
// 加载状态
const loading = ref(false)
// 提交中状态
const submitting = ref(false)

// 筛选条件
const roleFilter = ref('')
const searchKeyword = ref('')

// 对话框状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

// 表单数据
const form = reactive({
  id: null,
  role: '',
  menuKey: '',
  buttonKey: '',
  description: '',
  isActive: true
})

// 表单校验规则
const rules = {
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  menuKey: [{ required: true, message: '请输入菜单权限 Key', trigger: 'blur' }]
}

/* ==================== 统计数据 ==================== */

// 统计卡片数据
const permissionStats = computed(() => {
  const list = permissionList.value
  return [
    { label: '总权限数', value: list.length, class: '' },
    { label: '已启用', value: list.filter(p => p.isActive).length, class: 'active' },
    { label: '已停用', value: list.filter(p => !p.isActive).length, class: 'inactive' },
    { label: '管理员专用', value: list.filter(p => p.role === 'ADMIN').length, class: 'admin' }
  ]
})

/* ==================== 列表筛选 ==================== */

// 经过筛选的列表数据
const filteredList = computed(() => {
  let list = permissionList.value
  // 角色筛选
  if (roleFilter.value) {
    list = list.filter(p => p.role === roleFilter.value)
  }
  // 关键词搜索（匹配 menuKey 或 buttonKey）
  if (searchKeyword.value) {
    const q = searchKeyword.value.toLowerCase()
    list = list.filter(
      p =>
        (p.menuKey && p.menuKey.toLowerCase().includes(q)) ||
        (p.buttonKey && p.buttonKey.toLowerCase().includes(q))
    )
  }
  return list
})

/* ==================== 角色辅助函数 ==================== */

/** 角色值 → 中文标签 */
function roleLabel(role) {
  const map = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
  return map[role] || role
}

/** 角色 → tag 类型 */
function roleTagType(role) {
  const map = { ADMIN: 'danger', TEACHER: 'success', STUDENT: 'warning' }
  return map[role] || 'info'
}

/** 格式化时间 */
function formatTime(time) {
  if (!time) return '-'
  const d = new Date(time)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/* ==================== 数据加载 ==================== */

onMounted(async () => {
  await fetchPermissionList()
})

/** 从后端获取全部权限列表 */
async function fetchPermissionList() {
  loading.value = true
  try {
    const res = await permissionApi.getAllPermissions()
        const rawData = res.data?.records || res.data?.list || res.data || res
    permissionList.value = Array.isArray(rawData) ? rawData : []
  } catch (e) {
    console.error('获取权限列表失败:', e)
    ElMessage.error('获取权限列表失败')
    permissionList.value = []
  } finally {
    loading.value = false
  }
}

/* ==================== 对话框操作 ==================== */

/** 打开新增对话框 */
function openAddDialog() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

/** 打开编辑对话框 */
function openEditDialog(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    role: row.role,
    menuKey: row.menuKey,
    buttonKey: row.buttonKey || '',
    description: row.description || '',
    isActive: row.isActive ?? true
  })
  dialogVisible.value = true
}

/** 重置表单 */
function resetForm() {
  form.id = null
  form.role = ''
  form.menuKey = ''
  form.buttonKey = ''
  form.description = ''
  form.isActive = true
  // 清除校验状态
  formRef.value?.resetFields()
}

/* ==================== 增删改操作 ==================== */

/** 提交表单（新增或编辑） */
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        role: form.role,
        menuKey: form.menuKey,
        buttonKey: form.buttonKey,
        description: form.description,
        isActive: form.isActive
      }
      if (isEdit.value) {
        // 编辑模式 — 调用更新接口
        await permissionApi.updatePermission(form.id, payload)
        // 更新本地列表中的对应项
        const idx = permissionList.value.findIndex(p => p.id === form.id)
        if (idx !== -1) {
          Object.assign(permissionList.value[idx], payload)
        }
        ElMessage.success('权限更新成功')
      } else {
        // 新增模式 — 调用创建接口
        const res = await permissionApi.createPermission(payload)
        const newItem = res.data || res
        permissionList.value.push(newItem)
        ElMessage.success('权限创建成功')
      }
      dialogVisible.value = false
    } catch (e) {
      console.error('保存失败:', e)
      ElMessage.error(isEdit.value ? '更新失败，请重试' : '创建失败，请重试')
    } finally {
      submitting.value = false
    }
  })
}

/** 切换启用 / 停用状态 */
function handleToggle(row) {
  const action = row.isActive ? '停用' : '启用'
  ElMessageBox.confirm(`确定${action}该权限项吗？`, '操作确认', { type: 'warning' })
    .then(async () => {
      try {
        await permissionApi.togglePermission(row.id)
        row.isActive = !row.isActive
        ElMessage.success(`${action}成功`)
      } catch (e) {
        console.error('切换状态失败:', e)
        ElMessage.error(`${action}失败`)
      }
    })
    .catch(() => {})
}

/** 删除权限 */
function handleDelete(row) {
  ElMessageBox.confirm(`确定删除该权限项吗？删除后不可恢复。`, '删除确认', { type: 'warning' })
    .then(async () => {
      try {
        await permissionApi.deletePermission(row.id)
        permissionList.value = permissionList.value.filter(p => p.id !== row.id)
        ElMessage.success('删除成功')
      } catch (e) {
        console.error('删除失败:', e)
        ElMessage.error('删除失败，请重试')
      }
    })
    .catch(() => {})
}
</script>

<style scoped>
.permission-management {
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
  grid-template-columns: repeat(4, 1fr);
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

.stat-mini-card.active .value   { color: var(--ac-success); }
.stat-mini-card.inactive .value { color: var(--ac-warning); }
.stat-mini-card.admin .value    { color: var(--ac-danger); }

/* 工具栏 */
.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: var(--ac-spacing-md);
}

.toolbar-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

/* 内容卡片 */
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-md);
  min-height: 300px;
}

/* 表格内文字样式 */
.menu-key-text {
  font-weight: 500;
  color: var(--ac-text-primary);
}

.button-key-code {
  background: var(--ac-bg);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--ac-secondary);
  font-family: 'Consolas', 'Monaco', monospace;
}

/* 入场动画 */
.fade-in {
  animation: fadeInUp 0.4s ease-out;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 响应式适配 */
@media (max-width: 900px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .table-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-right {
    flex-direction: column;
  }
}
</style>
