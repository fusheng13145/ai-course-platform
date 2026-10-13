<template>
  <div class="fade-in file-upload-manage">
    <PageHeader
      title="上传会话监控"
      subtitle="查看最近 100 条上传会话的状态与分片进度，支持取消仍在进行的会话"
      icon="upload"
      icon-bg="accent"
    >
      <el-button type="primary" :loading="loading" @click="refreshList">
        <el-icon><Refresh /></el-icon>刷新列表
      </el-button>
    </PageHeader>

    <el-alert
      class="upload-capability-notice"
      title="文件分片上传与合并当前未启用"
      description="本页仅管理上传会话元数据；进度反映后端已记录的分片数，不代表文件已成功保存。"
      type="warning"
      :closable="false"
      show-icon
    />

    <div class="stat-grid">
      <StatCard label="最近会话数" :value="sessionTotal" icon="list" tone="accent" />
      <StatCard label="上传中" :value="uploadingCount" icon="play" tone="warn" />
      <StatCard label="已完成" :value="completedCount" icon="check" tone="ok" />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico"
          ><svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" /></svg
        ></span>
        <div><h3 class="l1-title">上传会话列表</h3></div>
      </div>
      <div class="l1-body">
        <el-alert
          v-if="listError"
          class="list-error"
          :title="listError"
          type="error"
          :closable="false"
          show-icon
        />
        <div class="content-card">
          <el-table
            v-loading="loading"
            :data="sessions"
            style="width: 100%"
            :empty-text="listError ? '无法确认会话数据，请查看上方提示后重试' : '暂无上传会话'"
          >
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="文件名称" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="file-name"
                  ><el-icon><Document /></el-icon>
                  {{ row.fileName || row.filename || '未命名' }}</span
                >
              </template>
            </el-table-column>
            <el-table-column prop="fileSize" label="文件大小" width="120">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="进度" width="180">
              <template #default="{ row }">
                <el-progress
                  :percentage="row.progress || 0"
                  :status="row.status === 'MERGED' ? 'success' : undefined"
                />
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small" effect="plain">{{
                  statusLabel(row.status)
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="180" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'UPLOADING'"
                  size="small"
                  type="danger"
                  @click="handleCancel(row)"
                >
                  <el-icon><Close /></el-icon>取消
                </el-button>
                <el-button v-else size="small" @click="viewProgress(row)">查看进度</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty
                :description="listError ? '无法确认会话数据，请查看上方提示后重试' : '暂无上传会话'"
              >
                <template #image><div class="empty-deco">📤</div></template>
              </el-empty>
            </template>
          </el-table>
        </div>
      </div>
    </section>

    <!-- 进度弹窗 -->
    <el-dialog
      v-model="progressVisible"
      title="上传进度"
      width="520px"
      append-to-body
      @closed="stopProgressPolling"
    >
      <div v-if="currentSession" class="progress-detail">
        <p><strong>文件：</strong>{{ currentSession.fileName || '未知' }}</p>
        <p><strong>大小：</strong>{{ formatSize(currentSession.fileSize) }}</p>
        <p>
          <strong>状态：</strong>
          <el-tag :type="statusType(currentSession.status)" size="small">{{
            statusLabel(currentSession.status)
          }}</el-tag>
        </p>
        <el-progress :percentage="currentSession.progress || 0" :stroke-width="20" />
        <el-alert
          v-if="progressError"
          class="progress-error"
          :title="progressError"
          type="error"
          :closable="false"
          show-icon
        />
        <p class="progress-hint">
          <template v-if="currentSession.status === 'UPLOADING'">
            每 5 秒刷新一次；进度仅反映后端记录的分片数
          </template>
          <template v-else>
            {{ lastProgressUpdatedAt ? `最近更新：${lastProgressUpdatedAt}` : '会话已结束' }}
          </template>
        </p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Document, Close } from '@element-plus/icons-vue'
import uploadApi from '@/api/fileUpload'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

const loading = ref(false)
interface UploadSession {
  id: number
  filename?: string | null
  fileName?: string | null
  fileSize?: number | null
  totalChunks?: number | null
  uploadedChunks?: number | null
  progress: number
  status: string
  createdAt?: string | null
}

interface UploadProgress {
  uploaded?: number | null
  total?: number | null
  percentage?: string | null
  status?: string | null
}

const sessions = ref<UploadSession[]>([])
const sessionTotal = computed(() => sessions.value.length)
const uploadingCount = computed(() => sessions.value.filter((s) => s.status === 'UPLOADING').length)
const completedCount = computed(
  () => sessions.value.filter((s) => s.status === 'MERGED' || s.status === 'COMPLETED').length,
)
const progressVisible = ref(false)
const currentSession = ref<UploadSession | null>(null)
const listError = ref('')
const progressError = ref('')
const lastProgressUpdatedAt = ref('')
const progressRefreshing = ref(false)
const progressTimer = ref<ReturnType<typeof setInterval> | null>(null)

