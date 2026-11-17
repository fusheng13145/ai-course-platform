<template>
  <section class="resources-panel">
    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>
            课程资源 <span class="chip chip-mute">{{ total }} 条</span>
          </h2>
        </div>
        <div class="filter-row class-tools">
          <select v-model="courseId" class="input course-pick" aria-label="选择课程" @change="load">
            <option :value="null">选择课程</option>
            <option v-for="course in courses" :key="String(course.id)" :value="course.id">
              {{ course.name || course.title || `课程 #${course.id}` }}
            </option>
          </select>
          <input
            v-model="keyword"
            class="input search-input"
            type="search"
            placeholder="搜索文件名 / 描述"
            aria-label="搜索资源"
          />
          <button class="btn btn-sm" :disabled="loading || !courseId" @click="load">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>

      <div v-if="listError" class="callout danger" role="alert">
        <span>资源库</span>
        <p>{{ listError }}</p>
        <button class="btn btn-sm" :disabled="loading" @click="load">重试</button>
      </div>
      <div v-else-if="!courseId" class="empty-state compact">
        <b>请先选择课程</b>
        <p>资源库按课程读取；先选课程再查看文件与版本。</p>
      </div>
      <div v-else-if="loading && !rows.length" class="empty-state compact">
        <b>正在加载课程资源…</b>
      </div>
      <div v-else-if="!rows.length" class="empty-state compact">
        <b>该课程暂无资源</b>
        <p>教师上传课程资源后，这里会出现文件与版本入口。</p>
      </div>
      <div v-else-if="!filtered.length" class="empty-state compact">
        <b>没有匹配的资源</b>
        <p>换个关键词再试。</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-80">ID</th>
              <th>文件名</th>
              <th class="w-96">类型</th>
              <th class="w-96">章节</th>
              <th class="w-120">上传人</th>
              <th class="w-120">可见性</th>
              <th class="w-180">上传时间</th>
              <th class="w-180 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in paged" :key="String(row.id)">
              <td>{{ row.id }}</td>
              <td>
                <b>{{ row.fileName || `资源 #${row.id}` }}</b>
                <small class="cell-sub">{{ brief(row.description) }}</small>
              </td>
              <td>{{ row.fileType || '—' }}</td>
              <td>{{ row.chapterId != null ? `#${row.chapterId}` : '课程级' }}</td>
              <td>{{ row.uploaderId != null ? `#${row.uploaderId}` : '—' }}</td>
              <td>
                <span class="chip" :class="row.visibleToAll ? 'chip-ok' : 'chip-mute'">
                  {{ row.visibleToAll ? '全员可见' : '需选课/授权' }}
                </span>
              </td>
              <td>{{ formatDateTime(row.uploadTime) }}</td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="openVersions(row)">版本</button>
                  <button
                    class="text-btn"
                    :disabled="acting === String(row.id)"
                    @click="toggle(row)"
                  >
                    {{ row.visibleToAll ? '取消全员' : '设为全员' }}
                  </button>
                  <a
                    v-if="row.url"
                    class="text-btn"
                    :href="String(row.url)"
                    target="_blank"
                    rel="noopener"
                  >
                    打开
                  </a>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer v-if="showPager" class="card-foot">
        <span>第 {{ page }} 页 · 共 {{ total }} 条</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1" @click="onPageChange(page - 1)">
            上一页
          </button>
          <button
            class="btn btn-sm"
            :disabled="page >= Math.ceil(total / pageSize)"
            @click="onPageChange(page + 1)"
          >
            下一页
          </button>
        </div>
      </footer>
    </section>

    <Modal v-model:open="versions.open" title="资源版本历史" width="760px">
      <p v-if="versions.resource" class="modal-text">
        {{ versions.resource.fileName || `资源 #${versions.resource.id}` }}
      </p>
      <div v-if="versions.loading" class="modal-hint">正在加载版本…</div>
      <div v-else-if="versions.error" class="form-error" role="alert">{{ versions.error }}</div>
      <div v-else-if="versions.rows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-80">版本</th>
              <th>文件名</th>
              <th class="w-96">大小</th>
              <th>说明</th>
              <th class="w-120">状态</th>
              <th class="w-180">创建时间</th>
              <th class="w-96 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in versions.rows" :key="`v-${item.id ?? index}`">
              <td>v{{ item.versionNumber ?? '—' }}</td>
              <td>{{ item.fileName || '—' }}</td>
              <td>{{ formatBytes(item.fileSize) }}</td>
              <td>{{ brief(item.description) }}</td>
              <td>
                <span class="chip" :class="item.isActive ? 'chip-ok' : 'chip-mute'">
                  {{ item.isActive ? '当前版本' : '历史版本' }}
                </span>
              </td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
              <td>
                <button
                  v-if="!item.isActive"
                  class="text-btn"
                  :disabled="versions.rolling === String(item.id)"
                  @click="rollback(item)"
                >
                  {{ versions.rolling === String(item.id) ? '回滚中…' : '回滚' }}
                </button>
                <span v-else class="cell-sub">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="modal-hint">该资源还没有版本记录。</p>
      <template #footer>
        <button class="btn btn-ghost" @click="versions.open = false">关闭</button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import Stat from '../../components/Stat.vue'
import Modal from '../../components/Modal.vue'
import { useUi } from '../../composables/ui'
import { useClientPagination } from '@/composables/useClientPagination'
import { getAllCourses } from '@/api/course'
import resourceApi from '@/api/resource'
import resourceVersionApi from '@/api/resourceVersion'
import { pickList, unwrap } from '@/utils/response'
import type { Metric } from '../../nav'

