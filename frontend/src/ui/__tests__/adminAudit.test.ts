import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * 管理端「操作与登录日志」接真判据（批次 23）。
 *
 * 这一页此前是「待扩展」空态，端点却一直是齐的（`/admin/operation-logs`、
 * `/login-logs`）。接真之后最容易犯的错不是画不出来，而是把两类日志混成一个口径：
 * 操作日志是**写操作留痕**（含未登录时被记录的行），登录日志是**登录尝试**（含失败原因）。
 * 所以断言分三组：请求参数与读数、两类日志各自的字段呈现、失败态互不牵连。
 */
const opApi = vi.hoisted(() => ({
  getOperationLogs: vi.fn(),
  getOperationLogStatistics: vi.fn(),
}))
const loginApi = vi.hoisted(() => ({ getList: vi.fn(), getStats: vi.fn() }))

vi.mock('@/api/operationLog', () => ({ ...opApi, default: opApi }))
vi.mock('@/api/loginLog', () => ({ loginLogApi: loginApi, default: loginApi }))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))

import AuditBody from '../views/specials/AuditBody.vue'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })

function paged(list: unknown[], total: number, pageSize = 15) {
  return {
    list,
    total,
    page: 1,
    pageSize,
    totalPages: Math.max(1, Math.ceil(total / pageSize)),
    hasNext: total > pageSize,
    hasPrevious: false,
  }
}

const OP_ROWS = [
  {
    id: 409834,
    userId: 1,
    username: 'admin',
    module: 'ExamPaper',
    action: 'UPDATE',
    target: 'ExamPaperController.update(..)',
    ip: '127.0.0.1',
    method: 'PUT',
    durationMs: 243,
    status: 'SUCCESS',
    createdAt: '2026-11-04T21:09:13',
  },
  {
    // 真栈实测：登录接口本身也会被记录，此时还没有会话，userId/username 整个缺键
    id: 409835,
    module: 'Auth',
    action: 'CREATE',
    target: 'AuthController.login(..)',
    ip: '0:0:0:0:0:0:0:1',
    method: 'POST',
    durationMs: 88,
    status: 'FAILED',
    errorMsg: '用户名或密码错误',
    createdAt: '2026-11-04T21:09:14',
  },
]

const LOGIN_ROWS = [
  {
    id: 625654,
    userId: 4,
    username: 'student',
    loginStatus: 'SUCCESS',
    ipAddress: '127.0.0.1',
    deviceName: 'Computer',
    browser: 'Chrome 15',
    operatingSystem: 'Windows 10',
    createdAt: '2026-11-04T21:08:00',
  },
  {
    id: 625653,
    userId: 9,
    username: 'ghost',
    // 真栈实测：库内写的是 FAILURE，不是 FAILED（status=FAILED 命中 0 条）
    loginStatus: 'FAILURE',
    ipAddress: '10.0.0.8',
    failureReason: '账号已锁定',
    createdAt: '2026-11-04T21:07:00',
  },
]

function statValues(wrapper: ReturnType<typeof mount>): string[] {
  return wrapper.findAll('.stat-value').map((node) => node.text().replace(/\s+/g, ''))
}

beforeEach(() => {
  vi.clearAllMocks()
  opApi.getOperationLogs.mockResolvedValue(envelope(paged(OP_ROWS, 120)))
  opApi.getOperationLogStatistics.mockResolvedValue(
    envelope({ totalCount: 120, successCount: 110, failedCount: 10 }),
  )
  loginApi.getList.mockResolvedValue(envelope(paged(LOGIN_ROWS, 2)))
  loginApi.getStats.mockResolvedValue(
    envelope({
      totalCount: 5886,
      successCount: 5851,
      failedCount: 35,
      successRate: 99.41,
      todayCount: 161,
      weekCount: 695,
    }),
  )
})