function statusType(s?: string) {
  if (s === 'MERGED' || s === 'COMPLETED') return 'success'
  if (s === 'UPLOADING') return 'warning'
  if (s === 'CANCELLED' || s === 'FAILED') return 'danger'
  return 'info'
}
function statusLabel(s?: string) {
  const map = {
    UPLOADING: '上传中',
    MERGED: '已合并',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    FAILED: '失败',
    PENDING: '待上传',
  }
  return map[s] || s || '未知状态'
}
function formatSize(bytes?: number | null) {
  if (bytes == null || !Number.isFinite(bytes) || bytes < 0) return '--'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}

async function refreshList() {
  loading.value = true
  listError.value = ''
  try {
    const res = await uploadApi.listSessions()
    const payload = res?.data
    const list = Array.isArray(payload)
      ? payload
      : Array.isArray(payload?.records)
        ? payload.records
        : Array.isArray(payload?.list)
          ? payload.list
          : null
    if (!list) throw new Error('上传会话响应格式无效')
    sessions.value = list.map((s) => ({
      ...s,
      fileName: s.filename || s.fileName,
      // 进度由已上传分片数/总分片数推导（实体无 progress 字段）
      progress: s.totalChunks > 0 ? Math.round(((s.uploadedChunks || 0) / s.totalChunks) * 100) : 0,
    }))
  } catch {
    listError.value = '上传会话加载失败，请稍后刷新；已保留上次显示的数据。'
    ElMessage.error('上传会话加载失败')
  } finally {
    loading.value = false
  }
}

async function handleCancel(row: UploadSession) {
  try {
    await ElMessageBox.confirm('确定取消该上传会话？已记录的分片进度将不再继续。', '取消确认', {
      type: 'warning',
    })
  } catch {
    return
  }

  try {
    await uploadApi.cancelUpload(row.id)
    const activeSession = currentSession.value
    if (activeSession?.id === row.id) {
      currentSession.value = { ...activeSession, status: 'CANCELLED' }
      stopProgressPolling()
    }
    ElMessage.success('会话已取消')
    await refreshList()
  } catch {
    ElMessage.error('取消失败，请稍后重试')
  }
}

function stopProgressPolling() {
  if (progressTimer.value) clearInterval(progressTimer.value)
  progressTimer.value = null
}

function startProgressPolling() {
  stopProgressPolling()
  if (currentSession.value?.status !== 'UPLOADING') return
  progressTimer.value = setInterval(() => {
    if (currentSession.value?.status !== 'UPLOADING') {
      stopProgressPolling()
      return
    }
    void refreshProgress(true)
  }, 5000)
}

async function refreshProgress(silent = false) {
  const session = currentSession.value
  if (!session || progressRefreshing.value) return

  progressRefreshing.value = true
  progressError.value = ''
  try {
    const res = await uploadApi.getProgress(session.id)
    const data = (res?.data || {}) as UploadProgress
    if (currentSession.value?.id !== session.id) return

    const total = Number(data.total ?? session.totalChunks ?? 0)
    const uploaded = Number(data.uploaded ?? session.uploadedChunks ?? 0)
    const progress =
      total > 0 && Number.isFinite(uploaded)
        ? Math.min(100, Math.max(0, Math.round((uploaded / total) * 100)))
        : session.progress
    const updated: UploadSession = {
      ...session,
      progress,
      status: data.status || session.status,
      totalChunks: total || session.totalChunks,
      uploadedChunks: Number.isFinite(uploaded) ? uploaded : session.uploadedChunks,
    }
    currentSession.value = updated
    sessions.value = sessions.value.map((item) => (item.id === updated.id ? updated : item))
    lastProgressUpdatedAt.value = new Date().toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })

    if (updated.status !== 'UPLOADING') stopProgressPolling()
  } catch {
    progressError.value = '无法获取最新进度，系统会继续重试。'
    if (!silent) ElMessage.error('进度加载失败，请稍后重试')
  } finally {
    progressRefreshing.value = false
  }
}

async function viewProgress(row: UploadSession) {
  stopProgressPolling()
  currentSession.value = row
  progressError.value = ''
  lastProgressUpdatedAt.value = ''
  progressVisible.value = true
  await refreshProgress()
  startProgressPolling()
}

onMounted(refreshList)
onUnmounted(stopProgressPolling)
</script>

<style scoped>
.upload-capability-notice {
  margin-bottom: var(--ac-spacing-md);
}
.list-error {
  margin-bottom: var(--ac-spacing-md);
}
.progress-error {
  margin-top: var(--ac-spacing-md);
}
.file-upload-manage {
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
.file-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.progress-detail p {
  margin: 8px 0;
}
.progress-hint {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 6px;
}

/* 空状态装饰 */
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
</style>
