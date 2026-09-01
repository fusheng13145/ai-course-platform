<template>
  <div>
    <div class="table-toolbar">
      <div class="toolbar-right">
        <el-select
          :model-value="roleFilter"
          placeholder="角色筛选"
          clearable
          style="width: 140px"
          @update:model-value="$emit('update:roleFilter', $event)"
        >
          <el-option label="全部角色" value="" />
          <el-option label="管理员" value="ADMIN" />
          <el-option label="教师" value="TEACHER" />
          <el-option label="学生" value="STUDENT" />
        </el-select>
        <el-input
          :model-value="searchKeyword"
          placeholder="搜索菜单/按钮标识"
          clearable
          style="width: 220px"
          :prefix-icon="Search"
          @update:model-value="$emit('update:searchKeyword', $event)"
        />
      </div>
    </div>

    <!-- 权限列表表格 -->
    <div v-loading="loading" class="content-card">
      <el-table :data="list" style="width: 100%" empty-text="暂无权限数据">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="role" label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small" effect="light">{{
              roleLabel(row.role)
            }}</el-tag>
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
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small" effect="light">{{
              row.isActive ? '启用' : '停用'
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="$emit('edit', row)">编辑</el-button>
            <el-button
              text
              :type="row.isActive ? 'warning' : 'success'"
              @click="$emit('toggle', row)"
              >{{ row.isActive ? '停用' : '启用' }}</el-button
            >
            <el-button text type="danger" @click="$emit('delete', row)">删除</el-button>
          </template>
        </el-table-column>
        <!-- 空状态 -->
        <template #empty>
          <el-empty description="暂无权限数据">
            <template #image><div class="empty-deco">🔐</div></template>
          </el-empty>
        </template>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'
import { formatTime } from '@/utils/datetime'

defineProps({
  loading: { type: Boolean, default: false },
  list: { type: Array, default: () => [] },
  roleFilter: { type: String, default: '' },
  searchKeyword: { type: String, default: '' },
})
defineEmits(['update:roleFilter', 'update:searchKeyword', 'edit', 'toggle', 'delete'])

function roleLabel(role) {
  const map = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
  return map[role] || role
}
function roleTagType(role) {
  const map = { ADMIN: 'danger', TEACHER: 'success', STUDENT: 'warning' }
  return map[role] || 'info'
}
</script>

<style scoped>
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
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-md);
  min-height: 300px;
}
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
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
@media (max-width: 900px) {
  .table-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .toolbar-right {
    flex-direction: column;
  }
}
</style>
