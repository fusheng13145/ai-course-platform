<template>
  <div class="fade-in permission-management">
    <PageHeader
      title="权限管理"
      subtitle="系统角色权限配置与菜单按钮控制"
      icon="shield"
      icon-bg="accent"
    >
      <el-button type="primary" @click="openAddDialog"
        ><el-icon><Plus /></el-icon>添加权限</el-button
      >
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="总权限数"
        :value="permissionStats[0].value"
        icon="shield"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="已启用"
        :value="permissionStats[1].value"
        icon="check"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="已停用"
        :value="permissionStats[2].value"
        icon="warn"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="管理员专用"
        :value="permissionStats[3].value"
        icon="lock"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
    </div>

    <!-- 工具栏 + 列表 -->
    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background: var(--accent-soft); color: var(--accent)">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
          </svg>
        </span>
        <div>
          <div class="l1-title">权限列表</div>
          <div class="l1-sub">按角色筛选，维护菜单与按钮级权限</div>
        </div>
      </div>
      <div class="l1-body">
        <PmPermissionTable
          v-model:role-filter="roleFilter"
          v-model:search-keyword="searchKeyword"
          :loading="loading"
          :list="filteredList"
          @edit="openEditDialog"
          @toggle="handleToggle"
          @delete="handleDelete"
        />
      </div>
    </section>

    <!-- 添加/编辑对话框 -->
    <PmFormDialog
      v-model="dialogVisible"
      :is-edit="isEdit"
      :submitting="submitting"
      :form="form"
      :rules="rules"
      @submit="handleSubmit"
    />
  </div>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import PmPermissionTable from './components/PmPermissionTable.vue'
import PmFormDialog from './components/PmFormDialog.vue'
import { usePermissionManagement } from '@/composables/usePermissionManagement'

const {
  loading,
  submitting,
  roleFilter,
  searchKeyword,
  dialogVisible,
  isEdit,
  form,
  rules,
  permissionStats,
  filteredList,
  openAddDialog,
  openEditDialog,
  handleSubmit,
  handleToggle,
  handleDelete,
} = usePermissionManagement()
</script>

<style scoped>
.permission-management {
  max-width: 1400px;
  margin: 0 auto;
}
</style>