describe('管理端 · 操作日志', () => {
  it('挂载即按服务端分页拉取，KPI 取统计端点而不是从当前页累加', async () => {
    const wrapper = mount(AuditBody)
    await flushPromises()

    expect(opApi.getOperationLogs).toHaveBeenCalledWith(
      expect.objectContaining({ page: 1, size: 15 }),
    )
    expect(opApi.getOperationLogStatistics).toHaveBeenCalledTimes(1)
    // 120 条里当前页只有 2 行——KPI 若按行数累加会显示 2，这里必须显示 120
    expect(statValues(wrapper)).toEqual(['120条', '110条', '10条', '91.7%'])
    expect(wrapper.text()).toContain('用户 ID 1')
    expect(wrapper.text()).toContain('ExamPaper')
    expect(wrapper.text()).toContain('更新（UPDATE）')
    expect(wrapper.text()).toContain('243 ms')
    wrapper.unmount()
  })

  it('未登录时被记录的行如实写「未登录 / 系统」，不渲染成 undefined', async () => {
    const wrapper = mount(AuditBody)
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('未登录 / 系统')
    expect(text).not.toContain('undefined')
    // 失败行要能看到后端给的错误文案，而不是只一个红点
    expect(text).toContain('用户名或密码错误')
    wrapper.unmount()
  })

  it('筛选条件原样传给服务端并回到第 1 页；重置清空四项', async () => {
    const wrapper = mount(AuditBody)
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('admin')
    await inputs[1].setValue('Auth')
    await inputs[2].setValue('CREATE')
    await wrapper.find('select').setValue('FAILED')
    await wrapper.find('form.search-form').trigger('submit')
    await flushPromises()

    expect(opApi.getOperationLogs).toHaveBeenLastCalledWith(
      expect.objectContaining({
        page: 1,
        username: 'admin',
        module: 'Auth',
        action: 'CREATE',
        status: 'FAILED',
      }),
    )

    const reset = wrapper.findAll('button').find((node) => node.text() === '重置')
    await reset!.trigger('click')
    await flushPromises()
    expect(opApi.getOperationLogs).toHaveBeenLastCalledWith(
      expect.objectContaining({
        page: 1,
        username: undefined,
        module: undefined,
        action: undefined,
        status: undefined,
      }),
    )
    wrapper.unmount()
  })

  it('空列表给可执行空态，不画 0 行表', async () => {
    opApi.getOperationLogs.mockResolvedValue(envelope(paged([], 0)))
    opApi.getOperationLogStatistics.mockResolvedValue(
      envelope({ totalCount: 0, successCount: 0, failedCount: 0 }),
    )
    const wrapper = mount(AuditBody)
    await flushPromises()

    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
    expect(wrapper.text()).toContain('暂无操作日志')
    wrapper.unmount()
  })

  it('加载失败给错误与重试，统计失败时 KPI 显示 — 而不是 0', async () => {
    opApi.getOperationLogs.mockRejectedValue(new Error('network'))
    opApi.getOperationLogStatistics.mockRejectedValue(new Error('network'))
    const wrapper = mount(AuditBody)
    await flushPromises()

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('操作日志加载失败')
    expect(wrapper.text()).toContain('无法确认操作日志')
    expect(statValues(wrapper)).toEqual(['—条', '—条', '—条', '—'])
    wrapper.unmount()
  })
})

describe('管理端 · 登录日志', () => {
  it('切到登录页签才拉取，统计口径按后端百分比直接展示（不再乘 100）', async () => {
    const wrapper = mount(AuditBody)
    await flushPromises()
    expect(loginApi.getList).not.toHaveBeenCalled()

    const tab = wrapper.findAll('.tabs button').find((node) => node.text() === '登录日志')
    await tab!.trigger('click')
    await flushPromises()

    expect(loginApi.getList).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 15 }))
    expect(loginApi.getStats).toHaveBeenCalledTimes(1)
    expect(statValues(wrapper)).toEqual(['5,886次', '99.4%', '161次', '695次'])
    expect(wrapper.text()).toContain('student')
    // 失败行必须带后端给的原因
    expect(wrapper.text()).toContain('账号已锁定')
    expect(wrapper.text()).toContain('10.0.0.8')
    wrapper.unmount()
  })

  it('登录日志失败不影响操作日志：切回去仍能看到原有行', async () => {
    loginApi.getList.mockRejectedValue(new Error('500'))
    const wrapper = mount(AuditBody)
    await flushPromises()

    const toLogin = wrapper.findAll('.tabs button').find((node) => node.text() === '登录日志')
    await toLogin!.trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('登录日志加载失败')

    const toOperation = wrapper.findAll('.tabs button').find((node) => node.text() === '操作日志')
    await toOperation!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('ExamPaper')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
