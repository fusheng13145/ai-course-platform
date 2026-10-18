/**
 * useChunkedUpload 行为契约（DR-69 = A 验收线①②③）
 *
 * 只断言「对外可见的行为」：发给后端的报文与顺序、断点续传的依据、状态与错误口径。
 * 不断言内部实现（不检查私有 ref、不检查是否用了某个辅助函数），
 * 这样实现可以重构，而契约不会跟着代码一起漂。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { watch } from 'vue'
import { md5Hex } from '@/utils/md5'

const api = vi.hoisted(() => ({
  createSession: vi.fn(),
  uploadChunk: vi.fn(),
  mergeChunks: vi.fn(),
  getProgress: vi.fn(),
  cancelUpload: vi.fn(),
  listSessions: vi.fn(),
}))

vi.mock('@/api/fileUpload', () => api)

const { useChunkedUpload } = await import('@/composables/useChunkedUpload')

const KB = 1024

/** 可复现的伪随机字节：失败时能凭内容定位到具体分片，不用看随机数 */
function makeFile(name = 'lecture.pdf', size = 5 * KB, seed = 7): File {
  const bytes = new Uint8Array(size)
  let s = seed
  for (let i = 0; i < size; i++) {
    s = (s * 1103515245 + 12345) & 0x7fffffff
    bytes[i] = s % 256
  }
  return new File([bytes], name, { type: 'application/pdf', lastModified: 1730000000000 })
}

/** jsdom 的 Blob 没有 arrayBuffer()，与生产侧同样走 FileReader 兜底 */
async function readBlob(b: Blob): Promise<Uint8Array> {
  const buf =
    typeof b.arrayBuffer === 'function'
      ? await b.arrayBuffer()
      : await new Promise<ArrayBuffer>((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => resolve(reader.result as ArrayBuffer)
          reader.onerror = () => reject(reader.error)
          reader.readAsArrayBuffer(b)
        })
  return new Uint8Array(buf)
}

/** 取 uploadChunk 第 N 次调用的 (sessionId, index, formData) */
function chunkCall(i: number) {
  const call = api.uploadChunk.mock.calls[i]
  return { sessionId: call[0], index: call[1], form: call[2] as FormData }
}

function session(id: number, extra = {}) {
  return {
    code: 200,
    message: 'ok',
    data: { id, filename: 'lecture.pdf', fileSize: 5 * KB, totalChunks: 3, status: 'UPLOADING', ...extra },
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  api.createSession.mockResolvedValue(session(101))
  api.uploadChunk.mockResolvedValue(session(101))
  api.mergeChunks.mockResolvedValue(session(101, { status: 'COMPLETED', uploadPath: '2026/10/abc.pdf' }))
  api.getProgress.mockResolvedValue({
    code: 200,
    message: 'ok',
    data: { uploaded: 0, total: 3, percentage: '0.0%', status: 'UPLOADING', missingIndexes: [0, 1, 2] },
  })
})

describe('分片计划（阈值与分片大小是被消费的旋钮）', () => {
  it('小于阈值文件不切片：一个分片承载全部字节', async () => {
    const { planChunks } = useChunkedUpload()
    const file = makeFile('small.pdf', 512)
    const plan = planChunks(file, { chunkThreshold: 1 * KB, chunkSize: 1 * KB })
    expect(plan.totalChunks).toBe(1)
    expect(plan.slices).toEqual([{ index: 0, start: 0, end: 512 }])
  })

  it('按 chunkSize 切到最后一片允许不足一片', async () => {
    const { planChunks } = useChunkedUpload()
    const plan = planChunks(makeFile('five.bytes', 5), { chunkThreshold: 2, chunkSize: 2 })
    expect(plan.totalChunks).toBe(3)
    expect(plan.slices).toEqual([
      { index: 0, start: 0, end: 2 },
      { index: 1, start: 2, end: 4 },
      { index: 2, start: 4, end: 5 },
    ])
  })

  it('默认参数下 2GB 文件既不超后端 totalChunks ≤ 4096，单片也不超 multipart 50MB', () => {
    const { planChunks } = useChunkedUpload()
    const huge = { size: 2 * 1024 * 1024 * 1024, name: 'a.bin', lastModified: 1, type: '' } as unknown as File
    const plan = planChunks(huge)
    expect(plan.totalChunks).toBeLessThanOrEqual(4096)
    expect(plan.chunkSize).toBeLessThanOrEqual(50 * 1024 * 1024)
  })
})

