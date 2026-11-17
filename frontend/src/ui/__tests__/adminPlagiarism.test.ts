import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * 管理端「查重管理」接真判据（M-12d）。
 *
 * 管理端列表走裸 `GET /plagiarism-checks`（后端 LIMIT 500），不是学生维度的
 * `/student/{id}`；分页是客户端分页，不伪造服务端 page/size。执行与详情各自独立。
 */
const plagiarismApi = vi.hoisted(() => ({
  getCheckHistory: vi.fn(),
  executeCheck: vi.fn(),
  getCheckDetail: vi.fn(),
  getCheckMatches: vi.fn(),
}))
const toast = vi.hoisted(() => vi.fn())

vi.mock('@/api/plagiarismCheck', () => ({ ...plagiarismApi, default: plagiarismApi }))
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))

import PlagiarismPanel from '../views/specials/PlagiarismPanel.vue'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })

const ROWS = [
  {
    id: 901,
    taskId: 301,
    submissionId: 701,
    studentId: 1001,
    checkType: 'TEXT',
    status: 'PENDING',
    similarityScore: 0,
    highSimilarityCount: 0,
    checkedAt: null,
  },
  {
    id: 902,
    taskId: 302,
    submissionId: 702,
    studentId: 1002,
    checkType: 'TEXT',
    status: 'COMPLETED',
    similarityScore: 0.42,
    highSimilarityCount: 3,
    resultSummary: '与 1 份提交高度相似',
    checkedAt: '2026-11-16T10:00:00',
  },
]

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

beforeEach(() => {
  vi.clearAllMocks()
  plagiarismApi.getCheckHistory.mockResolvedValue(envelope(ROWS))
  plagiarismApi.executeCheck.mockResolvedValue(envelope(null))
  plagiarismApi.getCheckDetail.mockResolvedValue(
    envelope({
      status: 'COMPLETED',
      similarityScore: 0.42,
      highSimilarityCount: 3,
      resultSummary: '与 1 份提交高度相似',
      matches: [
        {
          id: 1,
          matchedSubmissionId: 705,
          matchedSource: '提交 #705',
          similarity: 0.82,
          originalText: '原始段落',
          matchedText: '相似段落',
        },
      ],
    }),
  )
  plagiarismApi.getCheckMatches.mockResolvedValue(envelope([]))
})

describe('管理端 · 查重管理', () => {
  it('挂载即读全部查重任务，按真实状态渲染 KPI 与列表', async () => {
    const wrapper = mount(PlagiarismPanel)
    await flushPromises()

    expect(plagiarismApi.getCheckHistory).toHaveBeenCalledTimes(1)
    expect(plagiarismApi.getCheckHistory).toHaveBeenCalledWith()
    expect(wrapper.text()).toContain('提交 #701')
    expect(wrapper.text()).toContain('提交 #702')
    expect(wrapper.text()).toContain('待检测')
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('42.00%')
    expect(wrapper.text()).not.toContain('示例数据')
  })

  it('执行查重走 execute 端点并刷新列表', async () => {
    const wrapper = mount(PlagiarismPanel)
    await flushPromises()

    await buttonByText(wrapper, '执行')!.trigger('click')
    await flushPromises()

    expect(plagiarismApi.executeCheck).toHaveBeenCalledWith(901)
    expect(plagiarismApi.getCheckHistory).toHaveBeenCalledTimes(2)
  })

  it('详情按任务读 result/matches，空匹配不伪造数据', async () => {
    const wrapper = mount(PlagiarismPanel)
    await flushPromises()

    await buttonByText(wrapper, '详情')!.trigger('click')
    await flushPromises()

    expect(plagiarismApi.getCheckDetail).toHaveBeenCalledWith(901)
    expect(document.body.textContent).toContain('相似度 42.00%')
    expect(document.body.textContent).toContain('原始段落')
    expect(document.body.textContent).toContain('相似段落')
  })

  it('列表失败与真空分开，失败时不渲染「暂无查重任务」', async () => {
    plagiarismApi.getCheckHistory.mockRejectedValueOnce(new Error('network down'))
    const wrapper = mount(PlagiarismPanel)
    await flushPromises()

    expect(wrapper.text()).toContain('查重任务加载失败')
    expect(wrapper.text()).not.toContain('暂无查重任务')
    expect(buttonByText(wrapper, '重试')).toBeTruthy()
  })
})
