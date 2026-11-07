import { describe, it, expect } from 'vitest'

import { mergeGradeRecords } from '@/composables/useTeacherGrades'

/**
 * useTeacherGrades 成绩合并逻辑单元测试
 *
 * <p>本文件的用例针对真实缺陷 P1-8（2026-10-04）：</p>
 * <p>① 同一学生多次考试时，原实现写 `existing.totalScore = (existing.totalScore +
 * (s.score || 0)) / 2` —— 这是**递推均值**，三次考试 60/80/100 得到
 * `((60+80)/2+100)/2 = 85`，真实均值应为 80，且考试次数越多偏差越大、无规律；
 * 同时 `s.score || 0` 把「未批阅(null)」当 0 分计入，直接拉低总评。</p>
 * <p>② 原实现硬编码 `examScore * 0.5 + sub.score * 0.5` 魔法权重。</p>
 * <p>③ 原实现 `studentId || userId`：两个键都缺失时 key 为 undefined，
 * 不同学生全部塌缩进同一个桶，合并成同一行（姓名取第一条、分数互相污染）。</p>
 */

describe('mergeGradeRecords — 真实均值（P1-8① 回归）', () => {
  it('同一学生三次考试 60/80/100 的总评必须是真实均值 80，而非递推值 85', () => {
    const rows = mergeGradeRecords(
      [
        { studentId: 1, studentName: '张三', score: 60 },
        { studentId: 1, studentName: '张三', score: 80 },
        { studentId: 1, studentName: '张三', score: 100 },
      ],
      [],
    )

    expect(rows).toHaveLength(1)
    expect(rows[0].examScores).toEqual([60, 80, 100])
    expect(rows[0].examScore).toBe(80)
    expect(rows[0].totalScore).toBe(80)
  })

  it('未批阅（score=null）不得当 0 分计入均值', () => {
    const rows = mergeGradeRecords(
      [
        { studentId: 1, score: null },
        { studentId: 1, score: 80 },
      ],
      [],
    )

    expect(rows[0].examScores).toEqual([80])
    expect(rows[0].totalScore).toBe(80)
  })

  it('全部未批阅时总评为 null，等级为空串而不是 F', () => {
    const rows = mergeGradeRecords(
      [
        { studentId: 1, score: null },
        { studentId: 1, score: undefined },
      ],
      [],
    )

    expect(rows[0].totalScore).toBeNull()
    expect(rows[0].gradeLevel).toBe('')
    // 未批阅标记必须为真，教师端才能看到待批阅提示
    expect(rows[0].needsGrading).toBe(true)
  })

  it('考试与作业按次数等权参与真实均值，不再使用隐藏 0.5/0.5 系数（P1-8②）', () => {
    const rows = mergeGradeRecords([{ studentId: 1, score: 90 }], [{ studentId: 1, score: 70 }])

    expect(rows[0].examScore).toBe(90)
    expect(rows[0].homeworkScore).toBe(70)
    expect(rows[0].totalScore).toBe(80)
  })

  it('两次作业 + 一次考试时，总评是三次有效分数的均值', () => {
    const rows = mergeGradeRecords(
      [{ studentId: 7, score: 100 }],
      [
        { studentId: 7, score: 70 },
        { studentId: 7, score: 85 },
      ],
    )

    expect(rows[0].totalScore).toBe(85)
  })

  it('非数值分数（字符串/NaN）按未批阅处理，不污染均值', () => {
    const rows = mergeGradeRecords(
      [
        { studentId: 1, score: '80' },
        { studentId: 1, score: NaN },
        { studentId: 1, score: 60 },
      ],
      [],
    )

    expect(rows[0].examScores).toEqual([60])
    expect(rows[0].totalScore).toBe(60)
  })
})

describe('mergeGradeRecords — 学生身份与合并边界（P1-8③）', () => {
  it('studentId 与 userId 双缺失的两条记录必须各自单独成行，绝不合并', () => {
    const rows = mergeGradeRecords(
      [
        { studentName: '甲', score: 60 },
        { studentName: '乙', score: 80 },
      ],
      [],
    )

    expect(rows).toHaveLength(2)
    // 默认 sort 按 UTF-16 码位排中文（'乙' < '甲'），这里用 localeCompare 保证可读的断言
    const names = rows
      .map((r: { studentName: string }) => r.studentName)
      .sort((a, b) => a.localeCompare(b, 'zh'))
    expect(names).toEqual(['甲', '乙'])
    expect(rows.map((r) => r.totalScore).sort()).toEqual([60, 80])
  })

  it('studentId 缺失但 userId 存在时按 userId 合并为同一学生', () => {
    const rows = mergeGradeRecords(
      [
        { userId: 42, score: 60 },
        { userId: 42, score: 80 },
      ],
      [],
    )

    expect(rows).toHaveLength(1)
    expect(rows[0].totalScore).toBe(70)
  })

  it('空字符串 id 视为缺失（不得把所有空串记录并成一个学生）', () => {
    const rows = mergeGradeRecords(
      [
        { studentId: '', score: 50 },
        { studentId: '', score: 90 },
      ],
      [],
    )

    expect(rows).toHaveLength(2)
  })

  it('不同学生之间不得互相污染分数', () => {
    const rows = mergeGradeRecords(
      [
        { studentId: 1, score: 60 },
        { studentId: 2, score: 100 },
      ],
      [],
    )

    expect(rows).toHaveLength(2)
    const byId = Object.fromEntries(rows.map((r) => [String(r.id), r.totalScore]))
    expect(byId).toEqual({ '1': 60, '2': 100 })
  })

  it('首条记录无名时，后续记录的真实姓名必须补齐', () => {
    const rows = mergeGradeRecords(
      [{ studentId: 1 }, { studentId: 1, studentName: '李四', studentNo: '2021001', score: 70 }],
      [],
    )

    expect(rows[0].studentName).toBe('李四')
    expect(rows[0].studentNo).toBe('2021001')
  })
})

describe('mergeGradeRecords — 入参健壮性与等级换算', () => {
  it.each([undefined, null, 'not-an-array', {}, 42])('非法入参 %p 返回空数组而不是抛错', (bad) => {
    expect(mergeGradeRecords(bad as never, bad as never)).toEqual([])
  })

  it.each([
    [90, 'A'],
    [89.9, 'B'],
    [80, 'B'],
    [70, 'C'],
    [60, 'D'],
    [59.9, 'F'],
    [0, 'F'],
  ])('总评 %s 对应等级 %s', (score, level) => {
    const rows = mergeGradeRecords([{ studentId: 1, score }], [])
    expect(rows[0].gradeLevel).toBe(level)
  })

  it('任一来源未批阅即标记待批阅（作业侧与考试侧都算）', () => {
    const rows = mergeGradeRecords(
      [{ studentId: 1, score: 80, isGraded: true }],
      [{ studentId: 1, score: 70, status: 'SUBMITTED' }],
    )
    expect(rows[0].needsGrading).toBe(true)

    const graded = mergeGradeRecords(
      [{ studentId: 1, score: 80, isGraded: true }],
      [{ studentId: 1, score: 70, status: 'graded' }],
    )
    expect(graded[0].needsGrading).toBe(false)
  })
})
