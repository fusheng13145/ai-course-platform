import { describe, expect, it, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const api = vi.hoisted(() => ({
  listSessions: vi.fn(),
  createSession: vi.fn(),
  uploadChunk: vi.fn(),
  mergeChunks: vi.fn(),
  getProgress: vi.fn(),
  cancelUpload: vi.fn(),
}))

vi.mock('@/api/fileUpload', () => ({
  default: api,
  listSessions: api.listSessions,
  createSession: api.createSession,
  uploadChunk: api.uploadChunk,
  mergeChunks: api.mergeChunks,
  getProgress: api.getProgress,
  cancelUpload: api.cancelUpload,
}))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({
  useV2Ui: () => ({ toast }),
}))

import V2AdminUploadsView from '../views/admin/V2AdminUploadsView.vue'

/** V2Modal 走 Teleport，测试里换成同 slot 结构的存根，确认按钮仍能被点到 */
const stubs = {
  V2Modal: {
    props: ['open', 'title'],
    template: '<div v-if="open" class="modal-stub"><slot /><slot name="footer" /></div>',
  },
}

const UPLOADING = {
  id: 1,
  fileName: 'slides.pptx',
  fileSize: 3 * 1024 * 1024,
  totalChunks: 8,
  uploadedChunks: 3,
  status: 'UPLOADING',
  createdAt: '2026-10-29 09:12:00',
}
const MERGED = {
  id: 2,
  fileName: 'lab-data.zip',
  fileSize: 2048,
  totalChunks: 1,
  uploadedChunks: 1,
  status: 'MERGED',
  createdAt: '2026-10-28 18:04:00',
}

function mountView() {
  return mount(V2AdminUploadsView, { global: { stubs } })
}

beforeEach(() => {
  vi.clearAllMocks()
  api.mergeChunks.mockResolvedValue({ data: { uploadPath: 'x/y.bin', status: 'MERGED' } })
  api.cancelUpload.mockResolvedValue({ data: { id: 1, status: 'CANCELLED' } })
  api.getProgress.mockResolvedValue({ data: {} })
})

describe('v2 上传会话监控', () => {
  it('加载失败与真正为空是两种可读状态，且提示走 v2 toast 而非 Element Plus', async () => {
    api.listSessions.mockRejectedValueOnce(new Error('500'))
    const failed = mountView()
    await flushPromises()
    expect(failed.find('.callout').text()).toContain('上传会话加载失败')
    expect(failed.text()).toContain('无法确认会话数据')
    expect(failed.text()).not.toContain('暂无上传会话')
    // useListFeedback 的 notify 被注入为 v2 toast：M-12a 的样板口径
    expect(toast).toHaveBeenCalledWith('上传会话加载失败', 'danger')
    failed.unmount()

    api.listSessions.mockResolvedValueOnce({ data: [] })
    const empty = mountView()
    await flushPromises()
    expect(empty.find('.callout').exists()).toBe(false)
    expect(empty.text()).toContain('暂无上传会话')
    empty.unmount()
  })

  it('只有 UPLOADING 行给「合并／取消」，其余行只给「查看进度」', async () => {
    api.listSessions.mockResolvedValue({ data: [UPLOADING, MERGED] })
    const wrapper = mountView()
    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].findAll('.row-actions button').map((b) => b.text())).toEqual(['合并', '取消'])
    expect(rows[1].findAll('.row-actions button').map((b) => b.text())).toEqual(['查看进度'])
    // 进度与状态取自服务端分片数：3/8 → 38%，chip 随语义色调
    expect(rows[0].text()).toContain('38%')
    expect(rows[0].find('.chip').classes()).toContain('chip-warn')
    expect(rows[1].find('.chip').classes()).toContain('chip-ok')
    expect(rows[1].text()).toContain('2.0KB')
    wrapper.unmount()
  })

  it('响应形状不可信时进失败态，不渲染半张表', async () => {
    api.listSessions.mockResolvedValue({ data: { id: 1, fileName: '不是列表' } })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('.callout').exists()).toBe(true)
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
    wrapper.unmount()
  })

  it('合并直接执行并刷新列表；取消必须先过二次确认，未确认不发请求', async () => {
    api.listSessions.mockResolvedValue({ data: [UPLOADING, MERGED] })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].findAll('.row-actions button')[0].trigger('click')
    await flushPromises()
    expect(api.mergeChunks).toHaveBeenCalledWith(1)
    expect(api.listSessions).toHaveBeenCalledTimes(2)
    expect(toast).toHaveBeenCalledWith('会话 1 的分片已合并', 'ok')

    await wrapper.findAll('tbody tr')[0].findAll('.row-actions button')[1].trigger('click')
    await flushPromises()
    const modal = wrapper.find('.modal-stub')
    expect(modal.exists()).toBe(true)
    expect(modal.text()).toContain('已上传的分片进度将不再继续')
    expect(modal.text()).toContain('此操作不可恢复')
    expect(api.cancelUpload).not.toHaveBeenCalled()

    const buttons = modal.findAll('button')
    await buttons[0].trigger('click') // 取消按钮：不放行
    await flushPromises()
    expect(api.cancelUpload).not.toHaveBeenCalled()

    await wrapper.findAll('tbody tr')[0].findAll('.row-actions button')[1].trigger('click')
    await flushPromises()
    const confirmed = wrapper.findAll('.modal-stub button')
    await confirmed[confirmed.length - 1].trigger('click')
    await flushPromises()
    expect(api.cancelUpload).toHaveBeenCalledWith(1)
    wrapper.unmount()
  })

  it('会话超过一页时按客户端分页切片（listSessions 无分页参数）', async () => {
    const many = Array.from({ length: 25 }, (_, i) => ({
      ...MERGED,
      id: 100 + i,
      fileName: `f${i}.zip`,
    }))
    api.listSessions.mockResolvedValue({ data: many })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findAll('tbody tr')).toHaveLength(20)
    expect(wrapper.find('.card-foot').text()).toContain('第 1 / 2 页 · 共 25 条')
    await wrapper.findAll('.v2-pager button')[1].trigger('click')
    await flushPromises()
    expect(wrapper.findAll('tbody tr')).toHaveLength(5)

    // 不超过一页就不该冒分页器（阴性对照：D2-5 是"超一页才出现"）
    api.listSessions.mockResolvedValue({ data: [UPLOADING, MERGED] })
    const small = mountView()
    await flushPromises()
    expect(small.find('.card-foot').exists()).toBe(false)
    wrapper.unmount()
    small.unmount()
  })

  it('进度弹窗按服务端真值补写分片数与缺失序号', async () => {
    api.listSessions.mockResolvedValue({ data: [UPLOADING, MERGED] })
    api.getProgress.mockResolvedValue({
      data: { uploaded: 6, total: 8, status: 'UPLOADING', missingIndexes: [6, 7] },
    })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('tbody tr')[1].find('.row-actions button').trigger('click')
    await flushPromises()
    const modal = wrapper.find('.modal-stub')
    expect(modal.exists()).toBe(true)
    expect(modal.text()).toContain('缺失分片序号：6、7')
    expect(modal.text()).toContain('75%')
    wrapper.unmount()
  })

  it('进度读不到时在弹窗内明示，不静默留旧数', async () => {
    api.listSessions.mockResolvedValue({ data: [UPLOADING, MERGED] })
    api.getProgress.mockRejectedValue(new Error('500'))
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('tbody tr')[1].find('.row-actions button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.modal-stub').text()).toContain('无法获取最新进度')
    wrapper.unmount()
  })
})
