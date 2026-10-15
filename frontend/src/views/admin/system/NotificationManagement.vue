<template>
  <div>
    <PageHeader title="通知公告" subtitle="按已读状态分组 · 管理员可向指定角色批量发送" icon="bell">
      <button class="btn btn-primary" @click="drawerOpen = true">
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
        发布通知
      </button>
    </PageHeader>

    <!-- 通知列表（未读/已读分组 + 行操作） -->
    <NmNoticeList
      :groups="noticeGroups"
      :loading="loading"
      @open="openNotice"
      @mark-read="doRead"
      @menu="openMenu"
    />

    <!-- 通知详情弹窗 -->
    <Modal v-if="detailNotice" wide @close="detailNotice = null">
      <template #title>通知详情</template>
      <template #sub>#{{ detailNotice.id }}</template>
      <div>
        <div class="l3-block">
          <div class="strong notice-title">{{ detailNotice.title }}</div>
          <div class="small muted mb-12">
            {{ catLabel(detailNotice.category) }} · {{ priorityLabel(detailNotice.priority) }} ·
            {{ fmtTime(detailNotice.createdAt) }}
          </div>
          <div class="notice-content">{{ detailNotice.content }}</div>
        </div>
      </div>
    </Modal>

    <!-- 发布通知抽屉（自包含发送逻辑） -->
    <NmPublishDrawer v-model="drawerOpen" @published="load" />

    <!-- 更多菜单 -->
    <DropdownMenu
      v-if="ctx.visible"
      :items="ctx.items"
      :x="ctx.x"
      :y="ctx.y"
      @close="ctx.visible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import Modal from '@/components/ui/Modal.vue'
import DropdownMenu from '@/components/ui/DropdownMenu.vue'
import { toast } from '@/composables/useToast'
import notificationApi from '@/api/notification'
import { confirmDanger } from '@/utils/dangerConfirm'
import NmNoticeList from './components/NmNoticeList.vue'
import NmPublishDrawer from './components/NmPublishDrawer.vue'

/**
 * 通知公告管理页（拆分子组件后页面壳）。
 * <p>职责收敛：数据加载/详情/已读/删除/菜单；列表（NmNoticeList）
 * 与发布（NmPublishDrawer）分别下沉。</p>
 */
const CAT_LABELS = { SYSTEM: '系统公告', ASSIGNMENT: '作业提醒', EXAM: '考试通知' }
const PRIORITY_LABELS = { HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级' }

const loading = ref(true)
const unread = ref([])
const read = ref([])

const detailNotice = ref(null)
const drawerOpen = ref(false)
const ctx = ref({ visible: false, x: 0, y: 0, items: [], notice: null })

const noticeGroups = computed(() => [
  {
    key: 'unread',
    label: '未读',
    icon: 'clock',
    open: true,
    icoBg: 'var(--warn-soft)',
    icoColor: 'var(--warn)',
    icoPath: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2',
    rows: unread.value,
  },
  {
    key: 'read',
    label: '已读',
    icon: 'checks',
    open: false,
    icoBg: 'var(--ok-soft)',
    icoColor: 'var(--ok)',
    icoPath: 'M18 6 7 17l-5-5M22 10 11 21l-2.5-2.5',
    rows: read.value,
  },
])

function catLabel(c) {
  return CAT_LABELS[c] || c || '其他'
}
function priorityLabel(p) {
  return PRIORITY_LABELS[p] || p || '—'
}
function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}

async function load() {
  loading.value = true
  try {
    const [unreadRes, readRes] = await Promise.all([
      notificationApi.getNotificationList({ page: 1, size: 50, isRead: false }),
      notificationApi.getNotificationList({ page: 1, size: 50, isRead: true }),
    ])
    unread.value = unreadRes?.data?.data?.list || unreadRes?.data?.list || []
    read.value = readRes?.data?.data?.list || readRes?.data?.list || []
  } catch {
    toast.show('通知加载失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openNotice(n) {
  detailNotice.value = n
}

async function doRead(n) {
  try {
    await notificationApi.markAsRead(n.id)
    n.isRead = true
    unread.value = unread.value.filter((x) => x.id !== n.id)
    read.value.unshift(n)
  } catch {
    toast.show('操作失败', 'error')
  }
}

function openMenu(e, n) {
  const r = e.currentTarget.getBoundingClientRect()
  const ops = [{ text: '查看详情', icon: 'eye', onClick: () => openNotice(n) }]
  if (!n.isRead) ops.push({ text: '标记已读', icon: 'check', onClick: () => doRead(n) })
  ops.push('-', { text: '删除', icon: 'trash', danger: true, onClick: () => doDelete(n) })
  ctx.value = { visible: true, x: r.left, y: r.bottom + 4, items: ops }
}

async function doDelete(n) {
  ctx.value.visible = false
  // D2-2：删除类操作必须二次确认（原实现右键菜单点击即删，无任何确认）
  const ok = await confirmDanger({
    verb: '删除',
    target: '通知',
    subject: n.title || n.id,
    impact: '该通知将从发送记录中移除，学生端已收到的内容不受影响',
  })
  if (!ok) return
  try {
    await notificationApi.deleteNotification(n.id)
    const src = n.isRead ? read.value : unread.value
    const i = src.findIndex((x) => x.id === n.id)
    if (i > -1) src.splice(i, 1)
    toast.show('通知已删除', 'ok')
  } catch {
    toast.show('删除失败', 'error')
  }
}
</script>

<style scoped>
.notice-title {
  font-size: 15px;
  margin-bottom: 6px;
}

.notice-content {
  line-height: 1.9;
  color: var(--ink-2);
  font-size: 13.5px;
  white-space: pre-wrap;
}
</style>
