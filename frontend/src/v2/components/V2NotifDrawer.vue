<template>
  <div v-if="notifOpen" class="notif-layer" @click.self="notifOpen = false">
    <aside class="notif-drawer" role="dialog" aria-modal="true" aria-label="通知">
      <header>
        <div>
          <p class="eyebrow">NOTIFICATIONS</p>
          <h2>
            通知 <span>{{ unread }}</span>
          </h2>
        </div>
        <button class="icon-btn" aria-label="关闭通知" @click="notifOpen = false">
          <V2Icon name="x" />
        </button>
      </header>
      <div class="notif-tools">
        <button class="text-btn" @click="markAllRead">全部已读</button>
      </div>
      <ul class="notif-list">
        <li
          v-for="(item, index) in rows"
          :key="index"
          class="notif-item"
          :class="{ 'is-unread': item.unread }"
        >
          <span class="notif-item-dot"></span>
          <span class="notif-main">
            <b>{{ item.title }}</b>
            <small>{{ item.meta }}</small>
          </span>
          <time>{{ item.time }}</time>
        </li>
      </ul>
      <button class="btn btn-ghost btn-block" @click="openCenter">打开通知中心</button>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import V2Icon from './V2Icon.vue'
import { useV2Ui } from '../composables/ui'
import { DB } from '../data/demoSource'
import { loadNotifications } from '../data/api'

interface Row {
  title: string
  meta: string
  time: string
  unread: boolean
}

const { role, notifOpen, unread, toast } = useV2Ui()
const router = useRouter()
const live = ref<Row[] | null>(null)
const demoRead = ref(false)

const rows = computed<Row[]>(() => {
  if (live.value && live.value.length) return live.value
  const raw = (DB.roles as Record<string, { notifications?: Row[] }>)[role.value]?.notifications
  return (raw || []).map((item) => ({
    title: item.title,
    meta: item.meta,
    time: item.time,
    unread: Boolean(item.unread) && !demoRead.value,
  }))
})

onMounted(async () => {
  const list = await loadNotifications()
  if (!list.length) return
  live.value = list.map((item) => ({
    title: item.title,
    meta: item.content || item.kind,
    time: item.time,
    unread: item.unread,
  }))
  const count = list.filter((item) => item.unread).length
  if (count) unread.value = count
})

function markAllRead() {
  demoRead.value = true
  if (live.value) live.value = live.value.map((item) => ({ ...item, unread: false }))
  unread.value = 0
  toast('全部通知已标记为已读')
}

function openCenter() {
  notifOpen.value = false
  router.push(`/${role.value}/messages`)
}
</script>
