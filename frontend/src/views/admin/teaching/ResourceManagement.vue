<template>
  <div class="fade-in resource-management">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">校本资源库</h2>
        <p class="page-desc">按课程管理教学资源，支持上传、检索、授权与下载统计</p>
      </div>
    </div>

    <!-- 统计概览 -->
    <div class="stats-row">
      <div class="stat-mini-card">
        <span class="label">资源总数</span>
        <span class="value">{{ resStats.total }}</span>
      </div>
      <div class="stat-mini-card teacher">
        <span class="label">公开资源</span>
        <span class="value">{{ resStats.publicCount }}</span>
      </div>
      <div class="stat-mini-card student">
        <span class="label">文档类</span>
        <span class="value">{{ resStats.docCount }}</span>
      </div>
      <div class="stat-mini-card admin">
        <span class="label">音视频</span>
        <span class="value">{{ resStats.mediaCount }}</span>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="content-card filter-card">
      <div class="filter-row">
        <el-select
          v-model="selectedCourseId"
          placeholder="选择课程"
          filterable
          style="width: 240px"
          @change="loadResources"
        >
          <el-option v-for="c in courseList" :key="c.id" :label="c.name || c.title" :value="c.id" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索资源名称"
          clearable
          style="width: 200px"
          @keyup.enter="onSearch"
        />
        <el-select v-model="typeFilter" placeholder="类型" clearable style="width: 130px">
          <el-option label="文档" value="doc" />
          <el-option label="图片" value="image" />
          <el-option label="音视频" value="media" />
          <el-option label="其他" value="other" />
        </el-select>
        <el-button type="primary" @click="onSearch"
          ><el-icon><Search /></el-icon>查询</el-button
        >
        <div class="spacer"></div>
        <el-button type="primary" :disabled="!selectedCourseId" @click="openUpload">
          <el-icon><Upload /></el-icon>上传资源
        </el-button>
      </div>
    </div>

    <!-- 资源表格 -->
    <div class="content-card">
      <el-table v-loading="loading" :data="filteredResources" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="fileName" label="资源名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="res-name">
              <el-icon class="res-icon"><component :is="fileIcon(row)" /></el-icon>
              {{ row.fileName || row.name || '未命名' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          prop="fileType"
          label="类型"
          width="120"
          align="center"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ typeTag(row.fileType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '—' }}</template>
        </el-table-column>
        <el-table-column label="可见性" width="130" align="center" class-name="tag-col">
          <template #default="{ row }">
            <el-switch
              :model-value="row.visibleToAll !== false"
              active-text="公开"
              inactive-text="私有"
              inline-prompt
              @change="(val) => toggleVisibility(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="uploadTime" label="上传时间" width="170">
          <template #default="{ row }">{{ formatTime(row.uploadTime || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <div class="action-row">
              <button class="act-btn act-btn--edit" @click="downloadRes(row)">下载</button>
              <button class="act-btn--del" @click="removeRes(row)">删除</button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="selectedCourseId ? '该课程暂无资源' : '请先选择课程'" />
        </template>
      </el-table>
    </div>

    <!-- 上传弹窗 -->
    <el-dialog v-model="showUpload" title="上传资源" width="520px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="所属课程">
          <el-input :model-value="currentCourseName" disabled />
        </el-form-item>
        <el-form-item label="资源描述">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="2"
            placeholder="选填，简要描述资源内容"
          />
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="onFileChange"
            :on-exceed="onExceed"
            drag
            style="width: 100%"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="upload-tip">支持 PDF/Word/Excel/图片/音视频，单文件 ≤ 50MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUpload = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="doUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search,
  Upload,
  UploadFilled,
  Document,
  Picture,
  VideoPlay,
  Files,
} from '@element-plus/icons-vue'
import resourceApi from '@/api/resource'
import courseApi from '@/api/course'

const loading = ref(false)
const courseList = ref([])
const selectedCourseId = ref(null)
const resources = ref([])
const keyword = ref('')
const typeFilter = ref('')

const currentCourseName = computed(() => {
  const c = courseList.value.find((x) => x.id === selectedCourseId.value)
  return c ? c.name || c.title : ''
})

function classifyType(fileType) {
  const t = (fileType || '').toLowerCase()
  if (/pdf|word|doc|excel|xls|ppt|txt|application/.test(t)) return 'doc'
  if (/image|jpg|jpeg|png|gif|bmp/.test(t)) return 'image'
  if (/video|audio|mp4|mp3|mpeg|avi/.test(t)) return 'media'
  return 'other'
}
function typeTag(fileType) {
  return { doc: '文档', image: '图片', media: '音视频', other: '其他' }[classifyType(fileType)]
}
function fileIcon(row) {
  const t = classifyType(row.fileType)
  return t === 'image' ? Picture : t === 'media' ? VideoPlay : t === 'doc' ? Document : Files
}

const resStats = computed(() => {
  const list = resources.value
  return {
    total: list.length,
    publicCount: list.filter((r) => r.visibleToAll !== false).length,
    docCount: list.filter((r) => classifyType(r.fileType) === 'doc').length,
    mediaCount: list.filter((r) => classifyType(r.fileType) === 'media').length,
  }
})

