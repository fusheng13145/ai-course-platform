<template>
  <div class="notices-tab">
    <div v-if="role === 'teacher'" class="course-tab-bar">
      <button class="btn btn-primary btn-sm" @click="composerOpen = !composerOpen">
        {{ composerOpen ? '收起' : '发布通知' }}
      </button>
    </div>

    <div v-if="role === 'teacher' && composerOpen" class="card composer">
      <header class="card-head">
        <div>
          <h2>发布通知</h2>
        </div>
      </header>
      <div class="composer-body">
        <input
          v-model="form.title"
          class="input"
          placeholder="标题"
          maxlength="200"
          aria-label="通知标题"
        />
        <textarea
          v-model="form.content"
          class="input content-input"
          rows="4"
          placeholder="正文"
          aria-label="通知正文"
        ></textarea>
        <div class="composer-actions">
          <span v-if="formError" class="form-error" role="alert">{{ formError }}</span>
          <button class="btn btn-primary btn-sm" :disabled="publishing" @click="publish()">
            {{ publishing ? '发布中…' : '发布' }}
          </button>
        </div>
      </div>
    </div>

    <section class="card">
      <header class="card-head">
        <div>
          <h2>通知公告</h2>
        </div>
        <div class="filter-row">
          <button class="btn btn-sm btn-ghost" :disabled="loading" @click="load()">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>

      <ul v-if="rows.length" class="notice-list">
        <li v-for="row in rows" :key="row.id">
          <button
            type="button"
            class="notice-item"
            :class="{ 'is-unread': row.unread }"
            @click="openDetail(row)"
          >
            <span class="notice-meta">
              <b class="notice-title">{{ row.title || '—' }}</b>
              <span class="notice-kind">{{ row.kind }}</span>
              <span class="notice-time">{{ formatTime(row.time) }}</span>
            </span>
            <span class="notice-content">{{ row.content || '—' }}</span>
          </button>
        </li>
      </ul>
      <div v-else-if="loading" class="empty-state compact">
        <b>正在加载通知公告…</b>
      </div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="bell" /></span>
        <b>{{ error || '本课程还没有通知公告' }}</b>
        <button v-if="error" class="btn btn-sm" :disabled="loading" @click="load">重试</button>
      </div>
    </section>

    <Modal :open="detailOpen" title="通知公告" width="560px" @update:open="detailOpen = $event">
      <div v-if="active" class="notice-detail">
        <h3>{{ active.title || '—' }}</h3>
        <div class="notice-detail-meta">
          <span class="chip chip-mute">{{ active.kind }}</span>
          <time>{{ formatTime(active.time) }}</time>
        </div>
        <p>{{ active.content || '—' }}</p>
      </div>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import Icon from '../../../components/Icon.vue'
import Modal from '../../../components/Modal.vue'
import { useUi } from '../../../composables/ui'
import {
  listCourseAnnouncementsForAdmin,
  listMyAnnouncements,
  publishCourseAnnouncement,
} from '@/api/announcement'
import { markAsRead } from '@/api/notification'
import { loadCourseNotifications, type Notification } from '../../../data/api'
import { pickList, unwrap } from '@/utils/response'

/**
 * 课程内「通知公告」页签：公告是内容本体，站内通知是提醒。
 * 先合并公告与通知（公告通知去重），再允许点击查看全文；
 * 管理员走管理端按课程过滤的公告列表，避免课程页因无个人通知而空白。
 */
const props = defineProps<{ courseId: number | string; role: 'teacher' | 'student' | 'admin' }>()

const { toast } = useUi()

const rows = ref<Notification[]>([])
const loading = ref(false)
const error = ref('')
const composerOpen = ref(false)
const publishing = ref(false)
const formError = ref('')
const form = reactive({ title: '', content: '' })
const active = ref<Notification | null>(null)
const detailOpen = ref(false)
const markingId = ref<Notification['id'] | null>(null)

interface AnnouncementRecord {
  id?: number | string
  courseId?: number | string
  title?: string
  content?: string
  createdAt?: string
}

function formatTime(value?: string | null): string {
  return value ? String(value).slice(0, 16).replace('T', ' ') : '—'
}

function errMsg(e: unknown, fallback: string): string {
  const resp = (e as { response?: { data?: { message?: string } } })?.response
  return resp?.data?.message || fallback
}

