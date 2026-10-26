import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * FileUploadManage 的「本地文件分片上传」入口（DR-69 = A 验收线①②③④的页面半边）
 *
 * <p>组合逻辑的契约由 useChunkedUpload.test.ts 钉住；这里只钉页面确实把
 * 分片大小/阈值当作参数传了下去、成功后给出可直链访问的合并产物、
 * 以及失败后把「按磁盘真值续传」这个动作露出来。
 * 若入口把魔数写死在组件里，或成功后不回报产物位置，这几条会当场变红。</p>
 */

const mocks = vi.hoisted(() => ({
  listSessions: vi.fn(),
  createSession: vi.fn(),
  uploadChunk: vi.fn(),
  mergeChunks: vi.fn(),
  cancelUpload: vi.fn(),
  getProgress: vi.fn(),
}))

vi.mock('@/api/fileUpload', () => ({
  ...mocks,
  default: mocks,
}))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  }),
  ElMessageBox: { confirm: vi.fn() },
}))
vi.mock('@/utils/dangerConfirm', () => ({
  confirmDanger: vi.fn().mockResolvedValue(false),
}))

import FileUploadManage from '@/views/admin/system/FileUploadManage.vue'
import { ElMessage } from 'element-plus'

const MB = 1024 * 1024

function mountPage() {
  return mount(FileUploadManage, {
    shallow: true,
    global: {
      stubs: {
        'el-table': { props: ['data'], template: '<div class="tbl-stub" />' },
        'el-table-column': true,
        'el-button': { template: '<button><slot /></button>' },
        'el-icon': { template: '<i><slot /></i>' },
        'el-tag': { template: '<span><slot /></span>' },
        'el-alert': { props: ['title'], template: '<div class="alert-stub">{{ title }}</div>' },
        'el-pagination': { props: ['total'], template: '<div class="pager-stub" />' },
        'el-progress': { props: ['percentage'], template: '<div class="progress-stub" />' },
        'el-input-number': {
          props: ['modelValue', 'size'],
          emits: ['update:modelValue'],
          // type=text 而非 number：页面带 size="small"，number input 上该属性非法
          template:
            '<input class="num-stub" type="text" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
        },
        // 批 3b 迁移后弹层由 ui/Modal 承载；原位渲染插槽的桩与原 el-dialog 桩等价
        Modal: { template: '<div class="dialog-stub"><slot /></div>' },
        EmptyState: { props: ['title'], template: '<div class="empty-stub" />' },
        PageHeader: { template: '<div />' },
        StatCard: { template: '<div />' },
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

/** 把 jsdom 里无法通过 setValue 赋值的 input[files] 塞进去，再触发 change */
async function pickFile(wrapper, file) {
  const input = wrapper.find('input[type="file"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

/** 页面上的两个旋钮（模板顺序：分片大小、切片阈值）；桩是真 input，走 v-model */
async function setKnob(wrapper, i, mb) {
  const knobs = wrapper.findAll('input.num-stub')
  expect(knobs.length).toBe(2)
  await knobs[i].setValue(String(mb))
}

function findButtonByText(wrapper, text) {
  return wrapper.findAll('button').find((b) => b.text().trim() === text)
}

/** 主按钮在失败后会改口；文案在卡片说明里也出现过，所以只能按按钮判定 */
function startButton(wrapper) {
  return findButtonByText(wrapper, '开始上传') || findButtonByText(wrapper, '按磁盘真值续传')
}

/**
 * 等到链路稳定再断言。
 * 每片 MD5 走 Blob → FileReader，其回调是宏任务；单次 flushPromises 只够推进一片，
 * 会把「跑到第几片」变成测试时序的函数。改成按谓词轮询，与片数无关。
 */
async function settle(done: () => boolean, timeout = 5000) {
  const start = Date.now()
  while (!done()) {
    if (Date.now() - start > timeout) throw new Error('上传链路未在预期时间内收敛')
    await new Promise((r) => setTimeout(r, 10))
    await flushPromises()
  }
  await flushPromises()
}

function bigFile(name = '讲义.pdf', sizeMb = 2.5) {
  const bytes = new Uint8Array(Math.round(sizeMb * MB))
  let s = 11
  for (let i = 0; i < bytes.length; i++) {
    s = (s * 1103515245 + 12345) & 0x7fffffff
    bytes[i] = s % 251
  }
  return new File([bytes], name, { type: 'application/pdf', lastModified: 1730000000000 })
}

function wireHappyPath() {
  mocks.createSession.mockResolvedValue({ data: { id: 7, status: 'UPLOADING' } })
  mocks.uploadChunk.mockImplementation((sid, index) =>
    Promise.resolve({ data: { id: sid, uploadedChunks: index + 1 } }),
  )
  mocks.mergeChunks.mockResolvedValue({
    data: { id: 7, status: 'COMPLETED', uploadPath: '2026/10/9f3ac_讲义.pdf' },
  })
}

/** 点「开始上传」并等合并发生 */
async function clickStart(wrapper) {
  await startButton(wrapper).trigger('click')
  await settle(() => mocks.mergeChunks.mock.calls.length > 0)
}

/** 点主按钮并等它改口为「按磁盘真值续传」（失败态出口） */
async function clickStartAndWaitFailure(wrapper) {
  await startButton(wrapper).trigger('click')
  await settle(() => startButton(wrapper)?.text().trim() === '按磁盘真值续传')
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  mocks.listSessions.mockResolvedValue({ data: [] })
  wireHappyPath()
})

describe('入口把旋钮当参数传下去（验收线①）', () => {
  it('分片大小 1MB + 阈值 1MB：2.5MB 文件切成 3 片并报给 createSession', async () => {
    const wrapper = mountPage()
    await settle(() => mocks.listSessions.mock.calls.length > 0)
    await pickFile(wrapper, bigFile('讲义.pdf', 2.5))
    await setKnob(wrapper, 0, 1)
    await setKnob(wrapper, 1, 1)
    await clickStart(wrapper)

    expect(mocks.createSession).toHaveBeenCalledWith({
      filename: '讲义.pdf',
      fileSize: Math.round(2.5 * MB),
      totalChunks: 3,
    })
    expect(mocks.uploadChunk).toHaveBeenCalledTimes(3)
  })

  it('同一份文件把分片大小调到 2MB：片数随之变成 2（旋钮不是写死的魔数）', async () => {
    const wrapper = mountPage()
    await settle(() => mocks.listSessions.mock.calls.length > 0)
    await pickFile(wrapper, bigFile('讲义.pdf', 2.5))
    await setKnob(wrapper, 0, 2)
    await setKnob(wrapper, 1, 1)
    await clickStart(wrapper)

    expect(mocks.createSession).toHaveBeenCalledWith(expect.objectContaining({ totalChunks: 2 }))
    expect(mocks.uploadChunk).toHaveBeenCalledTimes(2)
  })
})

describe('入口的报文与产物回报（验收线②④）', () => {
  it('每片 FormData 带 file 与 32 位十六进制 md5', async () => {
    const wrapper = mountPage()
    await settle(() => mocks.listSessions.mock.calls.length > 0)
    await pickFile(wrapper, bigFile('讲义.pdf', 2.5))
    await setKnob(wrapper, 0, 1)
    await setKnob(wrapper, 1, 1)
    await clickStart(wrapper)

    expect(mocks.uploadChunk).toHaveBeenCalledTimes(3)
    for (let i = 0; i < 3; i++) {
      const [sid, index, form] = mocks.uploadChunk.mock.calls[i]
      expect(sid).toBe(7)
      expect(index).toBe(i)
      expect(form).toBeInstanceOf(FormData)
      expect(form.get('file')).toBeInstanceOf(Blob)
      expect(String(form.get('md5'))).toMatch(/^[0-9a-f]{32}$/)
    }
  })

  it('合并成功后给出可直链访问的 /api/uploads 地址，并刷新会话列表', async () => {
    const wrapper = mountPage()
    await settle(() => mocks.listSessions.mock.calls.length > 0)
    await pickFile(wrapper, bigFile('讲义.pdf', 1))
    await clickStart(wrapper)
    await settle(() => mocks.listSessions.mock.calls.length >= 2)

    const result = wrapper.find('[data-testid="upload-result"]')
    expect(result.exists()).toBe(true)
    expect(result.find('a').attributes('href')).toBe('/api/uploads/2026/10/9f3ac_讲义.pdf')
  })
})

describe('入口的续传出口（验收线③）', () => {
  it('合并被服务端拒绝后，主按钮改口为「按磁盘真值续传」并给出动作口径', async () => {
    mocks.mergeChunks.mockRejectedValueOnce(new Error('分片未全部上传完成，缺失索引：[2]'))
    const wrapper = mountPage()
    await settle(() => mocks.listSessions.mock.calls.length > 0)
    await pickFile(wrapper, bigFile('讲义.pdf', 1))
    await clickStartAndWaitFailure(wrapper)

    expect(ElMessage.error).toHaveBeenCalledWith(
      '上传未完成，可点「按磁盘真值续传」补齐缺失分片',
    )
    expect(wrapper.find('[data-testid="upload-result"]').exists()).toBe(false)
  })

  it('续传按服务端报告的缺失分片补片，不重建会话', async () => {
    const wrapper = mountPage()
    await settle(() => mocks.listSessions.mock.calls.length > 0)
    const file = bigFile('讲义.pdf', 2.5)
    await pickFile(wrapper, file)
    await setKnob(wrapper, 0, 1)
    await setKnob(wrapper, 1, 1)
    mocks.mergeChunks.mockRejectedValueOnce(new Error('缺少分片'))
    await clickStartAndWaitFailure(wrapper)

    mocks.getProgress.mockResolvedValue({
      data: { uploaded: 1, total: 3, status: 'UPLOADING', missingIndexes: [1, 2] },
    })
    mocks.uploadChunk.mockClear()
    mocks.createSession.mockClear()

    await startButton(wrapper).trigger('click')
    await settle(() => mocks.uploadChunk.mock.calls.length === 2)

    expect(mocks.createSession).not.toHaveBeenCalled()
    expect(mocks.getProgress).toHaveBeenCalledWith(7)
    expect(mocks.uploadChunk.mock.calls.map((c) => c[1])).toEqual([1, 2])
    expect(mocks.mergeChunks).toHaveBeenCalledTimes(2)
  })
})
