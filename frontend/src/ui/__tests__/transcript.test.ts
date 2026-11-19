import { describe, expect, it } from 'vitest'
import { buildTranscriptHtml } from '@/utils/transcript'

const COURSES = [
  {
    code: 'CN202',
    name: '计算机网络',
    credits: 3.5,
    semester: '2026-2027-1',
    score: 86.5,
    remark: '已出分',
  },
]

describe('正式成绩单打印模板', () => {
  it('包含学生、课程与盖章栏，且不伪造电子印章', () => {
    const html = buildTranscriptHtml(
      {
        fullName: '李昂',
        studentId: '2023010101',
        schoolName: '江苏科技大学',
        collegeName: '计算机学院',
        majorName: '软件工程',
        grade: '2023',
      },
      COURSES,
      { issuedAt: new Date('2026-11-18T00:00:00+08:00') },
    )

    expect(html).toContain('江苏科技大学学生成绩单')
    expect(html).toContain('李昂')
    expect(html).toContain('2023010101')
    expect(html).toContain('CN202')
    expect(html).toContain('计算机网络')
    expect(html).toContain('86.5')
    expect(html).toContain('教务处（盖章）')
    expect(html).toContain('须经学校教务部门审核并加盖成绩单专用章后生效')
    expect(html).not.toContain('<img')
  })

  it('转义学生与课程字段，避免打印页被注入 HTML', () => {
    const html = buildTranscriptHtml({ fullName: '<script>alert(1)</script>', schoolName: 'A&B' }, [
      { name: '<b>课程</b>', code: '"><img src=x>' },
    ])

    expect(html).not.toContain('<script>alert(1)</script>')
    expect(html).not.toContain('<b>课程</b>')
    expect(html).not.toContain('<img src=x>')
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(html).toContain('A&amp;B')
  })
})
