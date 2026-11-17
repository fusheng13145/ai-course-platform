<template>
  <section class="messages-body">
    <div v-if="error" class="callout danger" role="alert">
      <span>通知</span>
      <p>{{ error }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="load">
        {{ loading ? '重试中…' : '重试' }}
      </button>
    </div>

    <section v-else class="card table-card">
      <header class="card-head">
        <div>
          <h2>通知</h2>
        </div>
        <div class="filter-row message-tools">
          <span v-if="unreadCount" class="chip chip-warn">{{ unreadCount }} 条未读</span>
          <button
            class="btn btn-sm mark-all-btn"
            :disabled="!unreadCount || allMarking || loading"
            @click="markAll"
          >
            {{ allMarking ? '处理中…' : '全部已读' }}
          </button>
        </div>
      </header>

      <div v-if="loading && !rows.length" class="empty-state compact">
        <b>正在加载通知…</b>
      </div>

      <ul v-else-if="rows.length" class="message-list">
        <li v-for="item in rows" :key="item.id">
          <button
            type="button"
            class="message-item"
            :class="{ 'is-unread': item.unread }"
            @click="openDetail(item)"
          >
            <span class="message-dot" aria-hidden="true"></span>
            <span class="message-main">
              <span class="message-title-row">
                <b class="message-title">{{ item.title }}</b>
                <span class="chip" :class="item.unread ? 'chip-warn' : 'chip-mute'">
                  {{ item.unread ? '未读' : '已读' }}
                </span>
              </span>
              <span class="message-content">{{ item.content || '—' }}</span>
              <small class="cell-sub">{{ formatTime(item.time) }}</small>
            </span>
          </button>
        </li>
      </ul>

      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="bell" /></span>
        <b>暂无通知</b>
      </div>
    </section>

    <Modal :open="detailOpen" title="通知" width="560px" @update:open="detailOpen = $event">
      <div v-if="active" class="message-detail">
        <h3>{{ active.title }}</h3>
        <div class="message-detail-meta">
          <span class="chip chip-mute">{{ active.kind }}</span>
          <time>{{ formatTime(active.time) }}</time>
        </div>
        <p>{{ active.content || '—' }}</p>
      </div>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Icon from '../../components/Icon.vue'
import Modal from '../../components/Modal.vue'
import { useUi } from '../../composables/ui'
import { loadNotifications, type Notification } from '../../data/api'
import { markAllAsRead, markAsRead } from '@/api/notification'

const { toast } = useUi()

const rows = ref<Notification[]>([])
const loading = ref(false)
const error = ref('')
const markingId = ref<Notification['id'] | null>(null)
const allMarking = ref(false)
const active = ref<Notification | null>(null)
const detailOpen = ref(false)

const unreadCount = computed(() => rows.value.filter((row) => row.unread).length)

function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await loadNotifications(true)
  } catch {
    rows.value = []
    error.value = '通知加载失败，请重试'
  } finally {
    loading.value = false
  }
}

async function markRead(item: Notification, quiet = false) {
  if (!item.unread || markingId.value !== null || allMarking.value) return
  markingId.value = item.id
  try {
    await markAsRead(item.id)
    item.unread = false
    if (!quiet) toast('已标记为已读', 'ok')
  } catch {
    toast('标记已读失败，请重试', 'danger')
  } finally {
    markingId.value = null
  }
}

async function openDetail(item: Notification) {
  active.value = item
  detailOpen.value = true
  await markRead(item, true)
}

async function markAll() {
  if (!unreadCount.value || allMarking.value || markingId.value !== null) return
  allMarking.value = true
  try {
    await markAllAsRead()
    rows.value = rows.value.map((row) => ({ ...row, unread: false }))
    toast('全部通知已标记为已读', 'ok')
  } catch {
    toast('全部已读失败，请重试', 'danger')
  } finally {
    allMarking.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.messages-body {
  display: grid;
  gap: 14px;
}
.callout.danger {
  border-left-color: var(--danger);
}
.callout.danger > span {
  color: var(--danger);
}
.message-tools {
  width: auto;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
}
.message-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.message-item {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  background: var(--surface);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.message-item:hover {
  border-color: var(--line-strong);
}
.message-item.is-unread {
  border-color: var(--line-strong);
  background: var(--surface-2);
}
.message-dot {
  width: 6px;
  height: 6px;
  margin-top: 6px;
  border-radius: var(--r-circle);
  background: var(--line-strong);
}
.message-item.is-unread .message-dot {
  background: var(--accent);
}
.message-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}
.message-title-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.message-title {
  font-size: 12px;
}
.message-content {
  display: block;
  margin: 0;
  font-size: 11px;
  line-height: 1.7;
  color: var(--ink-2);
}
.message-detail {
  display: grid;
  gap: 12px;
}
.message-detail h3 {
  margin: 0;
  font-size: 15px;
}
.message-detail-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--ink-2);
  font-size: 11px;
}
.message-detail p {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>
