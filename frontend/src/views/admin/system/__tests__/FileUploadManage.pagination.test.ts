import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * FileUploadManage 上传会话客户端分页（D2-5）
 *
 * <p>listSessions 全量返回（无分页参数），长历史下整表渲染。现渲染层客户端
 * 分页（每页 20）：≤20 零变化；超出切片 + 分页器；加载失败双态（D2-1）不受影响。</p>
 */

const mocks = vi.hoisted(() => ({
  listSessions: vi.fn(),
  createSession: vi.fn(),
  uploadChunk: vi.fn(),
  mergeChunks: vi.fn(),
}))

vi.mock('@/api/fileUpload', () => ({
  ...mocks,
  default: {
    listSessions: mocks.listSessions,
    cancelUpload: vi.fn(),
    getProgress: vi.fn(),
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

function makeSessions(n) {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    fileName: `文件${i + 1}.pdf`,
    fileSize: 1024,
    progress: 100,
    status: 'COMPLETED',
    createdAt: '2026-10-15 10:00',
  }))
}

function mountPage() {
  return mount(FileUploadManage, {
    shallow: true,
    global: {
      stubs: {
        'el-table': {
          props: ['data'],
          template: '<div class="tbl-stub">{{ (data || []).length }}</div>',
        },
        'el-table-column': true,
        // element-plus 整体被 mock 掉了，列插槽内真实渲染的这四个控件需要显式桩，
        // 否则每次重渲染都刷 "Failed to resolve component" 警告
        'el-button': { template: '<button><slot /></button>' },
        'el-icon': { template: '<i><slot /></i>' },
        'el-tag': { template: '<span><slot /></span>' },
        'el-progress': { props: ['percentage'], template: '<div class="progress-stub" />' },
        'el-input-number': {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<div class="num-stub" />',
        },
        'el-alert': {
          props: ['title'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        'el-pagination': {
          props: ['total'],
          emits: ['current-change'],
          template: '<div class="pager-stub" :data-total="total" />',
        },
        EmptyState: { props: ['title'], template: '<div class="empty-stub">{{ title }}</div>' },
        PageHeader: { template: '<div />' },
        StatCard: { template: '<div />' },
        // 批 3b 迁移后弹层由 ui/Modal 承载；原位渲染插槽的桩与原 el-dialog 桩等价
        Modal: { template: '<div><slot /></div>' },
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('FileUploadManage — D2-5 客户端分页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('会话 ≤20：全量渲染且无分页器', async () => {
    mocks.listSessions.mockResolvedValue({ data: makeSessions(14) })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('.tbl-stub').text()).toBe('14')
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
  })

  it('会话 45（长历史）：首页 20 行且分页器 total=45', async () => {
    mocks.listSessions.mockResolvedValue({ data: makeSessions(45) })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('.tbl-stub').text()).toBe('20')
    expect(wrapper.get('.pager-stub').attributes('data-total')).toBe('45')
  })

  it('加载失败：D2-1 双态不受分页影响（无分页器、有失败提示）', async () => {
    mocks.listSessions.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.pager-stub').exists()).toBe(false)
    expect(wrapper.find('.alert-stub').exists()).toBe(true)
  })
})
