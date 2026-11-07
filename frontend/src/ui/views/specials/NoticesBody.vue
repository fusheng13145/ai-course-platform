<template>
  <section class="notices-body">
    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">ADMIN NOTICES / PUBLISH</p>
          <h2>发布公告</h2>
        </div>
      </header>
      <div class="form-grid publish-grid">
        <label class="field"
          ><span>受众类型 *</span>
          <select v-model="form.audienceType" class="input" data-testid="notice-audience">
            <option value="course">课程（全部在读学生 + 授课教师）</option>
            <option value="teachingClass">教学班（班成员 + 主讲）</option>
            <option value="adminClass">行政班（在册学生 + 班主任）</option>
          </select>
        </label>
        <label v-if="form.audienceType === 'course'" class="field"
          ><span>课程 *</span>
          <select v-model="form.courseId" class="input">
            <option :value="null" disabled>请选择课程</option>
            <option v-for="course in courses" :key="String(course.id)" :value="course.id">
              {{ course.name }}（#{{ course.id }}）
            </option>
          </select>
        </label>
        <template v-if="form.audienceType === 'teachingClass'">
          <label class="field"
            ><span>课程 *</span>
            <select v-model="form.tcCourseId" class="input">
              <option :value="null" disabled>先选课程</option>
              <option v-for="course in courses" :key="String(course.id)" :value="course.id">
                {{ course.name }}（#{{ course.id }}）
              </option>
            </select>
          </label>
          <label class="field"
            ><span>教学班 *</span>
            <select v-model="form.teachingClassId" class="input" :disabled="!form.tcCourseId">
              <option :value="null" disabled>
                {{ form.tcCourseId ? '请选择教学班' : '先选课程' }}
              </option>
              <option v-for="tc in tcOptions" :key="String(tc.id)" :value="tc.id">
                {{ tc.name }}
              </option>
            </select>
          </label>
        </template>
        <label v-if="form.audienceType === 'adminClass'" class="field"
          ><span>行政班 *</span>
          <select v-model="form.adminClassId" class="input">
            <option :value="null" disabled>请选择行政班</option>
            <option v-for="ac in adminClasses" :key="String(ac.id)" :value="ac.id">
              {{ ac.name }}（{{ ac.studentCount ?? 0 }} 人）
            </option>
          </select>
        </label>
        <label class="field span-2"
          ><span>标题 *</span>
          <input
            v-model="form.title"
            class="input"
            data-testid="notice-title"
            placeholder="公告标题"
          />
        </label>
        <label class="field span-2"
          ><span>正文 *</span>
          <textarea
            v-model="form.content"
            class="input textarea"
            data-testid="notice-content"
            rows="4"
            placeholder="公告正文；发布后按受众扇出到站内通知（去重）"
          ></textarea>
        </label>
      </div>
      <p v-if="publishError" class="form-error" role="alert">{{ publishError }}</p>
      <div class="publish-actions">
        <button
          class="btn btn-primary"
          :disabled="publishing"
          data-testid="notice-publish"
          @click="publish"
        >
          {{ publishing ? '发布中…' : '发布公告' }}
        </button>
        <span class="hint-inline">受众在发布时去重；非受众不可见（403）。</span>
      </div>
    </section>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">ADMIN NOTICES / HISTORY</p>
          <h2>
            公告记录 <span class="chip chip-mute">{{ total }} 条</span>
          </h2>
        </div>
        <div class="filter-row class-tools">
          <input
            v-model="keyword"
            class="input search-input"
            type="search"
            placeholder="搜索标题 / 正文"
            aria-label="搜索公告"
            @keyup.enter="loadAnnouncements(true)"
          />
          <select
            v-model="targetType"
            class="input filter-select"
            aria-label="按受众类型筛选"
            @change="loadAnnouncements(true)"
          >
            <option :value="''">全部类型</option>
            <option value="course">课程</option>
            <option value="teachingClass">教学班</option>
            <option value="adminClass">行政班</option>
          </select>
          <button class="btn btn-sm" :disabled="loading" @click="loadAnnouncements(true)">
            {{ loading ? '查询中…' : '查询' }}
          </button>
        </div>
      </header>
      <div v-if="rows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>标题 / 正文摘要</th>
              <th class="w-140">受众</th>
              <th class="w-140">发布人</th>
              <th class="w-140">发布时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="String(row.id)">
              <td>
                <b>{{ row.title || '—' }}</b>
                <small class="cell-sub">{{ brief(row.content) }}</small>
              </td>
              <td>{{ audienceLabel(row) }}</td>
              <td>{{ row.publisherRole || '—' }} #{{ row.publisherId ?? '—' }}</td>
              <td>{{ briefTime(row.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载公告…</b></div>
      <div v-else class="empty-state compact">
        <b>暂无公告</b>
        <p>发布后会按受众扇出到站内通知。</p>
      </div>
      <footer v-if="total > pageSize" class="card-foot">
        <span>第 {{ page }} 页 · 共 {{ total }} 条</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1 || loading" @click="turnPage(page - 1)">
            上一页
          </button>
          <button
            class="btn btn-sm"
            :disabled="page * pageSize >= total || loading"
            @click="turnPage(page + 1)"
          >
            下一页
          </button>
        </div>
      </footer>
      <p class="hint">
        消费侧在消息中心（/notifications），已读与删除走既有通知通道；本页是发布与治理视图。
      </p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useUi } from '../../composables/ui'
import {
  getAllAnnouncements,
  getColleges,
  getMajors,
  getOrgAdminClasses,
  getTeachingClassesByCourse,
  publishAnnouncement,
} from '@/api/admin'
import { getCourseCatalog } from '../../data/api'
import { pickList, unwrap } from '@/utils/response'

interface NoticeRow {
  id?: number | string
  title?: string
  content?: string
  courseId?: number | string | null
  teachingClassId?: number | string | null
  adminClassId?: number | string | null
  publisherId?: number | string | null
  publisherRole?: string
  createdAt?: string
}

interface OptionRow {
  id?: number | string
  name?: string
  studentCount?: number | null
}

const { toast } = useUi()

const rows = ref<NoticeRow[]>([])
const page = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const targetType = ref('')

const courses = ref<OptionRow[]>([])
const adminClasses = ref<OptionRow[]>([])
const tcOptions = ref<OptionRow[]>([])

const publishing = ref(false)
const publishError = ref('')
const form = reactive({
  audienceType: 'course' as 'course' | 'teachingClass' | 'adminClass',
  courseId: null as number | string | null,
  tcCourseId: null as number | string | null,
  teachingClassId: null as number | string | null,
  adminClassId: null as number | string | null,
  title: '',
  content: '',
})

watch(
  () => form.tcCourseId,
  async (courseId) => {
    form.teachingClassId = null
    tcOptions.value = []
    if (courseId) {
      const payload = unwrap(await getTeachingClassesByCourse(courseId))
      tcOptions.value = pickList<OptionRow>(payload)
    }
  },
)

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

function brief(text: unknown): string {
  const raw = String(text ?? '')
  return raw.length > 60 ? raw.slice(0, 60) + '…' : raw
}

function briefTime(text: unknown): string {
  return (
    String(text ?? '')
      .replace('T', ' ')
      .slice(0, 16) || '—'
  )
}

function audienceLabel(row: NoticeRow): string {
  if (row.courseId != null) return `课程 #${row.courseId}`
  if (row.teachingClassId != null) return `教学班 #${row.teachingClassId}`
  if (row.adminClassId != null) return `行政班 #${row.adminClassId}`
  return '—'
}

async function loadAnnouncements(resetPage = false) {
  if (resetPage) page.value = 1
  loading.value = true
  try {
    const payload = unwrap(
      await getAllAnnouncements({
        page: page.value,
        size: pageSize,
        targetType: targetType.value || undefined,
        keyword: keyword.value.trim() || undefined,
      }),
    )
    const envelope = payload as { records?: NoticeRow[]; total?: number }
    rows.value = pickList<NoticeRow>(payload)
    total.value = Number(envelope?.total ?? rows.value.length)
  } catch (error) {
    toast(errorText(error, '公告列表加载失败'), 'danger')
  } finally {
    loading.value = false
  }
}

function turnPage(next: number) {
  page.value = next
  void loadAnnouncements()
}

async function publish() {
  const title = form.title.trim()
  const content = form.content.trim()
  if (!title || !content) {
    publishError.value = '标题与正文必填'
    return
  }
  const body: Record<string, unknown> = { title, content }
  if (form.audienceType === 'course') {
    if (!form.courseId) {
      publishError.value = '请选择课程'
      return
    }
    body.courseId = form.courseId
  } else if (form.audienceType === 'teachingClass') {
    if (!form.teachingClassId) {
      publishError.value = '请选择教学班'
      return
    }
    body.teachingClassId = form.teachingClassId
  } else {
    if (!form.adminClassId) {
      publishError.value = '请选择行政班'
      return
    }
    body.adminClassId = form.adminClassId
  }
  publishing.value = true
  publishError.value = ''
  try {
    await publishAnnouncement(body)
    toast('公告已发布，已按受众扇出通知', 'ok')
    Object.assign(form, { title: '', content: '' })
    await loadAnnouncements(true)
  } catch (error) {
    publishError.value = errorText(error, '发布失败，请稍后重试')
  } finally {
    publishing.value = false
  }
}

async function loadPickers() {
  try {
    const [catalog, adminClassPayload] = await Promise.all([
      getCourseCatalog(),
      getOrgAdminClasses({}),
    ])
    courses.value = (catalog || []).map((row) => ({ id: row.id, name: row.name }))
    adminClasses.value = pickList<OptionRow>(unwrap(adminClassPayload))
    void getColleges
    void getMajors
  } catch (error) {
    toast(errorText(error, '受众选项加载失败'), 'danger')
  }
}

onMounted(() => {
  void loadAnnouncements()
  void loadPickers()
})
</script>

<style scoped>
.notices-body {
  display: grid;
  gap: 14px;
}
.publish-grid {
  padding: 12px 16px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.search-input {
  width: 200px;
  height: 30px;
}
.filter-select {
  width: 130px;
  height: 30px;
}
.publish-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px 14px;
}
</style>
