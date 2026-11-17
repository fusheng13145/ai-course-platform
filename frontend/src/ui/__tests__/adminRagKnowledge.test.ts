import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * 管理端「RAG 知识库」接真判据（M-12e）。
 *
 * 文档、索引与课程资料构建全部走 `/rag/*` 六端点；引擎 503 必须显式透出，
 * 不得用空列表或假成功遮住。
 */
const courseApi = vi.hoisted(() => ({ getAllCourses: vi.fn() }))
const ragApi = vi.hoisted(() => ({
  ragListDocuments: vi.fn(),
  ragAddDocument: vi.fn(),
  ragUpdateDocument: vi.fn(),
  ragDeleteDocument: vi.fn(),
  ragBuildIndex: vi.fn(),
  ragClearIndex: vi.fn(),
  ragBuildCourseKnowledgeBase: vi.fn(),
  ragRetrieve: vi.fn(),
}))
const toast = vi.hoisted(() => vi.fn())

vi.mock('@/api/course', () => ({ ...courseApi, default: courseApi }))
vi.mock('@/api/rag', () => ({ ...ragApi, default: ragApi }))
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))

import RagKnowledgePanel from '../views/specials/RagKnowledgePanel.vue'

const COURSES = [{ id: 4178, name: '计算机网络' }]
const DOCS = [
  {
    id: 11,
    document_name: '第 1 章 概述',
    course_id: 4178,
    content: '计算机网络是互联的自治系统集合。',
  },
  { id: 12, document_name: '第 2 章 物理层', course_id: 4178, content: '物理层传输比特流。' },
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
  ragApi.ragListDocuments.mockResolvedValue(DOCS)
  ragApi.ragAddDocument.mockResolvedValue({ id: 13 })
  ragApi.ragUpdateDocument.mockResolvedValue({ id: 11 })
  ragApi.ragDeleteDocument.mockResolvedValue({ id: 11 })
  ragApi.ragBuildIndex.mockResolvedValue({ success: true, indexed_chunks: 49 })
  ragApi.ragClearIndex.mockResolvedValue({ success: true, indexed_chunks: 0 })
  ragApi.ragBuildCourseKnowledgeBase.mockResolvedValue({
    mode: 'LOCAL',
    total: 10,
    added: 8,
    skipped: 2,
    indexedChunks: 49,
  })
  ragApi.ragRetrieve.mockResolvedValue({
    mode: 'ENGINE',
    results: [
      {
        document_name: '第 1 章 概述',
        relevance_score: 0.82,
        snippet: '计算机网络是互联的自治系统集合。',
      },
    ],
  })
})

describe('管理端 · RAG 知识库', () => {
  it('自动选中首门课程并读文档，字符数与名称来自接口', async () => {
    const wrapper = mount(RagKnowledgePanel)
    await flushPromises()

    expect(courseApi.getAllCourses).toHaveBeenCalledTimes(1)
    expect(ragApi.ragListDocuments).toHaveBeenCalledWith(4178)
    expect(wrapper.text()).toContain('第 1 章 概述')
    expect(wrapper.text()).toContain('第 2 章 物理层')
    expect(wrapper.text()).toContain('正文字符')
    expect(wrapper.text()).not.toContain('示例数据')
  })

  it('新增文档写入 courseId/名称/正文并刷新列表', async () => {
    const wrapper = mount(RagKnowledgePanel)
    await flushPromises()

    await buttonByText(wrapper, '新增文档')!.trigger('click')
    await flushPromises()
    const nameInput = document.querySelector('input[aria-label="文档名称"]') as HTMLInputElement
    nameInput.value = '第 3 章 数据链路层'
    nameInput.dispatchEvent(new Event('input'))
    const contentInput = document.querySelector(
      'textarea[aria-label="文档正文"]',
    ) as HTMLTextAreaElement
    contentInput.value = '差错检测与介质访问控制。'
    contentInput.dispatchEvent(new Event('input'))
    await bodyButtonByText('保存').click()
    await flushPromises()

    expect(ragApi.ragAddDocument).toHaveBeenCalledWith(
      4178,
      '第 3 章 数据链路层',
      '差错检测与介质访问控制。',
    )
    expect(ragApi.ragListDocuments).toHaveBeenCalledTimes(2)
  })

  it('编辑走 PUT，删除经确认后走 DELETE 并刷新', async () => {
    const wrapper = mount(RagKnowledgePanel)
    await flushPromises()

    await buttonByText(wrapper, '编辑')!.trigger('click')
    await flushPromises()
    const contentInput = document.querySelector(
      'textarea[aria-label="文档正文"]',
    ) as HTMLTextAreaElement
    contentInput.value = '更新后的正文。'
    contentInput.dispatchEvent(new Event('input'))
    await bodyButtonByText('保存').click()
    await flushPromises()
    expect(ragApi.ragUpdateDocument).toHaveBeenCalledWith(11, '更新后的正文。')

    await buttonByText(wrapper, '删除')!.trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('确认删除')
    await bodyButtonByText('删除').click()
    await flushPromises()
    expect(ragApi.ragDeleteDocument).toHaveBeenCalledWith(11)
  })

  it('从课程资料构建索引并读回片段数；UNAVAILABLE 不伪装成功', async () => {
    const wrapper = mount(RagKnowledgePanel)
    await flushPromises()

    await buttonByText(wrapper, '从课程资料构建')!.trigger('click')
    await flushPromises()
    expect(ragApi.ragBuildCourseKnowledgeBase).toHaveBeenCalledWith(4178)
    expect(wrapper.text()).toContain('49 片段')

    ragApi.ragBuildCourseKnowledgeBase.mockRejectedValueOnce(new Error('RAG 引擎不可用（503）'))
    await buttonByText(wrapper, '从课程资料构建')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('RAG 引擎')
    expect(wrapper.text()).toContain('503')
  })

  it('检索试验走 ragRetrieve，命中片段与分数如实展示', async () => {
    const wrapper = mount(RagKnowledgePanel)
    await flushPromises()

    const input = wrapper.find('input[aria-label="检索问题"]')
    await input.setValue('什么是自治系统')
    await buttonByText(wrapper, '检索')!.trigger('click')
    await flushPromises()

    expect(ragApi.ragRetrieve).toHaveBeenCalledWith({
      query: '什么是自治系统',
      courseId: 4178,
      topK: 5,
    })
    expect(wrapper.text()).toContain('相关度 0.820')
    expect(wrapper.text()).toContain('计算机网络是互联的自治系统集合')
  })

  it('文档加载失败与真空分开，失败不显示「该课程还没有知识库文档」', async () => {
    ragApi.ragListDocuments.mockRejectedValueOnce(new Error('network down'))
    const wrapper = mount(RagKnowledgePanel)
    await flushPromises()

    expect(wrapper.text()).toContain('知识库文档加载失败')
    expect(wrapper.text()).not.toContain('该课程还没有知识库文档')
    expect(buttonByText(wrapper, '重试')).toBeTruthy()
  })
})
