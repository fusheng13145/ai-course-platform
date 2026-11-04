import { describe, expect, it, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * 任务 10：教师章节页的课程资源区与上传弹窗。
 * 契约取自后端 ResourceController：
 * - 列表 GET /resources/course/{courseId} → ApiResponse<List<Resource>>（实体无 fileSize）
 * - 上传 POST /resources/upload（multipart：file / courseId / description，单文件 ≤ 50MB）
 */
const api = vi.hoisted(() => ({
  getChapterTree: vi.fn(),
  getResourceList: vi.fn(),
  uploadResource: vi.fn(),
  downloadResource: vi.fn(),
}))

vi.mock('@/api/teacher', () => ({
  default: { getChapterTree: api.getChapterTree },
}))
vi.mock('@/api/chapter', () => ({
  default: {
    createChapter: vi.fn(),
    updateChapter: vi.fn(),
    deleteChapter: vi.fn(),
    importChaptersExcel: vi.fn(),
  },
}))
vi.mock('@/api/resource', () => ({
  default: {
    getResourceList: api.getResourceList,
    uploadResource: api.uploadResource,
    downloadResource: api.downloadResource,
  },
  getResourceList: api.getResourceList,
  uploadResource: api.uploadResource,
  downloadResource: api.downloadResource,
  buildUploadForm: vi.fn((data: unknown) => data),
}))
vi.mock('@/utils/logger', () => ({
  default: { error: vi.fn(), warn: vi.fn(), log: vi.fn() },
}))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({
  useUi: () => ({ toast }),
}))

import ChaptersTab from '../views/course/tabs/ChaptersTab.vue'

/** Modal 走 Teleport，测试里换成同 slot 结构的存根；上传弹窗由子组件渲染 */
const stubs = {
  Modal: {
    props: ['open', 'title', 'kicker', 'width', 'closeOnBackdrop'],
    template: '<div v-if="open" class="modal-stub"><slot /><slot name="footer" /></div>',
  },
  Icon: true,
}

const RESOURCE_A = {
  id: 11,
  fileName: '课程大纲.pdf',
  fileType: 'application/pdf',
  url: '/uploads/a.pdf',
  courseId: 7,
  uploadTime: '2026-10-04T12:30:00',
  visibleToAll: true,
}
const RESOURCE_B = {
  id: 12,
  fileName: '实验演示.mp4',
  fileType: 'video/mp4',
  url: '/uploads/b.mp4',
  courseId: 7,
  uploadTime: '2026-10-05T09:00:00',
  visibleToAll: true,
}

function mountTab() {
  return mount(ChaptersTab, { props: { courseId: 7 }, global: { stubs } })
}

function openUploadDialog(wrapper: ReturnType<typeof mountTab>) {
  const trigger = wrapper.findAll('button').find((button) => button.text() === '上传资源')
  return trigger as NonNullable<typeof trigger>
}

function fileInput(wrapper: ReturnType<typeof mountTab>) {
  return wrapper.find('.modal-stub input[type="file"]')
}

async function pickFile(wrapper: ReturnType<typeof mountTab>, file: File) {
  const input = fileInput(wrapper)
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

function footerButton(wrapper: ReturnType<typeof mountTab>, label: string) {
  const button = wrapper
    .findAll('.modal-stub button')
    .find((candidate) => candidate.text() === label)
  return button as NonNullable<typeof button>
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getChapterTree.mockResolvedValue({ data: { records: [] } })
  api.getResourceList.mockResolvedValue({ data: [RESOURCE_A, RESOURCE_B] })
})

