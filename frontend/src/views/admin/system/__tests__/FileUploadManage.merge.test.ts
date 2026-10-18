import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { computed, h, inject, provide, toRef } from 'vue'

/**
 * FileUploadManage 分片合并接线（D1-2 批次 2）
 *
 * <p>N-03 下线期挂的「文件分片上传与合并当前未启用」提示条必须撤除；
 * UPLOADING 行给出「合并」入口并真正调用 mergeChunks；进度弹窗展示服务端
 * 磁盘真值 missingIndexes。合并失败走 request.ts 拦截器 + 本页兜底文案，
 * 不得把英文 axios 文案透出。</p>
 */

const mocks = vi.hoisted(() => ({
  listSessions: vi.fn(),
  createSession: vi.fn(),
  uploadChunk: vi.fn(),
  mergeChunks: vi.fn(),
  cancelUpload: vi.fn(),
  getProgress: vi.fn(),
}))

// 页面用默认导出、useChunkedUpload 用命名导出，两种口径都要给，否则组合逻辑抓到 undefined
vi.mock('@/api/fileUpload', () => ({
  ...mocks,
  default: {
    listSessions: mocks.listSessions,
    mergeChunks: mocks.mergeChunks,
    cancelUpload: mocks.cancelUpload,
    getProgress: mocks.getProgress,
  },
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
vi.mock('@/composables/useToast', () => ({ toast: { show: vi.fn() } }))

import FileUploadManage from '@/views/admin/system/FileUploadManage.vue'
import { ElMessage } from 'element-plus'

// el-table 把行数据透传给列插槽，让「操作」列的按钮在 DOM 中真实可点
const TableStub = {
  name: 'ElTable',
  props: ['data'],
  setup(props, { slots }) {
    provide('stubRows', toRef(props, 'data'))
    return () => h('div', { class: 'tbl-stub' }, slots.default?.())
  },
}
const ColumnStub = {
  name: 'ElTableColumn',
  setup(_, { slots }) {
    const rows = inject('stubRows', computed(() => []))
    return () =>
      h(
        'div',
        { class: 'col-stub' },
        (rows.value || []).map((row, index) => slots.default?.({ row, $index: index })),
      )
  },
}

function makeSessions() {
  return [
    {
      id: 7,
      fileName: '进行中.pdf',
      fileSize: 2048,
      totalChunks: 4,
      uploadedChunks: 2,
      progress: 50,
      status: 'UPLOADING',
      createdAt: '2026-10-17 09:00',
    },
    {
      id: 8,
      fileName: '已取消.pdf',
      fileSize: 1024,
      totalChunks: 2,
      uploadedChunks: 1,
      progress: 50,
      status: 'CANCELLED',
      createdAt: '2026-10-17 09:05',
    },
  ]
}

function mountPage() {
  return mount(FileUploadManage, {
    shallow: true,
    global: {
      stubs: {
        'el-table': TableStub,
        'el-table-column': ColumnStub,
        'el-button': { template: '<button><slot /></button>' },
        'el-icon': { template: '<i><slot /></i>' },
        'el-tag': { template: '<span><slot /></span>' },
        'el-alert': { props: ['title'], template: '<div class="alert-stub">{{ title }}</div>' },
        'el-pagination': { props: ['total'], template: '<div class="pager-stub" />' },
        'el-progress': { props: ['percentage'], template: '<div class="progress-stub" />' },
        'el-input-number': {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<div class="num-stub" />',
        },
        'el-dialog': { template: '<div class="dialog-stub"><slot /></div>' },
        EmptyState: { props: ['title'], template: '<div class="empty-stub">{{ title }}</div>' },
        PageHeader: { props: ['subtitle'], template: '<div class="ph-stub">{{ subtitle }}</div>' },
        StatCard: { template: '<div />' },
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

function findButtonByText(wrapper, text) {
  return wrapper.findAll('button').find((b) => b.text().trim() === text)
}

describe('FileUploadManage — D1-2 批次 2 合并接线', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.listSessions.mockResolvedValue({ data: makeSessions() })
  })

  it('能力提示条已撤除', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).not.toContain('当前未启用')
    expect(wrapper.findAll('.alert-stub').length).toBe(0)
  })

  it('UPLOADING 行的「合并」调用 mergeChunks(会话ID)', async () => {
    mocks.mergeChunks.mockResolvedValue({ data: { id: 7, status: 'COMPLETED' } })
    const wrapper = mountPage()
    await flushPromises()

    const mergeBtn = findButtonByText(wrapper, '合并')
    expect(mergeBtn).toBeTruthy()
    await mergeBtn.trigger('click')
    await flushPromises()

    expect(mocks.mergeChunks).toHaveBeenCalledWith(7)
    expect(ElMessage.success).toHaveBeenCalledWith('会话 7 的分片已合并')
  })

  it('合并失败只给中文兜底文案，不透出 axios 英文文案', async () => {
    mocks.mergeChunks.mockRejectedValue(new Error('Request failed with status code 400'))
    const wrapper = mountPage()
    await flushPromises()

    await findButtonByText(wrapper, '合并').trigger('click')
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('合并失败，请稍后重试')
  })

  it('进度弹窗展示磁盘真值 missingIndexes', async () => {
    mocks.getProgress.mockResolvedValue({
      data: { uploaded: 2, total: 4, percentage: '50.0%', status: 'CANCELLED', missingIndexes: [1, 3] },
    })
    const wrapper = mountPage()
    await flushPromises()

    await findButtonByText(wrapper, '查看进度').trigger('click')
    await flushPromises()

    expect(wrapper.get('.progress-missing').text()).toContain('缺失分片序号：1、3')
  })
})
