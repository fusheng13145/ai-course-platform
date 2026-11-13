<template>
  <div class="notices-tab">
    <div class="course-tab-bar">
      <span class="tab-hint">课程公告按受众扇出为站内通知，只对本课程成员可见。</span>
      <button
        v-if="role === 'teacher'"
        class="btn btn-primary btn-sm"
        @click="composerOpen = !composerOpen"
      >
        {{ composerOpen ? '收起发布框' : '发布课程通知' }}
      </button>
    </div>

    <div v-if="role === 'teacher' && composerOpen" class="card composer">
      <header class="card-head">
        <div>
          <p class="card-kicker">NOTICES / PUBLISH</p>
          <h2>发布课程通知</h2>
        </div>
      </header>
      <div class="composer-body">
        <input
          v-model="form.title"
          class="input"
          placeholder="标题（必填，≤200 字）"
          maxlength="200"
          aria-label="通知标题"
        />
        <textarea
          v-model="form.content"
          class="input content-input"
          rows="4"
          placeholder="正文（必填）"
          aria-label="通知正文"
        ></textarea>
        <div class="composer-actions">
          <span v-if="formError" class="form-error" role="alert">{{ formError }}</span>
          <button class="btn btn-primary btn-sm" :disabled="publishing" @click="publish()">
            {{ publishing ? '发布中…' : '发布并扇出通知' }}
          </button>
        </div>
      </div>
      <p class="hint">
        发布即写入 announcements
        表，并扇出到全部选课学生与主讲教师的站内信（不入教学班/行政班维度）。
      </p>
    </div>

    <section class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">NOTICES / COURSE</p>
          <h2>
            课程通知
            <span class="chip chip-mute">{{ rows.length }} 条</span>
          </h2>
        </div>
        <div class="filter-row">
          <button class="btn btn-sm btn-ghost" :disabled="loading" @click="load()">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>

      <ul v-if="rows.length" class="notice-list">
        <li v-for="row in rows" :key="row.id" class="notice-item">
          <div class="notice-meta">
            <b class="notice-title">{{ row.title || '—' }}</b>
            <span class="notice-time">{{ formatTime(row.createdAt) }}</span>
          </div>
          <p class="notice-content">{{ row.content || '（无正文）' }}</p>
        </li>
      </ul>
      <div v-else-if="loading" class="empty-state compact">
        <b>正在加载课程通知…</b>
      </div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="bell" /></span>
        <b>{{ error ? '课程通知加载失败' : '本课程还没有通知' }}</b>
        <p>{{ error || '教师发布课程通知后，这里与站内信会同步出现。' }}</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import Icon from '../../../components/Icon.vue'
import { useUi } from '../../../composables/ui'
import { listMyAnnouncements, publishCourseAnnouncement } from '@/api/announcement'
import { pickList, unwrap } from '@/utils/response'

/**
 * 课程内「通知」页签（2026-11-07 P2 批次：闭环 PRD §3.1「课程通知＝待扩展」）。
 * 教师侧：发布框（courseId 维度公告 → 扇出站内信）；学生侧：只读列表。
 * 数据源复用 WP8 公告链路，不新增后端端点；列表取「与我相关」分页后按课程过滤。
 */
const props = defineProps<{ courseId: number | string; role: 'teacher' | 'student' | 'admin' }>()

const { toast } = useUi()

interface NoticeRow {
  id?: number
  courseId?: number | string | null
  title?: string | null
  content?: string | null
  publisherName?: string | null
  createdAt?: string | null
}

const rows = ref<NoticeRow[]>([])
const loading = ref(false)
const error = ref('')
const composerOpen = ref(false)
const publishing = ref(false)
const formError = ref('')
const form = reactive({ title: '', content: '' })

function formatTime(value?: string | null): string {
  return value ? String(value).slice(0, 16).replace('T', ' ') : '—'
}

function errMsg(e: unknown, fallback: string): string {
  const resp = (e as { response?: { data?: { message?: string } } })?.response
  return resp?.data?.message || fallback
}

/** 拉取「与我相关」的公告并按课程过滤；分页不足时继续向后翻页（最多 10 页兜底）。 */
async function load() {
  loading.value = true
  error.value = ''
  try {
    const collected: NoticeRow[] = []
    for (let page = 1; page <= 10; page += 1) {
      const payload = unwrap<Record<string, unknown>>(await listMyAnnouncements({ page, size: 50 }))
      const records = pickList(payload).map((row) => row as NoticeRow)
      collected.push(
        ...records.filter((row) => String(row.courseId ?? '') === String(props.courseId)),
      )
      const total = Number(payload?.total ?? 0)
      if (records.length === 0 || collected.length >= total) break
    }
    rows.value = collected
  } catch (e) {
    error.value = errMsg(e, '课程通知加载失败，请稍后重试')
  } finally {
    loading.value = false
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
    toast('课程通知已发布并扇出', 'ok')
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
  justify-content: space-between;
  gap: 10px;
}
.tab-hint {
  font-size: 12px;
  color: var(--text-3);
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
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  display: grid;
  gap: 4px;
}
.notice-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}
.notice-title {
  font-size: 14px;
}
.notice-time {
  font-size: 12px;
  color: var(--text-3);
}
.notice-content {
  margin: 0;
  font-size: 13px;
  color: var(--text-2);
  white-space: pre-wrap;
}
</style>
