/**
 * 分片上传组合逻辑（DR-69 = A 的产品入口内核）
 *
 * 一条链路：切片 → 逐片实算 MD5 → createSession → 按缺失索引补片 → merge。
 *
 * 三条不可动摇的口径：
 * 1. 断点续传只认服务端磁盘真值（GET /upload-sessions/{id}/progress 的 missingIndexes），
 *    本地从不维护「我传到第几片」的计数器 —— 页面刷新、网络中断、多标签页都让本地计数不可信。
 * 2. 每片声明的 MD5 由本片字节实算，服务端 DigestInputStream 复算比对；不符服务端删片并 400，
 *    所以上面这条链路一旦算错是「响亮地失败」，不会静默产出坏文件。
 * 3. 分片大小/切片阈值是入参（被 planChunks 真正消费），不是散落在组件里的魔数。
 *    刻意不从 import.meta.env 读：Vite 的 envDir 是 frontend/，而仓库只有根目录 .env，
 *    且 frontend/Dockerfile 的构建上下文是 ./frontend —— VITE_* 键在本项目当前无投递通路，
 *    写进 .env.example 只会多一个悬空旋钮（同 DR-66 的成因，见手册 DR-72）。
 */
import { computed, ref } from 'vue'
import {
  cancelUpload,
  createSession,
  getProgress,
  mergeChunks,
  uploadChunk,
} from '@/api/fileUpload'
import { md5Hex } from '@/utils/md5'

/** 默认分片大小：单片必须同时躲开两道后端上限（multipart 50MB、总片数 4096） */
export const DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024
/** 后端 spring.servlet.multipart.max-file-size（application.yaml:119） */
export const MAX_CHUNK_SIZE = 50 * 1024 * 1024
/** 后端 FileUploadSessionServiceImpl.MAX_TOTAL_CHUNKS */
export const MAX_TOTAL_CHUNKS = 4096

const RESUME_KEY_PREFIX = 'ai-course:chunked-upload:'

export interface ChunkSlice {
  index: number
  start: number
  end: number
}

export interface UploadPlan {
  chunkSize: number
  totalChunks: number
  slices: ChunkSlice[]
}

export interface UploadOptions {
  /** 分片字节数；小于等于 0 时回落默认值 */
  chunkSize?: number
  /** 超过该字节数才切片；默认与 chunkSize 相同 */
  chunkThreshold?: number
}

export interface ChunkedUploadResult {
  sessionId: number
  fileName: string
  totalChunks: number
  uploadPath: string
}

interface ResumeRecord {
  sessionId: number
  totalChunks: number
  chunkSize: number
}

/** 与后端 UploadProgressVO 对齐的窄接口：只声明本组合逻辑要读的键 */
interface ProgressView {
  uploaded?: number
  total?: number
  status?: string
  missingIndexes?: number[]
}

interface SessionView {
  id?: number
  uploadedChunks?: number
  uploadPath?: string
  status?: string
}

function positiveOr(value: number | undefined, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : fallback
}

/**
 * 读出一段字节。Blob#arrayBuffer 在旧 WebKit（Safari < 14）与 jsdom 下不存在，
 * 用 FileReader 兜住这两类环境；两条路径拿到的都是同一份切片字节。
 */
async function readBytes(blob: Blob): Promise<ArrayBuffer> {
  if (typeof blob.arrayBuffer === 'function') return blob.arrayBuffer()
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as ArrayBuffer)
    reader.onerror = () => reject(reader.error ?? new Error('分片字节读取失败'))
    reader.readAsArrayBuffer(blob)
  })
}

/**
 * 把请求的分片大小收敛到后端能吃下的区间：先夹单片上限，再在总片数越界时放大分片。
 * 夹的是「参数」而不是「抛错」——调用方给 100MB 分片时，正确的产品行为是降到 50MB，
 * 而不是让一次上传在第一个请求就 400。
 */
function clampChunkSize(size: number, requested: number): number {
  let chunkSize = Math.min(positiveOr(requested, DEFAULT_CHUNK_SIZE), MAX_CHUNK_SIZE)
  if (size > 0 && Math.ceil(size / chunkSize) > MAX_TOTAL_CHUNKS) {
    chunkSize = Math.min(Math.ceil(size / MAX_TOTAL_CHUNKS), MAX_CHUNK_SIZE)
  }
  return chunkSize
}

function buildPlan(size: number, chunkSize: number, chunkThreshold: number): UploadPlan {
  if (size <= 0) return { chunkSize, totalChunks: 0, slices: [] }
  // 未超阈值就整文件单片：仍走同一条会话链路，避免出现第二套上传实现
  const effective = size <= chunkThreshold ? size : chunkSize
  const totalChunks = Math.max(1, Math.ceil(size / effective))
  const slices: ChunkSlice[] = []
  for (let i = 0; i < totalChunks; i++) {
    const start = i * effective
    slices.push({ index: i, start, end: Math.min(start + effective, size) })
  }
  return { chunkSize: effective, totalChunks, slices }
}

function resumeKey(file: File): string {
  return `${RESUME_KEY_PREFIX}${file.name}:${file.size}:${file.lastModified}`
}

function readResume(key: string): ResumeRecord | null {
  const raw = localStorage.getItem(key)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    if (
      parsed &&
      typeof parsed.sessionId === 'number' &&
      typeof parsed.totalChunks === 'number' &&
      typeof parsed.chunkSize === 'number'
    ) {
      return parsed as ResumeRecord
    }
  } catch {
    // 记录本身就是脏数据，按「没有可续传会话」处理即可
  }
  return null
}

