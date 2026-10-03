<template>
  <div>
    <PageHeader title="用户管理" subtitle="平台账号总览 · 按角色分组维护账号与权限" icon="users">
      <input
        v-model="search"
        class="input search-input"
        placeholder="搜索姓名 / 账号 / 邮箱"
        @input="onSearchInput"
      />
      <button class="btn btn-plain btn-sm" @click="openImport">
        <svg
          viewBox="0 0 24 24"
          width="14"
          height="14"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" />
        </svg>
        批量导入
      </button>
      <button class="btn btn-primary btn-sm" @click="showCreate = true">
        <svg
          viewBox="0 0 24 24"
          width="14"
          height="14"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 5v14M5 12h14" />
        </svg>
        新增用户
      </button>
    </PageHeader>

    <!-- 统计卡片 -->
    <div class="stat-grid animate-in d1">
      <StatCard label="平台总用户" :value="overview.totalUsers ?? 0" icon="users" />
      <StatCard
        label="教师账号"
        :value="overview.roleDistribution?.teacher ?? 0"
        icon="grad"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="学生账号"
        :value="overview.roleDistribution?.student ?? 0"
        icon="user"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="管理员账号"
        :value="overview.roleDistribution?.admin ?? 0"
        icon="shield"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
    </div>

    <!-- 用户管理主卡片 -->
    <section class="l1-card animate-in d2">
      <div class="l1-body">
        <UmGroupTable
          :loading="loading"
          :groups="groups"
          @open-detail="openDetail"
          @open-reset="openReset"
          @open-delete="openDelete"
        />
      </div>
    </section>

    <!-- 用户详情弹窗（内嵌 Tab + 权限树） -->
    <UmDetailDialog
      v-if="detailUser"
      v-model:detail-tab="detailTab"
      :user="detailUser"
      :perm-loading="permLoading"
      :perm-nodes="permNodes"
      @close="detailUser = null"
    />

    <!-- 重置密码弹窗 -->
    <UmResetDialog
      v-if="resetUser"
      :user="resetUser"
      :submitting="resetting"
      @close="resetUser = null"
      @submit="handleReset"
    />

    <!-- 删除确认弹窗 -->
    <UmDeleteDialog
      v-if="deleteUser"
      :user="deleteUser"
      :deleting="deleting"
      @close="deleteUser = null"
      @confirm="handleDelete"
    />

    <!-- 新增用户弹窗 -->
    <UmCreateDialog
      v-if="showCreate"
      :creating="creating"
      @close="showCreate = false"
      @submit="handleCreate"
    />

    <!-- 批量导入弹窗（Excel） -->
    <UmImportDialog
      v-if="showImport"
      :importing="importing"
      :result="importResult"
      @close="showImport = false"
      @submit="handleImport"
    />
  </div>
</template>

<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import UmGroupTable from './components/UmGroupTable.vue'
import UmDetailDialog from './components/UmDetailDialog.vue'
import UmResetDialog from './components/UmResetDialog.vue'
import UmDeleteDialog from './components/UmDeleteDialog.vue'
import UmCreateDialog from './components/UmCreateDialog.vue'
import UmImportDialog from './components/UmImportDialog.vue'
import { useUserManagement } from '@/composables/useUserManagement'

const {
  overview,
  search,
  loading,
  groups,
  detailUser,
  detailTab,
  permLoading,
  permNodes,
  showCreate,
  creating,
  showImport,
  importing,
  importResult,
  openImport,
  handleImport,
  resetUser,
  resetting,
  deleteUser,
  deleting,
  onSearchInput,
  openDetail,
  handleCreate,
  openReset,
  handleReset,
  openDelete,
  handleDelete,
} = useUserManagement()
</script>

<style scoped>
.search-input {
  width: 220px;
  height: 32px;
}
</style>
