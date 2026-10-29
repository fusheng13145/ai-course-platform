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
  useV2Ui: () => ({ toast }),
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

describe('v2 学生阅读列 · 标记完成', () => {
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
