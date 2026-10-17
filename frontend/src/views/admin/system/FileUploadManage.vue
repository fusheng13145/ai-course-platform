<template>
  <div class="fade-in file-upload-manage">
    <PageHeader
      title="上传会话监控"
      subtitle="查看最近 100 条上传会话的状态与磁盘分片进度，支持合并已齐片的会话或取消仍在进行的会话"
      icon="upload"
      icon-bg="accent"
    >
      <el-button type="primary" :loading="loading" @click="refreshList">
        <el-icon><Refresh /></el-icon>刷新列表
      </el-button>
    </PageHeader>

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
            :data="pagedSessions"
            style="width: 100%"
            :empty-text="emptyDescription()"
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
            <el-table-column label="操作" width="240" align="center">
              <template #default="{ row }">
                <template v-if="row.status === 'UPLOADING'">
                  <el-button
                    size="small"
                    type="warning"
                    :loading="mergingId === row.id"
                    @click="handleMerge(row)"
                  >
                    <el-icon><Connection /></el-icon>合并
                  </el-button>
                  <el-button
                    size="small"
                    type="danger"
                    @click="handleCancel(row)"
                  >
                    <el-icon><Close /></el-icon>取消
                  </el-button>
                </template>
                <el-button v-else size="small" @click="viewProgress(row)">查看进度</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <EmptyState :title="emptyDescription()" icon="upload" />
            </template>
          </el-table>
          <!-- D2-5：上传会话历史无界（listSessions 全量返回），客户端分页仅超一页时出现 -->
          <div v-if="showPager" class="pager-wrap">
            <el-pagination
              small
              layout="total, prev, pager, next"
              :total="total"
              :page-size="pageSize"
              :current-page="page"
              @current-change="onPageChange"
            />
          </div>
        </div>
      </div>
    </section>

    <!-- 进度弹窗 -->
    <el-dialog
      ref="dialogRef"
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
        <p v-if="progressMissing.length" class="progress-missing">
          缺失分片序号：{{ progressMissing.join('、') }}（补齐后即可合并）
        </p>
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
            每 5 秒刷新一次；进度与缺失分片取自服务端磁盘上的实际分片文件
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
import { useDialogFocus } from '@/composables/useDialogFocus'
import { ElMessage } from 'element-plus'
import { Refresh, Document, Close, Connection } from '@element-plus/icons-vue'
import uploadApi from '@/api/fileUpload'
import { useListFeedback } from '@/composables/useListFeedback'
import { useClientPagination } from '@/composables/useClientPagination'
import { confirmDanger } from '@/utils/dangerConfirm'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

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
  missingIndexes?: number[] | null
}

const sessions = ref<UploadSession[]>([])
// D2-5：上传会话历史无界（listSessions 全量返回），渲染层客户端分页（每页 20）
const {
  page,
  pageSize,
  total,
  paged: pagedSessions,
  showPager,
  onPageChange,
} = useClientPagination(() => sessions.value)
const sessionTotal = computed(() => sessions.value.length)
const uploadingCount = computed(() => sessions.value.filter((s) => s.status === 'UPLOADING').length)
const completedCount = computed(
  () => sessions.value.filter((s) => s.status === 'MERGED' || s.status === 'COMPLETED').length,
)
const progressVisible = ref(false)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
const dialogRef = ref(null)
useDialogFocus(dialogRef, progressVisible)
const currentSession = ref<UploadSession | null>(null)
// 列表失败反馈统一原语（D2-1）：loading + 错误态 banner + 双态空文案
const { loading, listError, run, emptyDescription } = useListFeedback({
  failMessage: '上传会话加载失败，请稍后刷新；已保留上次显示的数据。',
  toastMessage: '上传会话加载失败',
  emptyText: '暂无上传会话',
  errorEmptyText: '无法确认会话数据，请查看上方提示后重试',
})
const progressError = ref('')
const progressMissing = ref<number[]>([])
const mergingId = ref<number | null>(null)
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
  await run(async () => {
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
  })
}

async function handleMerge(row: UploadSession) {
  mergingId.value = row.id
  try {
    await uploadApi.mergeChunks(row.id)
    ElMessage.success(`会话 ${row.id} 的分片已合并`)
    const activeSession = currentSession.value
    if (activeSession?.id === row.id) {
      currentSession.value = { ...activeSession, status: 'COMPLETED', progress: 100 }
      progressMissing.value = []
      stopProgressPolling()
    }
    await refreshList()
  } catch {
    // 失败原因（如「缺少分片：1、3」）已由 request.ts 响应拦截器统一提示，
    // 此处只补动作口径；不可拼接 err.message——HTTP 错误路径下它是英文的
    // axios 文案（"Request failed with status code 400"），会造成双重且出海的提示。
    ElMessage.error('合并失败，请稍后重试')
  } finally {
    mergingId.value = null
  }
}

async function handleCancel(row: UploadSession) {
  try {
    // D2-2：明确影响范围（分片进度不再继续、需重新上传）与不可逆性
    const ok = await confirmDanger({
      verb: '取消',
      target: '上传会话',
      impact: '已上传的分片进度将不再继续，需重新开始上传',
      irreversible: true,
    })
    if (!ok) return
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
    progressMissing.value = Array.isArray(data.missingIndexes) ? data.missingIndexes : []

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
  progressMissing.value = []
  lastProgressUpdatedAt.value = ''
  progressVisible.value = true
  await refreshProgress()
  startProgressPolling()
}

onMounted(refreshList)
onUnmounted(stopProgressPolling)
</script>

<style scoped>
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
/* D2-5：分页器右对齐 */
.pager-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
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
.progress-missing {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--ac-warning);
}
.progress-hint {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 6px;
}
</style>