describe('上传报文（验收线②：前端 MD5 ↔ 后端逐片实算比对）', () => {
  it('createSession 报后端要求的字段名，totalChunks 与切片计划一致', async () => {
    const { upload } = useChunkedUpload()
    await upload(makeFile('lecture.pdf', 5 * KB), { chunkThreshold: 2 * KB, chunkSize: 2 * KB })
    expect(api.createSession).toHaveBeenCalledWith({
      filename: 'lecture.pdf',
      fileSize: 5 * KB,
      totalChunks: 3,
    })
  })

  it('每一片都带 file 与 md5，且 md5 是该片字节的真实 MD5', async () => {
    const { upload } = useChunkedUpload()
    const file = makeFile('lecture.pdf', 5 * KB)
    await upload(file, { chunkThreshold: 2 * KB, chunkSize: 2 * KB })

    expect(api.uploadChunk).toHaveBeenCalledTimes(3)
    for (const { form } of [chunkCall(0), chunkCall(1), chunkCall(2)]) {
      const blob = form.get('file') as Blob
      const declared = form.get('md5') as string
      expect(await md5Hex(await readBlob(blob))).toBe(declared)
    }
  })

  it('分片字节严格不重叠且拼回原文件', async () => {
    const { upload } = useChunkedUpload()
    const file = makeFile('lecture.pdf', 5 * KB)
    await upload(file, { chunkThreshold: 2 * KB, chunkSize: 2 * KB })

    const merged = new Uint8Array(file.size)
    let cursor = 0
    for (let i = 0; i < 3; i++) {
      const { index, form } = chunkCall(i)
      expect(index).toBe(i)
      const part = await readBlob(form.get('file') as Blob)
      merged.set(part, cursor)
      cursor += part.length
    }
    expect(cursor).toBe(file.size)
    expect(merged).toEqual(await readBlob(file))
  })

  it('全部序号都用同一个会话 id，合并只调用一次', async () => {
    const { upload } = useChunkedUpload()
    await upload(makeFile(), { chunkThreshold: 2 * KB, chunkSize: 2 * KB })
    for (const { sessionId } of [chunkCall(0), chunkCall(1), chunkCall(2)]) {
      expect(sessionId).toBe(101)
    }
    expect(api.mergeChunks).toHaveBeenCalledTimes(1)
    expect(api.mergeChunks).toHaveBeenCalledWith(101)
  })

  it('成功后回读服务端出参并复位状态', async () => {
    const { upload, sessionId, status, uploadedChunks, totalChunks, uploadPath, isUploading } =
      useChunkedUpload()
    const res = await upload(makeFile(), { chunkThreshold: 2 * KB, chunkSize: 2 * KB })
    expect(res.uploadPath).toBe('2026/10/abc.pdf')
    expect(uploadPath.value).toBe('2026/10/abc.pdf')
    expect(status.value).toBe('COMPLETED')
    expect(sessionId.value).toBe(101)
    expect(totalChunks.value).toBe(3)
    expect(uploadedChunks.value).toBe(3)
    expect(isUploading.value).toBe(false)
  })
})

