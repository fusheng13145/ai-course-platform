<template>
  <Modal wide @close="emit('close')">
    <template #title>用户详情 · {{ user.fullName || user.username }}</template>
    <template #sub>#{{ user.id }} · {{ roleLabel(user.role) }}</template>
    <div>
      <TabGroup
        :tabs="detailTabs"
        :active="detailTab"
        style="pill"
        @change="emit('update:detailTab', $event)"
      />
      <div class="mt-16">
        <!-- 基本信息 -->
        <div v-if="detailTab === 'info'" class="desc-grid">
          <div v-for="(v, k) in infoItems" :key="k" class="desc-item">
            <span class="d-label">{{ k }}</span
            ><span class="d-value">{{ v }}</span>
          </div>
        </div>
        <!-- 权限配置（角色权限，来自权限表） -->
        <div v-if="detailTab === 'perm'">
          <div class="l3-block">
            <div class="l3-title">
              <svg
                viewBox="0 0 24 24"
                width="14"
                height="14"
                fill="none"
                stroke="currentColor"
                stroke-width="1.9"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path
                  d="m21 2-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0 3 3L22 7l-3-3m-3.5 3.5L19 4"
                />
              </svg>
              角色「{{ roleLabel(user.role) }}」权限配置
            </div>
            <div v-if="permLoading" class="empty-state"><div class="e-title">加载中…</div></div>
            <div v-else-if="permNodes.length === 0" class="empty-state">
              <div class="e-title">该角色暂无权限配置</div>
            </div>
            <TreeView v-else :nodes="permNodes" @select="() => {}" />
          </div>
        </div>
      </div>
    </div>
    <template #footer>
      <button class="btn btn-plain" @click="emit('close')">关闭</button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import TreeView from '@/components/ui/TreeView.vue'
import TabGroup from '@/components/ui/TabGroup.vue'
import Modal from '@/components/ui/Modal.vue'

const props = defineProps({
  user: { type: Object, default: null },
  detailTab: { type: String, default: 'info' },
  permLoading: { type: Boolean, default: false },
  permNodes: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:detailTab', 'close'])

const ROLE_LABELS = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
const detailTabs = [
  { key: 'info', label: '基本信息' },
  { key: 'perm', label: '权限配置' },
]

function roleLabel(r) {
  return ROLE_LABELS[r] || r || '—'
}
function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}
function statusOf(u) {
  if (u.lockedUntil && new Date(u.lockedUntil) > new Date()) return { text: '锁定', cls: 'amber' }
  if (u.isActive === false) return { text: '停用', cls: 'red' }
  return { text: '正常', cls: 'green' }
}

const infoItems = computed(() => {
  const u = props.user || {}
  const st = statusOf(u)
  return {
    姓名: u.fullName || '—',
    登录账号: u.username || '—',
    用户编号: u.id != null ? '#' + u.id : '—',
    角色: roleLabel(u.role),
    所属单位: u.department || '—',
    邮箱: u.email || '—',
    手机号: u.phone || '—',
    账号状态: st.text,
    最近登录: fmtTime(u.lastLogin),
    注册时间: fmtTime(u.createdAt),
  }
})
</script>