describe('章节页 · 课程资源列表', () => {
  it('加载成功后按名称/类型/大小/上传时间/下载入口列出当前课程资源', async () => {
    const wrapper = mountTab()
    await flushPromises()

    expect(api.getResourceList).toHaveBeenCalledWith(7)
    const rows = wrapper.findAll('.table-card tbody tr')
    expect(rows).toHaveLength(2)

    expect(rows[0].text()).toContain('课程大纲.pdf')
    expect(rows[0].text()).toContain('文档')
    // Resource 实体没有 fileSize：如实显示 —，不做估算
    expect(rows[0].text()).toContain('—')
    expect(rows[0].text()).toContain('2026-10-04 12:30')
    expect(rows[0].find('.text-btn').text()).toBe('下载')

    expect(rows[1].text()).toContain('实验演示.mp4')
    expect(rows[1].text()).toContain('视频')
    expect(wrapper.text()).not.toContain('暂无课程资源')
    wrapper.unmount()
  })

  it('空列表只显示空态，不与失败态混用', async () => {
    api.getResourceList.mockResolvedValueOnce({ data: [] })
    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.findAll('.table-card tbody tr')).toHaveLength(0)
    expect(wrapper.find('.callout').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无课程资源')
    expect(wrapper.text()).not.toContain('资源列表未能加载')
    wrapper.unmount()
  })

  it('加载失败给出持久错误提示，且不复用「暂无资源」文案', async () => {
    api.getResourceList.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.find('.callout').text()).toContain('课程资源加载失败')
    expect(wrapper.text()).toContain('资源列表未能加载')
    expect(wrapper.text()).not.toContain('暂无课程资源')
    expect(wrapper.findAll('.table-card tbody tr')).toHaveLength(0)
    expect(toast).toHaveBeenCalledWith('课程资源加载失败', 'danger')
    wrapper.unmount()
  })
})

describe('章节页 · 资源上传', () => {
  it('选文件 → 走 /resources/upload → 成功后关闭弹窗并刷新列表', async () => {
    api.uploadResource.mockResolvedValueOnce({ data: { id: 13 } })
    const wrapper = mountTab()
    await flushPromises()

    await openUploadDialog(wrapper).trigger('click')
    await flushPromises()
    expect(wrapper.find('.modal-stub').exists()).toBe(true)

    const file = new File(['%PDF-1.4'], 'syllabus.pdf', { type: 'application/pdf' })
    await pickFile(wrapper, file)
    await footerButton(wrapper, '上传').trigger('click')
    await flushPromises()

    expect(api.uploadResource).toHaveBeenCalledTimes(1)
    const payload = api.uploadResource.mock.calls[0][0]
    expect(payload).toEqual({ file, courseId: 7, description: '' })

    // 成功后：弹窗关闭 + 列表重新拉取（初始 1 次 + 刷新 1 次）
    expect(wrapper.find('.modal-stub').exists()).toBe(false)
    expect(api.getResourceList).toHaveBeenCalledTimes(2)
    expect(toast).toHaveBeenCalledWith('课程资源已上传', 'ok')
    wrapper.unmount()
  })

  it('上传失败时保留弹窗、展示可读原因，并可原地点「重试上传」', async () => {
    api.uploadResource.mockRejectedValueOnce(new Error('文件内容与声明类型不符，已拒绝上传'))
    const wrapper = mountTab()
    await flushPromises()

    await openUploadDialog(wrapper).trigger('click')
    await flushPromises()
    await pickFile(wrapper, new File(['%PDF-1.4'], 'fake.pdf', { type: 'application/pdf' }))
    await footerButton(wrapper, '上传').trigger('click')
    await flushPromises()

    const modal = wrapper.find('.modal-stub')
    expect(modal.exists()).toBe(true)
    expect(modal.text()).toContain('上传失败')
    expect(modal.text()).toContain('文件内容与声明类型不符')
    expect(api.getResourceList).toHaveBeenCalledTimes(1)

    // 文件仍在、按钮可再次点击 → 真正的重试态
    api.uploadResource.mockResolvedValueOnce({ data: { id: 14 } })
    await footerButton(wrapper, '重试上传').trigger('click')
    await flushPromises()
    expect(api.uploadResource).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.modal-stub').exists()).toBe(false)
    expect(api.getResourceList).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('不支持的扩展名在本地拦下，不发请求', async () => {
    const wrapper = mountTab()
    await flushPromises()

    await openUploadDialog(wrapper).trigger('click')
    await flushPromises()
    await pickFile(wrapper, new File(['MZ'], 'setup.exe', { type: 'application/x-msdownload' }))

    expect(wrapper.find('.modal-stub').text()).toContain('不支持该文件格式')
    expect(api.uploadResource).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
