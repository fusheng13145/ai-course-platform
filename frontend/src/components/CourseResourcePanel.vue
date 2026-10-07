import { getMonoSeries, getMonoStatus, monoScore } from '@/utils/chartTheme'
<template>
  <div class="resource-panel">
    <div class="panel-toolbar">
      <el-button type="primary" plain @click="showUploadDialog">
        <el-icon><Plus /></el-icon> 上传资源
      </el-button>
      <el-select v-model="resourceFilter" class="filter-select" placeholder="筛选类型">
        <el-option label="全部" value="" />
        <el-option label="文档" value="document" />
        <el-option label="图片" value="image" />
        <el-option label="视频" value="video" />
        <el-option label="音频" value="audio" />
      </el-select>
    </div>

    <div class="content-card">
      <el-table v-loading="loading" :data="filteredResources" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="文件名" min-width="250">
          <template #default="{ row }">
            <div class="file-name-cell">
              <el-icon :size="20" :color="getFileIconColor(row.fileType)">
                <Document v-if="isDocument(row.fileType)" />
                <Picture v-else-if="isImage(row.fileType)" />
                <VideoPlay v-else-if="isVideo(row.fileType)" />
                <Headphones v-else />
              </el-icon>
              <span class="file-name">{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="getFileTagType(row.fileType)">
              {{ getFileTypeName(row.fileType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.uploadTime) }}
          </template>
        </el-table-column>
        <el-table-column label="可见性" width="100">
          <template #default="{ row }">
            <el-tag :type="row.visibleToAll ? 'success' : 'info'" size="small">
              {{ row.visibleToAll ? '全员可见' : '仅教师' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button text type="primary" @click="downloadResource(row)">下载</el-button>
            <el-button text type="warning" @click="toggleVisibility(row)">
              {{ row.visibleToAll ? '隐藏' : '公开' }}
            </el-button>
            <el-button text type="danger" @click="deleteResource(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && filteredResources.length === 0" description="暂无资源" />
    </div>

    <el-dialog v-model="uploadDialogVisible" title="上传资源" width="500px" append-to-body>
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="文件">
          <el-upload
            class="upload-demo"
            action="/api/resources/upload"
            :data="{ courseId: courseId }"
            :multiple="true"
            :auto-upload="false"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :file-list="uploadFileList"
            accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.gif,.mp4,.mp3"
          >
            <el-button type="primary">选择文件</el-button>
          </el-upload>
          <div style="margin-top: 8px; color: var(--ink-3); font-size: 12px">
            支持 PDF/DOC/XLS/图片/视频/音频，单文件最大50MB
          </div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="3"
            placeholder="资源描述"
          />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="uploadForm.visibleToAll">
            <el-radio :value="true">全员可见</el-radio>
            <el-radio :value="false">仅教师可见</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import resourceApi from '@/api/resource'

const props = defineProps({
  courseId: { type: Number, required: true },
})

const emit = defineEmits(['resourceUpdated'])

const resources = ref([])
const loading = ref(false)
const resourceFilter = ref('')
const uploadDialogVisible = ref(false)
const uploadFileList = ref([])
const uploading = ref(false)
const uploadForm = ref({
  description: '',
  visibleToAll: true,
})

const filteredResources = computed(() => {
  if (!resourceFilter.value) return resources.value
  return resources.value.filter((r) => {
    if (resourceFilter.value === 'document') return isDocument(r.fileType)
    if (resourceFilter.value === 'image') return isImage(r.fileType)
    if (resourceFilter.value === 'video') return isVideo(r.fileType)
    if (resourceFilter.value === 'audio') return isAudio(r.fileType)
    return true
  })
})

function isDocument(type) {
  return type && (type.includes('pdf') || type.includes('word') || type.includes('excel'))
}

function isImage(type) {
  return type && type.includes('image')
}

function isVideo(type) {
  return type && type.includes('video')
}

function isAudio(type) {
  return type && type.includes('audio')
}

function getFileIconColor(type) {
  if (isDocument(type)) return getMonoSeries()[0]
  if (isImage(type)) return getMonoSeries()[2]
  if (isVideo(type)) return getMonoSeries()[1]
  if (isAudio(type)) return getMonoSeries()[5]
  return getMonoSeries()[3]
}

function getFileTagType(type) {
  if (isDocument(type)) return 'primary'
  if (isImage(type)) return 'success'
  if (isVideo(type)) return 'warning'
  if (isAudio(type)) return 'danger'
  return 'info'
}

function getFileTypeName(type) {
  if (isDocument(type)) return '文档'
  if (isImage(type)) return '图片'
  if (isVideo(type)) return '视频'
  if (isAudio(type)) return '音频'
  return '其他'
}

function formatFileSize(size) {
  if (!size) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatDateTime(date) {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

async function loadResources() {
  loading.value = true
  try {
    const res = await resourceApi.getResourceList(props.courseId)
    resources.value = res.data || res || []
  } catch (e) {
    logger.error('加载资源失败:', e)
  } finally {
    loading.value = false
  }
}

function showUploadDialog() {
  uploadFileList.value = []
  uploadForm.value = { description: '', visibleToAll: true }
  uploadDialogVisible.value = true
}

function handleFileChange(file, list) {
  uploadFileList.value = list
}

function handleFileRemove(file, list) {
  uploadFileList.value = list
}

async function submitUpload() {
  if (uploadFileList.value.length === 0) {
    ElMessage.warning('请选择文件')
    return
  }
  // 缺陷修复（P0-5）：courseId 为空时本地先拒发并提示，
  // 不再把一个 undefined 的 courseId 送到后端换 400。
  if (props.courseId === null || props.courseId === undefined || props.courseId === '') {
    ElMessage.warning('课程信息缺失，无法上传资源')
    return
  }
  uploading.value = true
  try {
    for (const file of uploadFileList.value) {
      // 交给 api/resource.ts 的 buildUploadForm 统一转成合法 multipart FormData。
      // 原实现直接传普通对象，axios 在 multipart 头下无法序列化文件部件 → 必然 400。
      await resourceApi.uploadResource({
        file: file.raw,
        courseId: props.courseId,
        description: uploadForm.value.description,
      })
    }
    ElMessage.success('上传成功')
    uploadDialogVisible.value = false
    await loadResources()
    emit('resourceUpdated')
  } catch (e) {
    // 归一化校验抛出的可读消息直接展示（如「请选择要上传的文件」）
    ElMessage.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

function downloadResource(row) {
  window.open(`/api/resources/download/${row.id}`, '_blank')
}

async function toggleVisibility(row) {
  try {
    // 契约修正：api/resource.ts 的签名是 toggleVisibility(id, visible)，
    // 内部以 query 参数 visibleToAll 上报。原实现传了整个对象，
    // 会被序列化成 `visibleToAll=[object Object]`，后端布尔解析必然失败。
    await resourceApi.toggleVisibility(row.id, !row.visibleToAll)
    row.visibleToAll = !row.visibleToAll
    ElMessage.success('设置成功')
  } catch {
    ElMessage.error('操作失败')
  }
}

async function deleteResource(row) {
  try {
    await ElMessageBox.confirm('确定删除该资源？', '提示', { type: 'warning' })
    await resourceApi.deleteResource(row.id)
    resources.value = resources.value.filter((r) => r.id !== row.id)
    ElMessage.success('删除成功')
    emit('resourceUpdated')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

watch(
  () => props.courseId,
  () => {
    loadResources()
  },
  { immediate: true },
)
</script>

<style scoped>
.resource-panel {
  padding: 0;
}

.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.filter-select {
  width: 150px;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-name {
  font-size: 14px;
  color: var(--ac-text-primary);
}

.upload-demo {
  width: 100%;
}
</style>
