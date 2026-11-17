import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * 管理端「资源库与版本」接真判据（M-12b）。
 *
 * 课程资源按 `GET /resources/course/{id}` 读；可见性走 PATCH；版本历史与回滚
 * 走 `resourceVersion` 的独立端点。页面不得用示例数组补空。
 */
const courseApi = vi.hoisted(() => ({ getAllCourses: vi.fn() }))
const resourceApi = vi.hoisted(() => ({
  getResourceList: vi.fn(),
  toggleVisibility: vi.fn(),
}))
const versionApi = vi.hoisted(() => ({
  getVersionHistory: vi.fn(),
  rollbackVersion: vi.fn(),
}))
const toast = vi.hoisted(() => vi.fn())

vi.mock('@/api/course', () => ({ ...courseApi, default: courseApi }))
vi.mock('@/api/resource', () => ({ ...resourceApi, default: resourceApi }))
vi.mock('@/api/resourceVersion', () => ({ ...versionApi, default: versionApi }))
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))

import ResourceLibraryPanel from '../views/specials/ResourceLibraryPanel.vue'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })

const COURSES = [
  { id: 4178, name: '计算机网络' },
  { id: 1, name: 'Python 程序设计' },
]
const RESOURCES = [
  {
    id: 501,
    fileName: '第一章-概览.pdf',
    fileType: 'pdf',
    url: '/uploads/cn/ch1.pdf',
    courseId: 4178,
    chapterId: 12,
    uploaderId: 9001,
    uploadTime: '2026-11-15T09:00:00',
    description: '课程导论',
    visibleToAll: false,
  },
  {
    id: 502,
    fileName: '实验手册.docx',
    fileType: 'docx',
    url: '/uploads/cn/lab.docx',
    courseId: 4178,
    chapterId: null,
    uploaderId: 9001,
    uploadTime: '2026-11-16T09:00:00',
    description: null,
    visibleToAll: true,
  },
]
const VERSIONS = [
  {
    id: 1,
    versionNumber: 2,
    fileName: '第一章-概览-v2.pdf',
    isActive: true,
    createdAt: '2026-11-16',
  },
  {
    id: 2,
    versionNumber: 1,
    fileName: '第一章-概览.pdf',
    isActive: false,
    createdAt: '2026-11-15',
  },
]

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function bodyButtonByText(text: string): HTMLButtonElement {
  const button = Array.from(document.querySelectorAll('button')).find((item) =>
    item.textContent?.includes(text),
  )
  if (!button) throw new Error(`未找到按钮：${text}`)
  return button as HTMLButtonElement
}

beforeEach(() => {
  vi.clearAllMocks()
  document.body.innerHTML = ''
  courseApi.getAllCourses.mockResolvedValue(COURSES)
  resourceApi.getResourceList.mockResolvedValue(envelope(RESOURCES))
  resourceApi.toggleVisibility.mockResolvedValue(envelope(null))
  versionApi.getVersionHistory.mockResolvedValue(envelope(VERSIONS))
  versionApi.rollbackVersion.mockResolvedValue(envelope(null))
})

describe('管理端 · 资源库与版本', () => {
  it('自动选中首门课程并读课程资源，读数来自接口', async () => {
    const wrapper = mount(ResourceLibraryPanel)
    await flushPromises()

    expect(courseApi.getAllCourses).toHaveBeenCalledTimes(1)
    expect(resourceApi.getResourceList).toHaveBeenCalledWith(4178)
    expect(wrapper.text()).toContain('第一章-概览.pdf')
    expect(wrapper.text()).toContain('实验手册.docx')
    expect(wrapper.text()).toContain('全员可见')
    expect(wrapper.text()).toContain('需选课/授权')
    expect(wrapper.text()).not.toContain('示例数据')
  })

  it('可见性切换走 PATCH 并刷新资源列表', async () => {
    const wrapper = mount(ResourceLibraryPanel)
    await flushPromises()

    await buttonByText(wrapper, '设为全员')!.trigger('click')
    await flushPromises()

    expect(resourceApi.toggleVisibility).toHaveBeenCalledWith(501, true)
    expect(resourceApi.getResourceList).toHaveBeenCalledTimes(2)
  })

  it('版本历史按资源读取，回滚后重拉版本', async () => {
    const wrapper = mount(ResourceLibraryPanel)
    await flushPromises()

    await buttonByText(wrapper, '版本')!.trigger('click')
    await flushPromises()
    expect(versionApi.getVersionHistory).toHaveBeenCalledWith(501)
    expect(document.body.textContent).toContain('v2')
    expect(document.body.textContent).toContain('当前版本')

    await bodyButtonByText('回滚').click()
    await flushPromises()
    expect(versionApi.rollbackVersion).toHaveBeenCalledWith(2)
    expect(versionApi.getVersionHistory).toHaveBeenCalledTimes(2)
  })

  it('资源加载失败与真空分开，失败不显示「该课程暂无资源」', async () => {
    resourceApi.getResourceList.mockRejectedValueOnce(new Error('network down'))
    const wrapper = mount(ResourceLibraryPanel)
    await flushPromises()

    expect(wrapper.text()).toContain('课程资源加载失败')
    expect(wrapper.text()).not.toContain('该课程暂无资源')
    expect(buttonByText(wrapper, '重试')).toBeTruthy()
  })
})