function announcementRows(list: AnnouncementRecord[]): Notification[] {
  return list
    .filter((item) => item.id != null)
    .map((item) => ({
      id: `announcement-${item.id}`,
      announcementId: Number(item.id),
      title: String(item.title ?? '课程公告'),
      content: String(item.content ?? ''),
      time: String(item.createdAt ?? ''),
      unread: false,
      kind: '课程公告',
      type: 'ANNOUNCEMENT',
      category: 'ANNOUNCEMENT',
      actionUrl: '',
    }))
}

async function loadAnnouncements(): Promise<Notification[]> {
  if (props.role === 'admin') {
    const payload = unwrap<unknown>(await listCourseAnnouncementsForAdmin(props.courseId))
    return announcementRows(pickList(payload) as unknown as AnnouncementRecord[])
  }
  const payload = unwrap<unknown>(await listMyAnnouncements({ page: 1, size: 100 }))
  return announcementRows(
    (pickList(payload) as unknown as AnnouncementRecord[]).filter(
      (item) => String(item.courseId) === String(props.courseId),
    ),
  )
}

function mergeRows(notifications: Notification[], announcements: Notification[]): Notification[] {
  const coveredIds = new Set(
    notifications.map((row) => row.announcementId).filter((id): id is number => id != null),
  )
  const coveredKeys = new Set(
    notifications
      .filter((row) => row.type === 'ANNOUNCEMENT')
      .map((row) => `${row.title}|${row.time}|${row.content}`),
  )
  const extra = announcements.filter(
    (row) =>
      (row.announcementId == null || !coveredIds.has(row.announcementId)) &&
      !coveredKeys.has(`${row.title}|${row.time}|${row.content}`),
  )
  return [...notifications, ...extra].sort((a, b) => String(b.time).localeCompare(String(a.time)))
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [notificationResult, announcementResult] = await Promise.allSettled([
      loadCourseNotifications(props.courseId, true),
      loadAnnouncements(),
    ])
    if (notificationResult.status === 'rejected' && announcementResult.status === 'rejected') {
      throw notificationResult.reason
    }
    const notifications = notificationResult.status === 'fulfilled' ? notificationResult.value : []
    const announcements = announcementResult.status === 'fulfilled' ? announcementResult.value : []
    rows.value = mergeRows(notifications, announcements)
  } catch (e) {
    rows.value = []
    error.value = errMsg(e, '通知公告加载失败')
  } finally {
    loading.value = false
  }
}

async function openDetail(row: Notification) {
  active.value = row
  detailOpen.value = true
  if (!row.unread || typeof row.id !== 'number' || markingId.value !== null) return
  markingId.value = row.id
  try {
    await markAsRead(row.id)
    row.unread = false
  } catch {
    toast('标记已读失败，请重试', 'danger')
  } finally {
    markingId.value = null
  }
}

async function publish() {
  if (publishing.value) return
  formError.value = ''
  if (!form.title.trim()) {
    formError.value = '标题不能为空'
    return
  }
  if (!form.content.trim()) {
    formError.value = '正文不能为空'
    return
  }
  publishing.value = true
  try {
    await publishCourseAnnouncement(props.courseId, {
      title: form.title.trim(),
      content: form.content.trim(),
    })
    form.title = ''
    form.content = ''
    toast('课程通知已发布', 'ok')
    await load()
  } catch (e) {
    toast(errMsg(e, '发布失败，请稍后重试'), 'danger')
  } finally {
    publishing.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.notices-tab {
  display: grid;
  gap: 12px;
}
.course-tab-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}
.composer-body {
  display: grid;
  gap: 8px;
}
.content-input {
  resize: vertical;
}
.composer-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}
.form-error {
  color: var(--danger, #dc2626);
  font-size: 12px;
}
.notice-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}
.notice-item {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  display: grid;
  gap: 4px;
}
.notice-item:hover {
  border-color: var(--line-strong);
}
.notice-item.is-unread {
  border-color: var(--line-strong);
  background: var(--surface-2);
}
.notice-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.notice-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}
.notice-kind {
  flex: none;
  color: var(--text-3);
  font-size: 12px;
}
.notice-time {
  flex: none;
  margin-left: auto;
  color: var(--text-3);
  font-size: 12px;
}
.notice-content {
  margin: 0;
  color: var(--text-2);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.notice-detail {
  display: grid;
  gap: 12px;
}
.notice-detail h3 {
  margin: 0;
  font-size: 15px;
}
.notice-detail-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--ink-2);
  font-size: 11px;
}
.notice-detail p {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>