describe('断点续传以磁盘真值驱动（验收线③）', () => {
  it('同指纹的既有会话：只补服务端报告的缺失分片', async () => {
    // 第一轮：片 0 落盘成功、片 1 被打断 → 会话记录必须留下（见下方"分片失败后保留"同源断言）
    api.uploadChunk.mockResolvedValueOnce(session(101, { uploadedChunks: 1 })).mockRejectedValueOnce(
      new Error('网络中断'),
    )
    const first = useChunkedUpload()
    const opts = { chunkThreshold: 2 * KB, chunkSize: 2 * KB }
    await expect(first.upload(makeFile('lecture.pdf', 5 * KB), opts)).rejects.toThrow('网络中断')

    // 第二轮：换一个实例、同一份文件，服务端磁盘上已有片 0
    api.getProgress.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { uploaded: 1, total: 3, percentage: '33.3%', status: 'UPLOADING', missingIndexes: [1, 2] },
    })
    api.uploadChunk.mockResolvedValue(session(101))
    api.createSession.mockClear()
    const roundOne = api.uploadChunk.mock.calls.length

    const second = useChunkedUpload()
    await second.upload(makeFile('lecture.pdf', 5 * KB), opts)

    expect(api.createSession).not.toHaveBeenCalled()
    expect(api.getProgress).toHaveBeenCalledWith(101)
    const sent = api.uploadChunk.mock.calls.slice(roundOne).map((c) => c[1])
    expect(sent).toEqual([1, 2])
  })

  it('服务端报告无缺失：一片也不发，直接合并', async () => {
    api.getProgress.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { uploaded: 3, total: 3, percentage: '100.0%', status: 'UPLOADING', missingIndexes: [] },
    })
    localStorage.setItem(
      'ai-course:chunked-upload:lecture.pdf:5120:1730000000000',
      JSON.stringify({ sessionId: 101, totalChunks: 3, chunkSize: 2 * KB }),
    )
    const { upload } = useChunkedUpload()
    await upload(makeFile('lecture.pdf', 5 * KB), { chunkThreshold: 2 * KB, chunkSize: 2 * KB })
    expect(api.uploadChunk).not.toHaveBeenCalled()
    expect(api.mergeChunks).toHaveBeenCalledWith(101)
  })

  it('会话在服务端已是完成态：不复用，重建会话（否则 merge 必然被状态机拒）', async () => {
    localStorage.setItem(
      'ai-course:chunked-upload:lecture.pdf:5120:1730000000000',
      JSON.stringify({ sessionId: 101, totalChunks: 3, chunkSize: 2 * KB }),
    )
    api.getProgress.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { uploaded: 3, total: 3, percentage: '100.0%', status: 'COMPLETED', missingIndexes: [] },
    })
    const { upload } = useChunkedUpload()
    await upload(makeFile('lecture.pdf', 5 * KB), { chunkThreshold: 2 * KB, chunkSize: 2 * KB })
    expect(api.createSession).toHaveBeenCalledTimes(1)
    expect(api.uploadChunk).toHaveBeenCalledTimes(3)
  })

  it('计划变了（分片大小不同）旧会话不可复用：重建会话', async () => {
    localStorage.setItem(
      'ai-course:chunked-upload:lecture.pdf:5120:1730000000000',
      JSON.stringify({ sessionId: 101, totalChunks: 3, chunkSize: 999 }),
    )
    const { upload } = useChunkedUpload()
    await upload(makeFile('lecture.pdf', 5 * KB), { chunkThreshold: 2 * KB, chunkSize: 2 * KB })
    expect(api.getProgress).not.toHaveBeenCalled()
    expect(api.createSession).toHaveBeenCalledTimes(1)
  })

  it('完成即清掉续传记录，不留可复用会话', async () => {
    localStorage.setItem(
      'ai-course:chunked-upload:lecture.pdf:5120:1730000000000',
      JSON.stringify({ sessionId: 101, totalChunks: 3, chunkSize: 2 * KB }),
    )
    api.getProgress.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { uploaded: 3, total: 3, percentage: '100.0%', status: 'UPLOADING', missingIndexes: [] },
    })
    const { upload } = useChunkedUpload()
    await upload(makeFile('lecture.pdf', 5 * KB), { chunkThreshold: 2 * KB, chunkSize: 2 * KB })
    expect(localStorage.getItem('ai-course:chunked-upload:lecture.pdf:5120:1730000000000')).toBeNull()
  })

  it('分片失败后保留会话记录，下一次从缺失处续传', async () => {
    api.uploadChunk.mockRejectedValueOnce(new Error('分片校验失败'))
    const { upload, status, error } = useChunkedUpload()
    await expect(
      upload(makeFile('lecture.pdf', 5 * KB), { chunkThreshold: 2 * KB, chunkSize: 2 * KB }),
    ).rejects.toThrow('分片校验失败')
    expect(api.mergeChunks).not.toHaveBeenCalled()
    expect(status.value).toBe('FAILED')
    expect(error.value).toBe('分片校验失败')
    expect(localStorage.getItem('ai-course:chunked-upload:lecture.pdf:5120:1730000000000')).toContain('101')
  })

  it('服务端缺片 400 的错误口径透传给调用方', async () => {
    api.mergeChunks.mockRejectedValueOnce(new Error('缺少分片：1、3（已落盘 1/3）'))
    const { upload, error } = useChunkedUpload()
    await expect(
      upload(makeFile('lecture.pdf', 5 * KB), { chunkThreshold: 2 * KB, chunkSize: 2 * KB }),
    ).rejects.toThrow('缺少分片')
    expect(error.value).toContain('缺少分片')
  })
})

