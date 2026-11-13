<template>
  <div v-if="notifOpen" class="notif-layer" @click.self="notifOpen = false">
    <aside class="notif-drawer" role="dialog" aria-modal="true" aria-label="通知">
      <header>
        <div>
          <h2>
            通知 <span>{{ unread }}</span>
          </h2>
        </div>
        <button class="icon-btn" aria-label="关闭通知" @click="notifOpen = false">
          <Icon name="x" />
        </button>
      </header>
      <div class="notif-tools">
        <button class="text-btn" :disabled="marking || !rows.length" @click="markAllRead">
          {{ marking ? '处理中…' : '全部已读' }}
        </button>
      </div>
      <p v-if="loadError" class="notif-empty">{{ loadError }}</p>
      <p v-else-if="!rows.length" class="notif-empty">暂无通知</p>
      <ul v-else class="notif-list">
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
import Icon from './Icon.vue'
import { useUi } from '../composables/ui'
import { loadNotifications } from '../data/api'
import { markAllAsRead } from '@/api/notification'

interface Row {
  title: string
  meta: string
  time: string
  unread: boolean
}

const { role, notifOpen, unread, toast } = useUi()
const router = useRouter()
const live = ref<Row[]>([])
const loadError = ref('')
const marking = ref(false)

const rows = computed<Row[]>(() => live.value)

onMounted(async () => {
  try {
    const list = await loadNotifications(true)
    live.value = list.map((item) => ({
      title: item.title,
      meta: item.content || item.kind,
      time: item.time,
      unread: item.unread,
    }))
    unread.value = live.value.filter((item) => item.unread).length
  } catch {
    live.value = []
    unread.value = 0
    loadError.value = '通知加载失败，请重试'
  }
})

async function markAllRead() {
  if (!live.value.length || marking.value) return
  marking.value = true
  try {
    await markAllAsRead()
    live.value = live.value.map((item) => ({ ...item, unread: false }))
    unread.value = 0
    toast('全部通知已标记为已读', 'ok')
  } catch {
    toast('全部已读失败，请重试', 'danger')
  } finally {
    marking.value = false
  }
}

function openCenter() {
  notifOpen.value = false
  router.push(`/${role.value}/messages`)
}
</script>
