<template>
  <div>
    <div class="table-toolbar"><div class="toolbar-left"><el-button type="primary"><el-icon><Plus /></el-icon>添加权限</el-button></div><div class="toolbar-right"><el-select v-model="roleFilter" placeholder="角色" clearable style="width:120px"><el-option label="管理员" value="ADMIN" /><el-option label="教师" value="TEACHER" /><el-option label="学生" value="STUDENT" /></el-select></div></div>
    <div class="content-card">
      <el-table :data="permissionList" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="role" label="角色" width="100"><template #default="{row}"><el-tag :type="row.role === 'ADMIN' ? 'danger' : row.role === 'TEACHER' ? 'success' : 'warning'" size="small">{{ row.role }}</el-tag></template></el-table-column>
        <el-table-column prop="menuKey" label="菜单权限" />
        <el-table-column prop="isActive" label="状态" width="80"><template #default="{row}"><el-tag :type="row.isActive ? 'success' : 'info'" size="small">{{ row.isActive ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="180" fixed="right"><template #default="{row}"><el-button text type="primary">编辑</el-button><el-button :type="row.isActive ? 'warning' : 'success'" text @click="row.isActive = !row.isActive">{{ row.isActive ? '禁用' : '启用' }}</el-button></template></el-table-column>
      </el-table>
    </div>
  </div>
</template>
<script setup>
import { ref, computed } from 'vue'
import permissionApi from '@/api/permission'
const permissions = ref([])
const roleFilter = ref('')
const permissionList = computed(() => roleFilter.value ? permissions.value.filter(p => p.role === roleFilter.value) : permissions.value)
permissions.value = [{ id: 1, role: 'ADMIN', menuKey: '全部权限', isActive: true }, { id: 2, role: 'TEACHER', menuKey: '课程管理,考试管理', isActive: true }, { id: 3, role: 'STUDENT', menuKey: '我的课程,知识图谱,AI助手', isActive: true }]
</script>