export function useChunkedUpload() {
  const sessionId = ref<number | null>(null)
  const fileName = ref('')
  const status = ref('')
  const uploadPath = ref('')
  const error = ref('')
  const totalChunks = ref(0)
  const uploadedChunks = ref(0)
  const isUploading = ref(false)
  const currentKey = ref('')

  const percentage = computed(() =>
    totalChunks.value > 0 ? Math.round((uploadedChunks.value / totalChunks.value) * 100) : 0,
  )

  function planChunks(file: File, options: UploadOptions = {}): UploadPlan {
    const chunkSize = positiveOr(options.chunkSize, DEFAULT_CHUNK_SIZE)
    const chunkThreshold = positiveOr(options.chunkThreshold, chunkSize)
    return buildPlan(file.size, clampChunkSize(file.size, chunkSize), chunkThreshold)
  }

  async function upload(file: File, options: UploadOptions = {}): Promise<ChunkedUploadResult> {
    if (!file || file.size === 0) {
      error.value = '文件为空，无法上传'
      status.value = 'FAILED'
      throw new Error('文件为空')
    }
    const plan = planChunks(file, options)
    const key = resumeKey(file)
    currentKey.value = key
    fileName.value = file.name
    totalChunks.value = plan.totalChunks
    error.value = ''
    isUploading.value = true

    let resumeFrom: ResumeRecord | null = null
    let pending = plan.slices.map((s) => s.index)
    let baseline = 0
    const stored = readResume(key)
    // 只有「同一切片计划 + 服务端仍在 UPLOADING」的会话才值得续：
    // 已 COMPLETED 的会话再调 merge 会被后端以「当前会话状态不允许合并」拒掉，
    // 切片计划变了则磁盘上的分片边界和新计划不兼容，必须重建会话。
    if (stored && stored.chunkSize === plan.chunkSize && stored.totalChunks === plan.totalChunks) {
      try {
        const res = await getProgress(stored.sessionId)
        const vo: ProgressView = res?.data ?? {}
        if (vo.status === 'UPLOADING') {
          resumeFrom = stored
          baseline = typeof vo.uploaded === 'number' ? vo.uploaded : 0
          pending = Array.isArray(vo.missingIndexes)
            ? vo.missingIndexes
                .filter((i) => Number.isInteger(i) && i >= 0 && i < plan.totalChunks)
                .slice()
                .sort((a, b) => a - b)
            : pending
        }
      } catch {
        // 会话可能已被回收清理：退回新建会话，而不是把一次续传尝试变成上传失败
        resumeFrom = null
      }
    }

    try {
      let sid: number
      if (resumeFrom) {
        sid = resumeFrom.sessionId
        sessionId.value = sid
        uploadedChunks.value = baseline
        status.value = 'UPLOADING'
      } else {
        const created = await createSession({
          filename: file.name,
          fileSize: file.size,
          totalChunks: plan.totalChunks,
        })
        sid = created?.data?.id
        if (typeof sid !== 'number') throw new Error('上传会话创建失败：服务端未返回会话 id')
        sessionId.value = sid
        uploadedChunks.value = 0
        baseline = 0
        status.value = 'UPLOADING'
        localStorage.setItem(
          key,
          JSON.stringify({
            sessionId: sid,
            totalChunks: plan.totalChunks,
            chunkSize: plan.chunkSize,
          }),
        )
      }

      for (let i = 0; i < pending.length; i++) {
        const index = pending[i]
        const slice = plan.slices[index]
        const blob = file.slice(slice.start, slice.end)
        const form = new FormData()
        // md5 先于 file：与后端已验证可用的报文顺序一致（见 scripts/e2e/e2e_deep_verify.py 的 E 段）
        form.append('md5', md5Hex(await readBytes(blob)))
        form.append('file', blob)
        const res = await uploadChunk(sid, index, form)
        const row: SessionView = res?.data ?? {}
        uploadedChunks.value =
          typeof row.uploadedChunks === 'number' ? row.uploadedChunks : baseline + i + 1
      }

      const merged = await mergeChunks(sid)
      const row: SessionView = merged?.data ?? {}
      uploadPath.value = row.uploadPath ?? ''
      status.value = row.status ?? 'COMPLETED'
      uploadedChunks.value = plan.totalChunks
      localStorage.removeItem(key)
      return {
        sessionId: sid,
        fileName: file.name,
        totalChunks: plan.totalChunks,
        uploadPath: uploadPath.value,
      }
    } catch (err) {
      status.value = 'FAILED'
      error.value = err instanceof Error ? err.message : String(err)
      throw err
    } finally {
      isUploading.value = false
    }
  }

  /** 放弃这次上传：通知后端回收分片，并清掉本地续传记录 */
  async function cancel() {
    const sid = sessionId.value
    if (typeof sid === 'number') await cancelUpload(sid)
    if (currentKey.value) localStorage.removeItem(currentKey.value)
    reset()
  }

  /** 只清本机状态；后端会话与已落盘分片保留，下一次同文件仍可续传 */
  function reset() {
    sessionId.value = null
    fileName.value = ''
    status.value = ''
    uploadPath.value = ''
    error.value = ''
    totalChunks.value = 0
    uploadedChunks.value = 0
    isUploading.value = false
    currentKey.value = ''
  }

  return {
    sessionId,
    fileName,
    status,
    uploadPath,
    error,
    totalChunks,
    uploadedChunks,
    isUploading,
    percentage,
    planChunks,
    upload,
    cancel,
    reset,
  }
}
