import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

/**
 * M-28：题库批量导入导出的 EP-free 共享件。
 *
 * <p>旧端与 共用 `utils/questionTransfer`；这里钉住三件事：
 * 导出按当前课程筛选并落成 xlsx blob 下载、导入以 multipart FormData 提交后刷新列表、
 * 宿主（loading/close/success/error）在成功与失败路径上都被成对调用。</p>
 */

const mocks = vi.hoisted(() => ({
  exportQuestionsExcel: vi.fn(),
  importQuestionsExcel: vi.fn(),
}))

vi.mock('@/api/question', () => ({
  default: {
    exportQuestionsExcel: mocks.exportQuestionsExcel,
    importQuestionsExcel: mocks.importQuestionsExcel,
  },
}))

vi.mock('@/utils/logger', () => ({
  default: { log: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
}))

import { createQuestionTransfer } from '@/utils/questionTransfer'

const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

function makeNotify() {
  return {
    loading: vi.fn(),
    close: vi.fn(),
    success: vi.fn(),
    error: vi.fn(),
  }
}

function makeTransfer(courseId: string | number = '') {
  const filters: { courseId: string | number } = { courseId }
  const fetchQuestions = vi.fn()
  const notify = makeNotify()
  const transfer = createQuestionTransfer({ filters, fetchQuestions, notify })
  return { transfer, filters, fetchQuestions, notify }
}

/** document.createElement 被替换前保存原生实现，供非 input 标签回落到真实元素 */
const originalCreateElement = document.createElement.bind(document)

type FakeFileInput = {
  type: string
  accept: string
  onchange: (e: unknown) => Promise<void> | undefined
  click: () => unknown
}

/** 由测试驱动的假文件选择框（jsdom 无法弹出真实对话框） */
let fakeInput: FakeFileInput | null = null

function stubFilePicker(pick: File | null) {
  const input: FakeFileInput = {
    type: '',
    accept: '',
    onchange: () => Promise.resolve(),
    click() {
      return Promise.resolve(input.onchange({ target: { files: pick ? [pick] : [] } }))
    },
  }
  fakeInput = input
  return input
}

beforeEach(() => {
  vi.clearAllMocks()
  fakeInput = null
  vi.spyOn(document, 'createElement').mockImplementation((tag: string) =>
    tag === 'input' && fakeInput
      ? (fakeInput as unknown as HTMLElement)
      : originalCreateElement(tag),
  )
  window.URL.createObjectURL = vi.fn(() => 'blob:mock')
  window.URL.revokeObjectURL = vi.fn()
  vi.spyOn(window.HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('questionTransfer — 导出', () => {
  it('按当前课程筛选请求 xlsx 文件流，并触发下载与成功提示', async () => {
    mocks.exportQuestionsExcel.mockResolvedValue({ data: new Blob(['x']) })
    const { transfer, notify } = makeTransfer('5')

    await transfer.handleExport()

    expect(notify.loading).toHaveBeenCalledWith('正在导出题库...')
    expect(mocks.exportQuestionsExcel).toHaveBeenCalledWith({ courseId: '5' })
    expect(notify.close).toHaveBeenCalled()
    const blob = (window.URL.createObjectURL as unknown as ReturnType<typeof vi.fn>).mock
      .calls[0][0] as Blob
    expect(blob).toBeInstanceOf(Blob)
    expect(blob.type).toBe(XLSX_MIME)
    expect(window.HTMLAnchorElement.prototype.click).toHaveBeenCalled()
    expect(notify.success).toHaveBeenCalledWith('导出成功')
    expect(transfer.busy.value).toBe(false)
  })

  it('未选课程时导出全部（不把空串当筛选条件）', async () => {
    mocks.exportQuestionsExcel.mockResolvedValue({ data: new Blob(['x']) })
    const { transfer } = makeTransfer('')

    await transfer.handleExport()

    expect(mocks.exportQuestionsExcel).toHaveBeenCalledWith({ courseId: undefined })
  })

  it('导出失败也关闭常驻提示，并给出可重试文案', async () => {
    mocks.exportQuestionsExcel.mockRejectedValue(new Error('500'))
    const { transfer, notify } = makeTransfer()

    await transfer.handleExport()

    expect(notify.close).toHaveBeenCalled()
    expect(notify.error).toHaveBeenCalledWith('导出失败，请稍后重试')
    expect(notify.success).not.toHaveBeenCalled()
    expect(transfer.busy.value).toBe(false)
  })

  it('导出进行中 busy 为真，重复点击不会重复请求', async () => {
    let resolveExport!: (value: unknown) => void
    mocks.exportQuestionsExcel.mockReturnValue(
      new Promise((resolve) => {
        resolveExport = resolve
      }),
    )
    const { transfer } = makeTransfer()

    const first = transfer.handleExport()
    expect(transfer.busy.value).toBe(true)
    await transfer.handleExport()
    expect(mocks.exportQuestionsExcel).toHaveBeenCalledTimes(1)

    resolveExport({ data: new Blob(['x']) })
    await first
    expect(transfer.busy.value).toBe(false)
  })
})

describe('questionTransfer — 导入', () => {
  it('以 8 列表头约定的 .xlsx 走 multipart 上传，成功后提示并刷新列表', async () => {
    mocks.importQuestionsExcel.mockResolvedValue({ data: { importedCount: 7, total: 9 } })
    const picker = stubFilePicker(new File(['x'], '题库.xlsx'))
    const { transfer, notify, fetchQuestions } = makeTransfer('9')

    transfer.handleImport()
    await flushPromises()

    expect(picker.accept).toBe('.xlsx')
    const [formData] = mocks.importQuestionsExcel.mock.calls[0] as [FormData]
    expect(formData).toBeInstanceOf(FormData)
    expect((formData.get('file') as File).name).toBe('题库.xlsx')
    expect(formData.get('courseId')).toBe('9')
    expect(notify.loading).toHaveBeenCalledWith('正在导入题目...')
    expect(notify.close).toHaveBeenCalled()
    expect(notify.success).toHaveBeenCalledWith('导入完成：成功 7 / 共 9 题')
    expect(fetchQuestions).toHaveBeenCalled()
    expect(transfer.busy.value).toBe(false)
  })

  it('未筛课程时 FormData 不带 courseId；后端缺省计数回落为 0', async () => {
    mocks.importQuestionsExcel.mockResolvedValue({ data: {} })
    stubFilePicker(new File(['x'], 'all.xlsx'))
    const { transfer, notify } = makeTransfer('')

    transfer.handleImport()
    await flushPromises()

    const [formData] = mocks.importQuestionsExcel.mock.calls[0] as [FormData]
    expect(formData.has('courseId')).toBe(false)
    expect(notify.success).toHaveBeenCalledWith('导入完成：成功 0 / 共 0 题')
  })

  it('未选择文件时不发导入请求也不弹提示', async () => {
    stubFilePicker(null)
    const { transfer, notify } = makeTransfer()

    transfer.handleImport()
    await flushPromises()

    expect(mocks.importQuestionsExcel).not.toHaveBeenCalled()
    expect(notify.loading).not.toHaveBeenCalled()
  })

  it('导入失败关闭常驻提示，给出格式要求且不刷新列表', async () => {
    mocks.importQuestionsExcel.mockRejectedValue(new Error('400'))
    stubFilePicker(new File(['x'], 'bad.xlsx'))
    const { transfer, notify, fetchQuestions } = makeTransfer()

    transfer.handleImport()
    await flushPromises()

    expect(notify.close).toHaveBeenCalled()
    expect(notify.error).toHaveBeenCalledWith('导入失败，请检查文件格式（.xlsx，8 列表头）')
    expect(fetchQuestions).not.toHaveBeenCalled()
    expect(transfer.busy.value).toBe(false)
  })

  it('导入进行中 busy 为真，解析完成后复位', async () => {
    let resolveImport!: (value: unknown) => void
    mocks.importQuestionsExcel.mockReturnValue(
      new Promise((resolve) => {
        resolveImport = resolve
      }),
    )
    stubFilePicker(new File(['x'], '题库.xlsx'))
    const { transfer } = makeTransfer()

    transfer.handleImport()
    await flushPromises()
    expect(transfer.busy.value).toBe(true)

    resolveImport({ data: { importedCount: 1, total: 1 } })
    await flushPromises()
    expect(transfer.busy.value).toBe(false)
  })
})
