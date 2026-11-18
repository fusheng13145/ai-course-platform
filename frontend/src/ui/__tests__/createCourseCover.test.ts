import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * F-3：教师建课页的封面预览地址过协议白名单。
 * `form.coverImage` 取自 POST /upload 的响应字段，属外部可控输入，
 * 直接绑进 `:src` 就是 javascript:/data: 的落点，故改为经 utils/safeUrl。
 */
const uploadFile = vi.hoisted(() => vi.fn())
const getSemesters = vi.hoisted(() => vi.fn())
const createCourse = vi.hoisted(() => vi.fn())
const getCourseDefinitions = vi.hoisted(() =>
  vi.fn(async () => ({ data: { list: [], records: [] } })),
)
const createCourseDefinition = vi.hoisted(() => vi.fn(async () => ({ data: { id: 55 } })))
const push = vi.hoisted(() => vi.fn())
const toast = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/api/common', () => ({ default: { uploadFile, getSemesters } }))
vi.mock('@/api/course', () => ({ createCourse, createCourseDefinition, getCourseDefinitions }))
vi.mock('../composables/ui', async () => {
  const { ref } = await import('vue')
  return { useUi: () => ({ toast, role: ref('teacher') }) }
})

import CreateCourseView from '../views/teacher/CreateCourseView.vue'

function mountView() {
  return mount(CreateCourseView, { global: { stubs: { PageHead: true } } })
}

/** 走真实的 input change 路径：文件名/类型/大小三道前置校验都在这条路上 */
async function uploadCover(url: unknown) {
  uploadFile.mockResolvedValue(url === null ? { data: {} } : { data: { url } })
  const wrapper = mountView()
  const input = wrapper.find('input[type="file"]')
  Object.defineProperty(input.element, 'files', {
    value: [new File(['x'], 'cover.png', { type: 'image/png' })],
    configurable: true,
  })
  await input.trigger('change')
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  getSemesters.mockResolvedValue({
    data: { list: [{ id: 2, name: '2026-2027-1', status: 'ACTIVE' }] },
  })
  getCourseDefinitions.mockResolvedValue({ data: { list: [], records: [] } })
  createCourseDefinition.mockResolvedValue({ data: { id: 55 } })
})

describe('建课页 · 封面地址协议白名单', () => {
  it('happy：http(s) 绝对地址原样渲染进 img', async () => {
    const wrapper = await uploadCover('https://cdn.example.com/cover.png')
    expect(uploadFile).toHaveBeenCalledTimes(1)
    const img = wrapper.find('img.cover-img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('https://cdn.example.com/cover.png')
    expect(wrapper.text()).not.toContain('协议不受支持')
    wrapper.unmount()
  })

  it('边界：接口回相对地址时按当前源解析，仍是 http(s)；缺字段仍是原有报错', async () => {
    const wrapper = await uploadCover('/uploads/cover.png')
    expect(wrapper.find('img.cover-img').attributes('src')).toBe(
      `${window.location.origin}/uploads/cover.png`,
    )
    wrapper.unmount()

    const missing = await uploadCover(null)
    expect(missing.find('img.cover-img').exists()).toBe(false)
    expect(missing.text()).toContain('上传失败：响应缺少文件地址')
    missing.unmount()
  })

  it('恶意：javascript:／data: 之类的封面地址不进 DOM，且如实告知被拒', async () => {
    for (const evil of [
      'javascript:alert(1)',
      'JaVaScRiPt:alert(1)',
      'data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==',
      'file:///etc/passwd',
    ]) {
      const wrapper = await uploadCover(evil)
      const img = wrapper.find('img.cover-img')
      expect(img.exists(), evil).toBe(false)
      expect(wrapper.text(), evil).toContain('封面地址协议不受支持')
      wrapper.unmount()
    }
  })

  it('封面写入课程定义，开课任务只带运行字段', async () => {
    const wrapper = await uploadCover('https://cdn.example.com/cover.png')
    const nameField = wrapper
      .findAll('label.field')
      .find((node) => node.text().includes('课程名称'))!
    const codeField = wrapper
      .findAll('label.field')
      .find((node) => node.text().includes('课程编码'))!
    await nameField.find('input').setValue('计算机网络')
    await codeField.find('input').setValue('CN101')
    const categoryField = wrapper
      .findAll('label.field')
      .find((node) => node.text().includes('课程分类'))!
    await categoryField.find('select').setValue('编程开发')

    await wrapper.find('.form-foot .btn-primary').trigger('click')
    await flushPromises()

    expect(createCourseDefinition).toHaveBeenCalledWith(
      expect.objectContaining({
        name: '计算机网络',
        code: 'CN101',
        category: '编程开发',
        coverImage: 'https://cdn.example.com/cover.png',
      }),
    )
    expect(createCourse).toHaveBeenCalledTimes(1)
    expect(createCourse.mock.calls[0][0]).toMatchObject({
      definitionId: 55,
      semesterId: 2,
      plannedSections: 1,
      maxStudents: 100,
      enrollmentType: 'FREE',
    })
    wrapper.unmount()
  })
})