describe('进度以服务端返回的会话为准', () => {
  it('每上传一片，uploadedChunks 跟着服务端会话行前进', async () => {
    api.uploadChunk
      .mockResolvedValueOnce(session(101, { uploadedChunks: 1 }))
      .mockResolvedValueOnce(session(101, { uploadedChunks: 2 }))
      .mockResolvedValueOnce(session(101, { uploadedChunks: 3 }))
    const { upload, uploadedChunks } = useChunkedUpload()
    const seen: number[] = []
    watch(uploadedChunks, (v) => seen.push(v), { flush: 'sync' })
    await upload(makeFile('lecture.pdf', 5 * KB), { chunkThreshold: 2 * KB, chunkSize: 2 * KB })
    expect(seen).toEqual([1, 2, 3])
  })

  it('服务端未回该字段时退回「已完成片数」，不显示 undefined', async () => {
    const { upload, uploadedChunks } = useChunkedUpload()
    await upload(makeFile('lecture.pdf', 5 * KB), { chunkThreshold: 2 * KB, chunkSize: 2 * KB })
    expect(uploadedChunks.value).toBe(3)
  })

  it('并发两个 upload 时，后启动者不得复用前者的进度状态', async () => {
    const a = useChunkedUpload()
    const b = useChunkedUpload()
    await a.upload(makeFile('a.pdf', 2 * KB), { chunkThreshold: 1, chunkSize: 1 * KB })
    expect(b.uploadedChunks.value).toBe(0)
    expect(b.totalChunks.value).toBe(0)
  })
})

describe('取消与复位', () => {
  it('cancel 会通知后端并清掉本地续传记录', async () => {
    const { upload, cancel } = useChunkedUpload()
    await upload(makeFile('lecture.pdf', 5 * KB), { chunkThreshold: 2 * KB, chunkSize: 2 * KB })
    await cancel()
    expect(api.cancelUpload).toHaveBeenCalledWith(101)
    expect(localStorage.length).toBe(0)
  })

  it('reset 清空状态但不碰后端', async () => {
    const { upload, reset, sessionId, status, uploadPath } = useChunkedUpload()
    await upload(makeFile('lecture.pdf', 5 * KB), { chunkThreshold: 2 * KB, chunkSize: 2 * KB })
    reset()
    expect(sessionId.value).toBeNull()
    expect(status.value).toBe('')
    expect(uploadPath.value).toBe('')
    expect(api.cancelUpload).not.toHaveBeenCalled()
  })

  it('空文件被拒绝，且一个请求都不发', async () => {
    const { upload } = useChunkedUpload()
    await expect(upload(new File([], 'empty.bin'))).rejects.toThrow('文件为空')
    expect(api.createSession).not.toHaveBeenCalled()
    expect(api.uploadChunk).not.toHaveBeenCalled()
  })
})
