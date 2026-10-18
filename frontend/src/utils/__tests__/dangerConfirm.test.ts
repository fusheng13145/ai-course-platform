import { describe, it, expect, vi, beforeEach } from 'vitest'
import { buildDangerMessage, confirmDanger, DEFAULT_RECOVERY } from '../dangerConfirm'

const mocks = vi.hoisted(() => ({
  confirm: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: mocks.confirm },
}))

describe('buildDangerMessage', () => {
  it('默认不可逆：含影响范围 + 不可恢复 + 默认恢复路径', () => {
    const msg = buildDangerMessage({
      verb: '删除',
      target: '试卷',
      subject: 'TCP 三次握手',
      impact: '将一并移除该试卷关联的学生作答记录与成绩明细',
    })
    expect(msg).toBe(
      '确定删除试卷「TCP 三次握手」？将一并移除该试卷关联的学生作答记录与成绩明细，' +
        `此操作不可恢复；${DEFAULT_RECOVERY}。`,
    )
  })

  it('无 subject 时不出现书名号', () => {
    const msg = buildDangerMessage({ verb: '删除', target: '通知' })
    expect(msg).toBe(`确定删除通知？此操作不可恢复；${DEFAULT_RECOVERY}。`)
    expect(msg).not.toContain('「')
  })

  it('自定义 recovery 覆盖默认恢复路径', () => {
    const msg = buildDangerMessage({
      verb: '删除',
      target: '试卷',
      subject: 'X',
      recovery: '请确认作答无误后再提交',
    })
    expect(msg).toContain('；请确认作答无误后再提交。')
    expect(msg).not.toContain(DEFAULT_RECOVERY)
  })

  it('irreversible=false：不写不可恢复，改写 note', () => {
    const msg = buildDangerMessage({
      verb: '结束',
      target: '考试',
      impact: '学生将无法继续作答',
      irreversible: false,
      note: '结束后暂不支持重新开启',
    })
    expect(msg).toBe('确定结束考试？学生将无法继续作答，结束后暂不支持重新开启。')
    expect(msg).not.toContain('不可恢复')
  })

  it('irreversible=false 且无 note：只保留影响范围', () => {
    const msg = buildDangerMessage({
      verb: '移出',
      target: '班级',
      subject: '张三',
      impact: '移出后该学生将不再属于此班级',
      irreversible: false,
    })
    expect(msg).toBe('确定移出班级「张三」？移出后该学生将不再属于此班级。')
  })
})

describe('confirmDanger', () => {
  beforeEach(() => {
    mocks.confirm.mockReset()
  })

  it('用户确认时 resolve(true)，并传标准化参数给 ElMessageBox', async () => {
    mocks.confirm.mockResolvedValue('confirm')
    const ok = await confirmDanger({
      verb: '删除',
      target: '试卷',
      subject: 'P',
      impact: '将一并移除关联数据',
    })
    expect(ok).toBe(true)
    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    const [message, title, options] = mocks.confirm.mock.calls[0]
    expect(message).toBe(
      `确定删除试卷「P」？将一并移除关联数据，此操作不可恢复；${DEFAULT_RECOVERY}。`,
    )
    expect(title).toBe('删除确认')
    expect(options).toMatchObject({
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
  })

  it('用户取消（reject）时 resolve(false)，不向上抛', async () => {
    mocks.confirm.mockRejectedValue('cancel')
    const ok = await confirmDanger({ verb: '删除', target: '班级', subject: 'C' })
    expect(ok).toBe(false)
  })

  it('关闭确认框（reject close）同样 resolve(false)', async () => {
    mocks.confirm.mockRejectedValue('close')
    const ok = await confirmDanger({ verb: '取消', target: '上传会话' })
    expect(ok).toBe(false)
  })

  it('confirmText / cancelText 可覆盖默认按钮文案', async () => {
    mocks.confirm.mockResolvedValue('confirm')
    await confirmDanger({
      verb: '提交',
      target: '试卷',
      confirmText: '确认提交',
      cancelText: '继续答题',
    })
    expect(mocks.confirm.mock.calls[0][2]).toMatchObject({
      confirmButtonText: '确认提交',
      cancelButtonText: '继续答题',
    })
  })
})