interface CourseOption {
  id?: number | string
  name?: string
  title?: string
}

interface ResourceRow {
  id?: number | string
  fileName?: string
  fileType?: string
  url?: string
  courseId?: number | string
  chapterId?: number | string | null
  uploaderId?: number | string | null
  uploadTime?: string | null
  description?: string | null
  visibleToAll?: boolean | null
}

interface VersionRow {
  id?: number | string
  versionNumber?: number | null
  fileName?: string
  description?: string | null
  fileSize?: number | null
  isActive?: boolean | null
  createdAt?: string | null
}

const { toast } = useUi()
const courses = ref<CourseOption[]>([])
const courseId = ref<number | string | null>(null)
const rows = ref<ResourceRow[]>([])
const loading = ref(false)
const listError = ref('')
const keyword = ref('')
const acting = ref('')

const filtered = computed(() => {
  const word = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    if (!word) return true
    return [row.fileName, row.description, row.fileType]
      .map((value) => String(value ?? '').toLowerCase())
      .some((value) => value.includes(word))
  })
})

const { page, pageSize, total, paged, showPager, onPageChange, reset } =
  useClientPagination<ResourceRow>(() => filtered.value, { pageSize: 15 })
watch(keyword, () => reset())

const metrics = computed<Metric[]>(() => {
  const visible = rows.value.filter((row) => row.visibleToAll).length
  const types = new Set(rows.value.map((row) => row.fileType).filter(Boolean)).size
  const chapters = new Set(rows.value.map((row) => row.chapterId).filter((id) => id != null)).size
  return [
    { label: '课程资源', value: rows.value.length, unit: '个', note: '当前课程', icon: 'layers' },
    { label: '全员可见', value: visible, unit: '个', note: '无需授权', icon: 'users', tone: 'ok' },
    { label: '资源类型', value: types, unit: '类', note: '按扩展名去重', icon: 'file' },
    {
      label: '挂章节',
      value: chapters,
      unit: '节',
      note: '章节级资源',
      icon: 'book',
      tone: 'info',
    },
  ]
})

const versions = reactive({
  open: false,
  loading: false,
  error: '',
  resource: null as ResourceRow | null,
  rows: [] as VersionRow[],
  rolling: '',
})

function brief(text: unknown): string {
  const raw = String(text ?? '')
  return raw.length > 40 ? raw.slice(0, 40) + '…' : raw || '—'
}

function formatDateTime(value: unknown): string {
  return (
    String(value ?? '')
      .replace('T', ' ')
      .slice(0, 16) || '—'
  )
}

function formatBytes(value: unknown): string {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return '—'
  if (num < 1024) return `${num} B`
  if (num < 1024 * 1024) return `${(num / 1024).toFixed(1)} KB`
  return `${(num / 1024 / 1024).toFixed(1)} MB`
}

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

async function loadCourses() {
  try {
    const payload = await getAllCourses()
    courses.value = Array.isArray(payload) ? payload : pickList<CourseOption>(payload)
    if (!courseId.value && courses.value.length) {
      courseId.value = courses.value[0].id ?? null
    }
  } catch (error) {
    toast(errorText(error, '课程列表加载失败'), 'danger')
  }
}

async function load() {
  if (!courseId.value) {
    rows.value = []
    return
  }
  loading.value = true
  listError.value = ''
  reset()
  try {
    const payload = unwrap<unknown>(await resourceApi.getResourceList(courseId.value))
    rows.value = pickList<ResourceRow>(payload)
  } catch (error) {
    listError.value = errorText(error, '课程资源加载失败，请稍后重试')
    toast(listError.value, 'danger')
  } finally {
    loading.value = false
  }
}

async function toggle(row: ResourceRow) {
  if (row.id == null) return
  acting.value = String(row.id)
  try {
    await resourceApi.toggleVisibility(row.id, !row.visibleToAll)
    toast(row.visibleToAll ? '已取消全员可见' : '已设为全员可见', 'ok')
    await load()
  } catch (error) {
    toast(errorText(error, '可见性更新失败'), 'danger')
  } finally {
    acting.value = ''
  }
}

async function openVersions(row: ResourceRow) {
  if (row.id == null) return
  versions.open = true
  versions.loading = true
  versions.error = ''
  versions.rows = []
  versions.resource = row
  try {
    const payload = unwrap<unknown>(await resourceVersionApi.getVersionHistory(row.id))
    versions.rows = pickList<VersionRow>(payload)
  } catch (error) {
    versions.error = errorText(error, '版本历史加载失败')
  } finally {
    versions.loading = false
  }
}

async function rollback(item: VersionRow) {
  if (item.id == null) return
  versions.rolling = String(item.id)
  try {
    await resourceVersionApi.rollbackVersion(item.id)
    toast(`已回滚到 v${item.versionNumber ?? '—'}`, 'ok')
    if (versions.resource?.id != null) {
      const payload = unwrap<unknown>(
        await resourceVersionApi.getVersionHistory(versions.resource.id),
      )
      versions.rows = pickList<VersionRow>(payload)
    }
    await load()
  } catch (error) {
    toast(errorText(error, '版本回滚失败'), 'danger')
  } finally {
    versions.rolling = ''
  }
}

onMounted(async () => {
  await loadCourses()
  await load()
})
</script>

<style scoped>
.resources-panel {
  display: grid;
  gap: 14px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.course-pick {
  width: 220px;
  height: 30px;
}
.search-input {
  width: 200px;
  height: 30px;
}
</style>
