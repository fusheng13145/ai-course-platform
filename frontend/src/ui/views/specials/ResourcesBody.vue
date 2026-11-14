<template>
  <section class="resources-body">
    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>我的资源库</h2>
        </div>
        <div class="filter-row class-tools">
          <select
            v-model="courseId"
            class="input filter-select"
            aria-label="选择课程"
            @change="loadResources"
          >
            <option :value="null" disabled>请选择课程</option>
            <option v-for="course in courses" :key="String(course.id)" :value="course.id">
              {{ course.name }}
            </option>
          </select>
          <label class="btn btn-sm btn-primary upload-btn">
            {{ uploading ? '上传中…' : '上传资源' }}
            <input
              type="file"
              class="sr-file"
              :disabled="uploading || !courseId"
              @change="onUpload"
            />
          </label>
        </div>
      </header>

      <div v-if="rows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>资源名称</th>
              <th class="w-96">大小</th>
              <th class="w-80">可见性</th>
              <th class="w-96">下载次数</th>
              <th class="w-140 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="String(row.id)">
              <td>
                <b>{{ row.name || '—' }}</b
                ><small class="cell-sub">{{ row.fileType || '' }}</small>
              </td>
              <td>{{ sizeLabel(row.fileSize) }}</td>
              <td>
                <span class="chip" :class="row.isVisibleToAll ? 'chip-ok' : 'chip-mute'">
                  {{ row.isVisibleToAll ? '全员可见' : '受限' }}
                </span>
              </td>
              <td>{{ row.downloadCount ?? 0 }}</td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="download(row)">下载</button>
                  <button class="text-btn" @click="toggleVis(row)">
                    {{ row.isVisibleToAll ? '设受限' : '设全员' }}
                  </button>
                  <button class="text-btn danger" @click="askDelete(row)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载资源…</b></div>
      <div v-else-if="courseId" class="empty-state compact">
        <b>该课程暂无资源</b>
        <p>用右上角「上传资源」添加课件。</p>
      </div>
      <div v-else class="empty-state compact">
        <b>请先选择课程</b>
        <p>资源按课程组织，先在右上角选课。</p>
      </div>
      <p class="hint">
        课程空间 → 章节与资源 内的上传走同一后端；本页是跨课程汇总视图。受限资源直链受对象级 ACL
        保护（WP7）。
      </p>
    </section>

    <Modal v-model:open="deleteOpen" title="删除资源" width="460px" :close-on-backdrop="false">
      <p class="modal-text">
        确定删除资源 <b>{{ deleteTarget?.name || '该资源' }}</b> 吗？
      </p>
      <p class="modal-hint">删除后磁盘文件与记录一并移除，不可恢复；学生端下载入口随之失效。</p>
      <p v-if="deleteError" class="form-error" role="alert">{{ deleteError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="deleting" @click="deleteOpen = false">取消</button>
        <button class="btn btn-danger" :disabled="deleting" @click="confirmDelete">
          {{ deleting ? '删除中…' : '确定删除' }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Modal from '../../components/Modal.vue'
import { useUi } from '../../composables/ui'
import { getCourseCatalog } from '../../data/api'
import {
  deleteResource,
  downloadResource,
  getResourceList,
  toggleVisibility,
  uploadResource,
} from '@/api/resource'
import { pickList, unwrap } from '@/utils/response'

interface ResourceRow {
  id?: number | string
  name?: string
  fileType?: string
  fileSize?: number | null
  isVisibleToAll?: boolean
  downloadCount?: number | null
}

interface OptionRow {
  id?: number | string
  name?: string
}

const { toast } = useUi()
const courses = ref<OptionRow[]>([])
const courseId = ref<number | string | null>(null)
const rows = ref<ResourceRow[]>([])
const loading = ref(false)
const uploading = ref(false)
const deleteOpen = ref(false)
const deleteTarget = ref<ResourceRow | null>(null)
const deleting = ref(false)
const deleteError = ref('')

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

function sizeLabel(size: unknown): string {
  const bytes = Number(size ?? 0)
  if (!bytes) return '—'
  return bytes > 1048576 ? (bytes / 1048576).toFixed(1) + ' MB' : Math.round(bytes / 1024) + ' KB'
}

async function loadCourses() {
  try {
    const catalog = await getCourseCatalog()
    courses.value = (catalog || []).map((row) => ({ id: row.id, name: row.name }))
  } catch (error) {
    toast(errorText(error, '课程目录加载失败'), 'danger')
  }
}

async function loadResources() {
  if (!courseId.value) return
  loading.value = true
  try {
    rows.value = pickList<ResourceRow>(unwrap(await getResourceList(courseId.value)))
  } catch (error) {
    toast(errorText(error, '资源列表加载失败'), 'danger')
  } finally {
    loading.value = false
  }
}

async function onUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !courseId.value) return
  uploading.value = true
  try {
    await uploadResource({ courseId: courseId.value, file })
    toast('资源已上传', 'ok')
    await loadResources()
  } catch (error) {
    toast(errorText(error, '上传失败，请稍后重试'), 'danger')
  } finally {
    uploading.value = false
  }
}

async function download(row: ResourceRow) {
  if (row.id === undefined || row.id === null) return
  try {
    await downloadResource(row.id)
  } catch (error) {
    toast(errorText(error, '下载失败'), 'danger')
  }
}

async function toggleVis(row: ResourceRow) {
  if (row.id === undefined || row.id === null) return
  try {
    await toggleVisibility(row.id, !row.isVisibleToAll)
    row.isVisibleToAll = !row.isVisibleToAll
    toast('可见性已更新', 'ok')
  } catch (error) {
    toast(errorText(error, '可见性更新失败'), 'danger')
  }
}

function askDelete(row: ResourceRow) {
  if (row.id === undefined || row.id === null) return
  deleteTarget.value = row
  deleteError.value = ''
  deleteOpen.value = true
}

async function confirmDelete() {
  const row = deleteTarget.value
  if (!row || row.id === undefined || row.id === null) return
  deleting.value = true
  deleteError.value = ''
  try {
    await deleteResource(row.id)
    deleteOpen.value = false
    deleteTarget.value = null
    toast('资源已删除', 'ok')
    await loadResources()
  } catch (error) {
    deleteError.value = errorText(error, '删除失败，请稍后重试')
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  void loadCourses()
})
</script>

<style scoped>
.resources-body {
  display: grid;
  gap: 14px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.filter-select {
  width: 240px;
  height: 30px;
}
.upload-btn {
  position: relative;
  overflow: hidden;
}
.sr-file {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}
</style>