const filteredResources = computed(() => {
  let list = resources.value
  if (typeFilter.value) list = list.filter((r) => classifyType(r.fileType) === typeFilter.value)
  const q = keyword.value.trim().toLowerCase()
  if (q)
    list = list.filter(
      (r) =>
        (r.fileName || r.name || '').toLowerCase().includes(q) ||
        (r.description || '').toLowerCase().includes(q),
    )
  return list
})

function formatTime(t) {
  if (!t) return '—'
  try {
    const d = new Date(t)
    if (isNaN(d.getTime())) return t
    const p = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
  } catch {
    return t
  }
}

async function loadCourses() {
  try {
    const res = await courseApi.getCourseList({ size: 500 })
    const raw = res.data?.records || res.data?.list || res.data || res
    courseList.value = Array.isArray(raw) ? raw : []
    if (courseList.value.length && !selectedCourseId.value) {
      selectedCourseId.value = courseList.value[0].id
      await loadResources()
    }
  } catch (e) {
    logger.error('加载课程失败:', e)
    courseList.value = []
  }
}

async function loadResources() {
  if (!selectedCourseId.value) return
  loading.value = true
  try {
    const res = await resourceApi.getResourceList(selectedCourseId.value)
    const raw = res.data?.records || res.data?.list || res.data || res
    resources.value = Array.isArray(raw) ? raw : []
  } catch (e) {
    logger.error('加载资源失败:', e)
    resources.value = []
    ElMessage.error('加载资源失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  /* 前端过滤即可，computed 已处理 */
}

async function toggleVisibility(row, val) {
  try {
    await resourceApi.toggleVisibility(row.id, { visibleToAll: val })
    row.visibleToAll = val
    ElMessage.success(val ? '已设为公开' : '已设为私有')
  } catch (e) {
    ElMessage.error('设置失败')
  }
}

async function downloadRes(row) {
  try {
    const res = await resourceApi.downloadResource(row.id)
    const blob = new Blob([res.data || res])
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName || row.name || `resource-${row.id}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    logger.error(e)
    ElMessage.error('下载失败')
  }
}

function removeRes(row) {
  ElMessageBox.confirm(`确定删除资源「${row.fileName || row.name}」？`, '删除确认', {
    type: 'warning',
  })
    .then(async () => {
      try {
        await resourceApi.deleteResource(row.id)
        ElMessage.success('删除成功')
        await loadResources()
      } catch (e) {
        ElMessage.error('删除失败')
      }
    })
    .catch(() => {})
}

/* 上传 */
const showUpload = ref(false)
const uploading = ref(false)
const uploadRef = ref(null)
const uploadForm = reactive({ description: '' })
const selectedFile = ref(null)

function openUpload() {
  uploadForm.description = ''
  selectedFile.value = null
  showUpload.value = true
  uploadRef.value?.clearFiles()
}
function onFileChange(file) {
  selectedFile.value = file.raw
}
function onExceed(files) {
  uploadRef.value?.clearFiles()
  uploadRef.value?.handleStart(files[0])
  selectedFile.value = files[0].raw || files[0]
}

async function doUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  if (selectedFile.value.size > 50 * 1024 * 1024) {
    ElMessage.error('文件超过 50MB 限制')
    return
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', selectedFile.value)
    fd.append('courseId', selectedCourseId.value)
    fd.append('description', uploadForm.description || '')
    await resourceApi.uploadResource(fd)
    ElMessage.success('上传成功')
    showUpload.value = false
    await loadResources()
  } catch (e) {
    logger.error(e)
    ElMessage.error('上传失败：' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    uploading.value = false
  }
}

onMounted(loadCourses)
</script>

<style scoped>
.resource-management {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: var(--ac-spacing-lg);
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  letter-spacing: -0.3px;
}
.page-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-top: 4px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}
.stat-mini-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: all var(--ac-transition-normal);
}
.stat-mini-card:hover {
  box-shadow: var(--ac-shadow-md);
  transform: translateY(-2px);
}
.stat-mini-card .label {
  font-size: 13px;
  color: var(--ac-text-muted);
  font-weight: 500;
}
.stat-mini-card .value {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
}
.stat-mini-card.admin .value {
  color: #7f1d1d;
}
.stat-mini-card.teacher .value {
  color: #166534;
}
.stat-mini-card.student .value {
  color: #1e40af;
}

.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px;
}
.filter-card {
  margin-bottom: var(--ac-spacing-md);
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.spacer {
  flex: 1;
}

.res-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.res-icon {
  color: var(--ac-text-muted);
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.act-btn,
.act-btn--edit,
.act-btn--del {
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
  font-weight: 500;
  background: transparent;
  white-space: nowrap;
}
.act-btn:hover,
.act-btn--edit:hover,
.act-btn--del:hover {
  transform: translateY(-1px);
}
.act-btn--edit {
  color: var(--ac-primary, #3b82f6);
}
.act-btn--edit:hover {
  background: rgba(59, 130, 246, 0.08);
}
.act-btn--del {
  color: #b05045;
}
.act-btn--del:hover {
  background: rgba(176, 80, 69, 0.08);
}

.upload-icon {
  font-size: 40px;
  color: var(--ac-text-muted);
  margin-bottom: 8px;
}
.upload-tip {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 6px;
}

:deep(.tag-col .cell) {
  display: flex;
  justify-content: center;
  align-items: center;
}

@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
