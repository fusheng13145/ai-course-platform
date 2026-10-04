import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as gradeApi from '@/api/grade'

/**
 * 成绩方案 API 契约测试（2026-10-04）
 * <p>本模块此前 0% 覆盖。成绩方案是「policy → item → final」三段链路，
 * `computeFinal` 的 URL 同时带 policyId 与 studentId 两段 path，
 * 顺序写反会把别人的总评算给当前学生，故锁定。</p>
 */

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: {
    get: mocks.get,
    post: mocks.post,
    put: mocks.put,
    delete: mocks.del,
  },
}))

describe('grade API 契约（方法 / URL / 参数）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: {} })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
  })

  it('saveGradePolicy 走 POST /grade/policy 并原样带上方案体', async () => {
    const body = { courseId: 3, name: '标准方案', items: [{ key: 'exam', weight: 0.5 }] }
    await gradeApi.saveGradePolicy(body)
    expect(mocks.post).toHaveBeenCalledWith('/grade/policy', body)
  })

  it('getGradePolicy 走 GET /grade/policy/course/{courseId}', async () => {
    await gradeApi.getGradePolicy(3)
    expect(mocks.get).toHaveBeenCalledWith('/grade/policy/course/3')
  })

  it('saveGradeItem 走 POST /grade/item（成绩项按 GradeItem DTO 上报）', async () => {
    const item = { policyId: 1, studentId: 8, componentKey: 'exam', score: 88 }
    await gradeApi.saveGradeItem(item)
    expect(mocks.post).toHaveBeenCalledWith('/grade/item', item)
  })

  it('computeFinal 的 policyId 在前、studentId 在后（顺序反了会算错学生）', async () => {
    await gradeApi.computeFinal(1, 8)
    expect(mocks.get).toHaveBeenCalledWith('/grade/final/1/student/8')
  })

  it('getCourseReport 走 GET /grade/report/course/{courseId}', async () => {
    await gradeApi.getCourseReport(3)
    expect(mocks.get).toHaveBeenCalledWith('/grade/report/course/3')
  })

  it('默认导出覆盖全部具名接口', () => {
    const named = Object.keys(gradeApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(gradeApi.default as object)
    expect(named.filter((k) => !defaultKeys.includes(k))).toEqual([])
  })
})
