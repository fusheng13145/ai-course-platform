<template>
  <section class="module">
    <PageHead
      kicker="ADMIN / UPLOADS"
      title="上传会话监控"
      sub="最近 100 条上传会话的状态与磁盘分片进度：可合并已齐片的会话，或取消仍在进行的会话。"
      source="api"
    >
      <template #actions>
        <button class="btn btn-ghost" :disabled="loading" @click="refreshList">
          {{ loading ? '拉取中…' : '刷新列表' }}
        </button>
      </template>
    </PageHead>

    <div v-if="listError" class="callout">
      <span>ERROR / 加载失败</span>
      <p>{{ listError }}</p>
    </div>

    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <section class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">CHUNKED / LOCAL FILE</p>
          <h2>本地文件分片上传</h2>
        </div>
      </header>
      <input
        ref="fileInputRef"
        type="file"
        class="file-input"
        aria-label="选择要分片上传的本地文件"
        @change="onFilePicked"
      />
      <div class="filter-row">
        <button class="btn" :disabled="isUploading" @click="pickFile">
          {{ pickedFile ? '重新选择' : '选择本地文件' }}
        </button>
        <span class="picked">{{
          pickedFile ? `${pickedFile.name} · ${formatUploadSize(pickedFile.size)}` : '未选择文件'
        }}</span>
        <label class="knob">
          分片大小(MB)
          <input
            v-model.number="chunkSizeMb"
            class="input num"
            type="number"
            min="1"
            :max="MAX_CHUNK_MB"
            :disabled="isUploading"
          />
        </label>
        <label class="knob">
          切片阈值(MB)
          <input
            v-model.number="thresholdMb"
            class="input num"
            type="number"
            min="1"
            max="2048"
            :disabled="isUploading"
          />
        </label>
        <button
          class="btn btn-primary"
          :disabled="!pickedFile || isUploading"
          data-testid="upload-start"
          @click="startUpload"
        >
          {{ uploadStatus === 'FAILED' ? '按磁盘真值续传' : '开始上传' }}
        </button>
        <button v-if="isUploading" class="btn btn-danger" @click="cancelUploadFlow">
          取消本次上传
        </button>
      </div>
      <p class="plan-hint">
        每片带前端实算的 MD5 由服务端逐片比对；中断后按服务端报告的缺失分片续传，本地不维护进度计数
      </p>
      <div class="upload-state">
        <div class="progress-row">
          <div
            class="progress-bar"
            role="progressbar"
            aria-label="上传进度"
            :aria-valuenow="percentage"
            aria-valuemin="0"
            aria-valuemax="100"
          >
            <i :style="{ '--p': `${percentage}%` }"></i>
          </div>
          <b class="progress-num">{{ percentage }}%</b>
        </div>
        <p class="plan-hint">
          计划 {{ totalChunks }} 片 · 服务端已落盘 {{ uploadedChunks }} 片
          <template v-if="sessionId">· 会话 {{ sessionId }}</template>
        </p>
      </div>
      <p v-if="uploadError" class="form-error">{{ uploadError }}</p>
      <p v-if="mergedUrl" class="result-line" data-testid="upload-result">
        合并完成，直链：<a :href="mergedUrl" target="_blank" rel="noopener">{{ mergedUrl }}</a>
      </p>
    </section>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">SESSIONS / RECENT</p>
          <h2>
            上传会话列表 <span class="chip chip-mute">{{ sessions.length }} 条</span>
          </h2>
        </div>
      </header>

      <div v-if="pagedSessions.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-80">ID</th>
              <th>文件名称</th>
              <th class="w-96">文件大小</th>
              <th class="w-140">进度</th>
              <th class="w-96">状态</th>
              <th class="w-220">创建时间</th>
              <th class="w-140 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in pagedSessions" :key="String(row.id ?? index)">
              <td>{{ row.id }}</td>
              <td>
                <b class="file-name">{{ fileNameOf(row) }}</b>
              </td>
              <td>{{ formatUploadSize(sizeOf(row)) }}</td>
              <td>
                <span class="progress-inline" :data-testid="`row-progress-${row.id}`">
                  <i :style="{ '--p': `${progressOf(row)}%` }"></i>
                </span>
                {{ progressOf(row) }}%
              </td>
              <td>
                <span class="chip" :class="chipOf(row.status)">{{
                  uploadStatusLabel(row.status)
                }}</span>
              </td>
              <td>{{ row.createdAt || '—' }}</td>
              <td>
                <div class="row-actions">
                  <template v-if="row.status === 'UPLOADING'">
                    <button
                      class="text-btn"
                      :disabled="mergingId === row.id"
                      @click="handleMerge(row)"
                    >
                      {{ mergingId === row.id ? '合并中…' : '合并' }}
                    </button>
                    <button class="text-btn danger" @click="handleCancel(row)">取消</button>
                  </template>
                  <button v-else class="text-btn" @click="viewProgress(row)">查看进度</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载上传会话…</b></div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="upload" /></span>
        <b>{{ emptyDescription() }}</b>
        <p>
          {{
            listError
              ? '修正后可点右上角「刷新列表」重试。'
              : '下方「本地文件分片上传」发起一次上传即可产生会话记录。'
          }}
        </p>
      </div>

      <!-- listSessions 一次性返回全量（后端无分页参数），渲染层按 D2-5 客户端切片 -->
      <footer v-if="showPager" class="card-foot">
        <span>
          第 {{ page }} / {{ pageCount }} 页 · 共 {{ total }} 条（每页 {{ pageSize }} 条）
        </span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1" @click="goPage(-1)">上一页</button>
          <button class="btn btn-sm" :disabled="page >= pageCount" @click="goPage(1)">
            下一页
          </button>
        </div>
      </footer>
    </section>

    <Modal
      v-model:open="progressOpen"
      title="上传进度"
      kicker="SESSION / PROGRESS"
      width="520px"
      @close="stopPolling()"
    >
      <div v-if="currentRow" class="detail-grid">
        <p>
          <span>文件</span><b>{{ fileNameOf(currentRow) }}</b>
        </p>
        <p>
          <span>大小</span><b>{{ formatUploadSize(sizeOf(currentRow)) }}</b>
        </p>
        <p>
          <span>状态</span>
          <b>
            <span class="chip" :class="chipOf(currentRow.status)">
              {{ uploadStatusLabel(currentRow.status) }}
            </span>
          </b>
        </p>
      </div>
      <div class="progress-row detail-row">
        <div class="progress-bar detail-bar">
          <i :style="{ '--p': `${progressOf(currentRow)}%` }"></i>
        </div>
        <b class="progress-num">{{ progressOf(currentRow) }}%</b>
      </div>
      <p v-if="progressMissing.length" class="missing-line">
        缺失分片序号：{{ progressMissing.join('、') }}（补齐后即可合并）
      </p>
      <p v-if="progressError" class="form-error">{{ progressError }}</p>
      <p class="modal-hint">
        <template v-if="currentRow?.status === 'UPLOADING'">
          每 5 秒刷新一次；进度与缺失分片取自服务端磁盘上的实际分片文件
        </template>
        <template v-else>{{
          lastProgressUpdatedAt ? `最近更新：${lastProgressUpdatedAt}` : '会话已结束'
        }}</template>
      </p>
      <template #footer>
        <button class="btn btn-ghost" @click="progressOpen = false">关闭</button>
      </template>
    </Modal>

    <Modal
      v-model:open="askState.open"
      :title="askState.title"
      kicker="CONFIRM"
      width="460px"
      @close="resolveAsk(false)"
    >
      <p class="modal-text">{{ askState.text }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="resolveAsk(false)">{{ askState.cancelText }}</button>
        <button class="btn btn-danger" @click="resolveAsk(true)">{{ askState.confirmText }}</button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import PageHead from '../../components/PageHead.vue'
import Modal from '../../components/Modal.vue'
import Stat from '../../components/Stat.vue'
import Icon from '../../components/Icon.vue'
import { useUi } from '../../composables/ui'
import { useListFeedback } from '@/composables/useListFeedback'
import { useClientPagination } from '@/composables/useClientPagination'
import { MAX_CHUNK_SIZE, useChunkedUpload } from '@/composables/useChunkedUpload'
import uploadApi from '@/api/fileUpload'
import {
  formatUploadSize,
  pickUploadProgress,
  pickUploadSessions,
  uploadStatusLabel,
  uploadStatusTone,
} from '@/utils/uploadDisplay'
import { buildDangerMessage, type DangerConfirmOptions } from '@/utils/dangerMessage'
import type { Metric } from '../../nav'

interface UploadSession {
  id?: number | string
  fileName?: string | null
  filename?: string | null
  fileSize?: number | null
  totalChunks?: number | null
  uploadedChunks?: number | null
  progress?: number | null
  status?: string
  createdAt?: string | null
}

const MB = 1024 * 1024
/** 分片大小上限即后端 multipart 上限（spring.servlet.multipart.max-file-size: 50MB） */
const MAX_CHUNK_MB = Math.floor(MAX_CHUNK_SIZE / MB)
const chipClass = { ok: 'chip-ok', warn: 'chip-warn', danger: 'chip-danger', info: 'chip-mute' }

const { toast } = useUi()
/** 的 toast 色调与 EP 的四档不同，这里做一次语义映射 */
const notify = {
  info: (message: string) => toast(message, 'info'),
  success: (message: string) => toast(message, 'ok'),
  warning: (message: string) => toast(message, 'warn'),
  error: (message: string) => toast(message, 'danger'),
}

const sessions = ref<UploadSession[]>([])

const {
  page,
  pageSize,
  total,
  paged: pagedSessions,
  showPager,
  onPageChange,
} = useClientPagination(() => sessions.value)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function goPage(step: number) {
  onPageChange(Math.min(pageCount.value, Math.max(1, page.value + step)))
}

const { loading, listError, run, emptyDescription } = useListFeedback({
  failMessage: '上传会话加载失败，请稍后刷新；已保留上次显示的数据。',
  toastMessage: '上传会话加载失败',
  emptyText: '暂无上传会话',
  errorEmptyText: '无法确认会话数据，请查看上方提示后重试',
  notify: (message) => notify.error(message),
})

const metrics = computed<Metric[]>(() => [
  {
    label: '最近会话数',
    value: sessions.value.length,
    unit: '条',
    note: '服务端列表',
    icon: 'file',
  },
  {
    label: '上传中',
    value: countBy('UPLOADING'),
    unit: '条',
    note: '本页统计',
    icon: 'clock',
    tone: 'warn',
  },
  {
    label: '已完成',
    value: countBy('MERGED', 'COMPLETED'),
    unit: '条',
    note: '本页统计',
    icon: 'check',
    tone: 'ok',
  },
])

function countBy(...statuses: string[]): number {
  return sessions.value.filter((row) => statuses.includes(String(row.status))).length
}

function fileNameOf(row?: UploadSession | null): string {
  return String(row?.fileName || row?.filename || '未命名')
}

function sizeOf(row?: UploadSession | null): number | null | undefined {
  const raw = row?.fileSize
  return typeof raw === 'number' ? raw : null
}

function progressOf(row?: UploadSession | null): number {
  if (!row) return 0
  if (typeof row.progress === 'number' && Number.isFinite(row.progress)) {
    return Math.min(100, Math.max(0, Math.round(row.progress)))
  }
  const total = Number(row.totalChunks ?? 0)
  const uploaded = Number(row.uploadedChunks ?? 0)
  return total > 0 ? Math.min(100, Math.round((uploaded / total) * 100)) : 0
}

function chipOf(status?: string): string {
  return chipClass[uploadStatusTone(status)]
}

/* ---- 本地分片上传入口 ---- */
const fileInputRef = ref<HTMLInputElement | null>(null)
const pickedFile = ref<File | null>(null)
const chunkSizeMb = ref(5)
const thresholdMb = ref(5)
const {
  sessionId,
  status: uploadStatus,
  error: uploadError,
  uploadPath,
  totalChunks,
  uploadedChunks,
  isUploading,
  percentage,
  upload,
  cancel: cancelUploadSession,
} = useChunkedUpload()

// 静态映射在 /uploads/**（UploadResourceConfig），经 /api 前缀转发；该路径需登录，
// 浏览器同站请求由 HttpOnly 认证 Cookie 自动携带。
const mergedUrl = computed(() => (uploadPath.value ? `/api/uploads/${uploadPath.value}` : ''))

function pickFile() {
  fileInputRef.value?.click()
}

function onFilePicked(event: Event) {
  const file = (event.target as HTMLInputElement)?.files?.[0]
  if (file) pickedFile.value = file
}

async function startUpload() {
  const file = pickedFile.value
  if (!file) return
  try {
    await upload(file, {
      chunkSize: chunkSizeMb.value * MB,
      chunkThreshold: thresholdMb.value * MB,
    })
    notify.success(`会话 ${sessionId.value} 已合并完成`)
    await refreshList()
  } catch {
    // 服务端原因（缺片、摘要不符、类型不允许）已由 request.ts 拦截器提示，
    // 这里只给动作口径：可原地点「按磁盘真值续传」，不必重头再传一遍。
    notify.error('上传未完成，可点「按磁盘真值续传」补齐缺失分片')
  }
}

async function cancelUploadFlow() {
  const ok = await askDanger({
    verb: '取消',
    target: '本次上传',
    impact: '服务端已落盘的分片将被回收，需重新上传该文件',
    irreversible: true,
  })
  if (!ok) return
  try {
    await cancelUploadSession()
    notify.success('本次上传已取消')
    await refreshList()
  } catch {
    notify.error('取消失败，请稍后重试')
  }
}

/* ---- 列表刷新 ---- */
async function refreshList() {
  await run(async () => {
    const list = pickUploadSessions(await uploadApi.listSessions())
    if (!list) throw new Error('上传会话响应格式无效')
    sessions.value = list.map((item) => {
      const row = item as UploadSession
      return {
        ...row,
        fileName: row.fileName || row.filename,
        // 实体无 progress 字段：进度由已落盘分片数/总分片数推导
        progress: progressOf(row),
      }
    })
  })
}

/* ---- 合并 / 取消 ---- */
const mergingId = ref<number | string | null>(null)

async function handleMerge(row: UploadSession) {
  mergingId.value = row.id ?? null
  try {
    await uploadApi.mergeChunks(Number(row.id))
    notify.success(`会话 ${row.id} 的分片已合并`)
    applyPatch(row.id, { status: 'COMPLETED', progress: 100 })
    if (currentRow.value?.id === row.id) {
      progressMissing.value = []
      stopPolling()
    }
    await refreshList()
  } catch {
    // 失败原因（如「缺少分片：1、3」）已由 request.ts 响应拦截器统一提示，
    // 此处只补动作口径；不拼接 err.message——HTTP 错误路径下它是英文的 axios 文案。
    notify.error('合并失败，请稍后重试')
  } finally {
    mergingId.value = null
  }
}

async function handleCancel(row: UploadSession) {
  // 明确影响范围（分片进度不再继续、需重新上传）与不可逆性
  const ok = await askDanger({
    verb: '取消',
    target: '上传会话',
    impact: '已上传的分片进度将不再继续，需重新开始上传',
    irreversible: true,
  })
  if (!ok) return
  try {
    await uploadApi.cancelUpload(Number(row.id))
    applyPatch(row.id, { status: 'CANCELLED' })
    if (currentRow.value?.id === row.id) stopPolling()
    notify.success('会话已取消')
    await refreshList()
  } catch {
    notify.error('取消失败，请稍后重试')
  }
}

/* ---- 进度详情与轮询 ---- */
const progressOpen = ref(false)
const currentRow = ref<UploadSession | null>(null)
const progressMissing = ref<number[]>([])
const progressError = ref('')
const lastProgressUpdatedAt = ref('')
const progressTimer = ref<ReturnType<typeof setInterval> | null>(null)
const progressRefreshing = ref(false)

function applyPatch(id: number | string | undefined, patch: Partial<UploadSession>) {
  sessions.value = sessions.value.map((row) => (row.id === id ? { ...row, ...patch } : row))
  if (currentRow.value?.id === id) currentRow.value = { ...currentRow.value, ...patch }
}

function stopPolling() {
  if (progressTimer.value) clearInterval(progressTimer.value)
  progressTimer.value = null
}

function startPolling() {
  stopPolling()
  if (currentRow.value?.status !== 'UPLOADING') return
  progressTimer.value = setInterval(() => {
    if (currentRow.value?.status !== 'UPLOADING') {
      stopPolling()
      return
    }
    void refreshProgress(true)
  }, 5000)
}

async function refreshProgress(silent = false) {
  const row = currentRow.value
  if (!row || progressRefreshing.value) return
  progressRefreshing.value = true
  progressError.value = ''
  try {
    const view = pickUploadProgress(await uploadApi.getProgress(Number(row.id)))
    if (currentRow.value?.id !== row.id) return
    progressMissing.value = view.missing
    const merged: UploadSession = {
      ...row,
      status: view.status || row.status,
      uploadedChunks: view.uploaded ?? row.uploadedChunks,
      totalChunks: view.total ?? row.totalChunks,
      progress: view.percent ?? row.progress,
    }
    currentRow.value = merged
    sessions.value = sessions.value.map((item) => (item.id === merged.id ? merged : item))
    lastProgressUpdatedAt.value = new Date().toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
    if (merged.status !== 'UPLOADING') stopPolling()
  } catch {
    progressError.value = '无法获取最新进度，系统会继续重试。'
    if (!silent) notify.error('进度加载失败，请稍后重试')
  } finally {
    progressRefreshing.value = false
  }
}

async function viewProgress(row: UploadSession) {
  stopPolling()
  currentRow.value = row
  progressError.value = ''
  progressMissing.value = []
  lastProgressUpdatedAt.value = ''
  progressOpen.value = true
  await refreshProgress()
  startPolling()
}

/* ---- 确认弹层（promise 式，供 askDanger 等待用户选择） ---- */
const askState = reactive({
  open: false,
  title: '确认',
  text: '',
  confirmText: '确定',
  cancelText: '取消',
  resolve: null as ((value: boolean) => void) | null,
})

function ask(message: string, title: string, confirmText: string): Promise<boolean> {
  if (askState.resolve) askState.resolve(false)
  askState.title = title
  askState.text = message
  askState.confirmText = confirmText
  askState.cancelText = '取消'
  askState.open = true
  return new Promise((resolve) => {
    askState.resolve = resolve
  })
}

function resolveAsk(value: boolean) {
  askState.open = false
  const settle = askState.resolve
  askState.resolve = null
  settle?.(value)
}

function askDanger(options: DangerConfirmOptions): Promise<boolean> {
  return ask(
    buildDangerMessage(options),
    `${options.verb}确认`,
    options.confirmText || `确定${options.verb}`,
  )
}

onMounted(() => {
  void refreshList()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.file-input {
  display: none;
}
.knob {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--ink-3);
}
.knob .num {
  width: 72px;
  height: 30px;
}
.picked {
  font-size: 11px;
  color: var(--ink-2);
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.plan-hint {
  margin: 8px 0 0;
  font-size: 10.5px;
  color: var(--ink-3);
}
.upload-state {
  margin-top: 10px;
}
.progress-bar {
  height: 8px;
  border: 1px solid var(--ink);
  background: var(--surface-2);
}
.progress-bar i {
  display: block;
  height: 100%;
  width: var(--p, 0%);
  background: var(--accent);
}
.progress-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.detail-row {
  margin-top: 12px;
}
.detail-bar {
  flex: 1;
}
.progress-num {
  flex: none;
  font-family: var(--font-mono);
  font-size: 11px;
}
.result-line {
  margin: 10px 0 0;
  font-size: 11px;
}
.result-line a {
  color: var(--accent);
  word-break: break-all;
}
.file-name {
  display: block;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
.detail-grid p {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 6px 0;
  font-size: 11px;
}
.detail-grid span {
  color: var(--ink-3);
}
.missing-line {
  margin: 10px 0 0;
  font-size: 11px;
  color: var(--warn);
}
</style>
