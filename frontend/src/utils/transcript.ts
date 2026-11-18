import { formatDate } from './datetime'

export interface TranscriptProfile {
  fullName?: string
  username?: string
  studentId?: string
  schoolName?: string
  collegeName?: string
  department?: string
  major?: string
  majorName?: string
  grade?: string
}

export interface TranscriptCourse {
  code?: string
  name: string
  credits?: number | null
  semester?: string
  score?: number | null
  remark?: string
}

export interface TranscriptOptions {
  issuedAt?: Date
}

function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function numberOrDash(value: number | null | undefined, digits = 1): string {
  if (value == null || !Number.isFinite(Number(value))) return '—'
  const rounded = Math.round(Number(value) * 10 ** digits) / 10 ** digits
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(digits)
}

function scoreText(value: number | null | undefined): string {
  return value == null || !Number.isFinite(Number(value)) ? '待出分' : numberOrDash(value)
}

/**
 * 生成可打印的成绩单 HTML。
 *
 * <p>这里输出的是「系统打印版」：学校名称、学生信息与课程成绩均来自真实数据；
 * **不画电子印章**，盖章栏留给学校教务部门线下处理。这样既保证页面可用，
 * 也不会用前端图形冒充官方盖章文件。</p>
 */
export function buildTranscriptHtml(
  profile: TranscriptProfile,
  courses: TranscriptCourse[],
  options: TranscriptOptions = {},
): string {
  const school = (profile.schoolName || '').trim()
  const title = school ? `${escapeHtml(school)}学生成绩单` : '学生成绩单'
  const issuedAt = options.issuedAt ?? new Date()
  const name = profile.fullName || profile.username || '—'
  const college = profile.collegeName || profile.department || '—'
  const major = profile.majorName || profile.major || '—'
  const totalCredits = courses.reduce(
    (sum, course) => sum + (Number.isFinite(Number(course.credits)) ? Number(course.credits) : 0),
    0,
  )
  const scored = courses.filter((course) => Number.isFinite(Number(course.score)))
  const average =
    scored.length > 0
      ? scored.reduce((sum, course) => sum + Number(course.score), 0) / scored.length
      : null

  const rows = courses
    .map((course, index) => {
      const credits =
        course.credits == null || !Number.isFinite(Number(course.credits))
          ? '—'
          : numberOrDash(course.credits)
      return `<tr>
        <td class="num">${index + 1}</td>
        <td>${escapeHtml(course.code || '—')}</td>
        <td class="course-name">${escapeHtml(course.name || '未命名课程')}</td>
        <td class="num">${credits}</td>
        <td>${escapeHtml(course.semester || '—')}</td>
        <td class="num score">${scoreText(course.score)}</td>
        <td>${escapeHtml(course.remark || (course.score == null ? '修读中' : '已出分'))}</td>
      </tr>`
    })
    .join('')

  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <title>${title}</title>
  <style>
    @page { size: A4; margin: 18mm; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      color: #111;
      background: #fff;
      font-family: "SimSun", "Songti SC", "Noto Serif CJK SC", serif;
      font-size: 12pt;
      line-height: 1.55;
    }
    .sheet { max-width: 760px; margin: 0 auto; }
    h1 { margin: 0 0 6px; text-align: center; font-size: 22pt; letter-spacing: 2px; }
    .subtitle { margin: 0 0 18px; text-align: center; color: #555; font-size: 10pt; }
    .meta {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 6px 18px;
      padding: 10px 0;
      border-top: 1px solid #222;
      border-bottom: 1px solid #222;
    }
    .meta div { white-space: nowrap; }
    .meta b { display: inline-block; min-width: 4.5em; font-weight: 600; }
    table { width: 100%; margin-top: 16px; border-collapse: collapse; table-layout: fixed; }
    th, td { padding: 7px 6px; border: 1px solid #333; vertical-align: middle; }
    th { background: #f3f3f3; font-weight: 600; text-align: center; }
    td.num { text-align: center; font-variant-numeric: tabular-nums; }
    td.course-name { overflow-wrap: anywhere; }
    td.score { font-weight: 600; }
    .summary { margin-top: 10px; color: #333; font-size: 10.5pt; }
    .signature { display: flex; justify-content: flex-end; margin-top: 34px; }
    .seal { min-width: 190px; padding-top: 5px; border-top: 1px solid #333; text-align: center; color: #555; font-size: 10.5pt; }
    .note { margin-top: 26px; padding-top: 10px; border-top: 1px dashed #aaa; color: #666; font-size: 9.5pt; line-height: 1.7; }
    .no-print { margin-top: 18px; text-align: center; }
    @media print {
      .no-print { display: none; }
      body { print-color-adjust: exact; -webkit-print-color-adjust: exact; }
    }
  </style>
</head>
<body>
  <main class="sheet">
    <h1>${title}</h1>
    <p class="subtitle">Academic Transcript · 打印日期 ${escapeHtml(formatDate(issuedAt))}</p>
    <section class="meta" aria-label="学生信息">
      <div><b>姓名</b>${escapeHtml(name)}</div>
      <div><b>学号</b>${escapeHtml(profile.studentId || '—')}</div>
      <div><b>年级</b>${escapeHtml(profile.grade || '—')}</div>
      <div><b>学院</b>${escapeHtml(college)}</div>
      <div><b>专业</b>${escapeHtml(major)}</div>
      <div><b>学校</b>${escapeHtml(school || '—')}</div>
    </section>
    <table aria-label="课程成绩">
      <thead>
        <tr>
          <th style="width: 7%">序号</th>
          <th style="width: 15%">课程代码</th>
          <th>课程名称</th>
          <th style="width: 9%">学分</th>
          <th style="width: 14%">学期</th>
          <th style="width: 10%">总评</th>
          <th style="width: 10%">备注</th>
        </tr>
      </thead>
      <tbody>${rows || '<tr><td colspan="7" style="text-align:center;color:#777">暂无课程成绩</td></tr>'}</tbody>
    </table>
    <p class="summary">共 ${courses.length} 门课程；总学分 ${numberOrDash(totalCredits)}；已出分课程平均总评 ${average == null ? '—' : numberOrDash(average)}。</p>
    <div class="signature">
      <div class="seal">教务处（盖章）</div>
    </div>
    <p class="note">
      说明：本成绩单由教务系统按当前数据生成，打印后须经学校教务部门审核并加盖成绩单专用章后生效。
      课程成绩以教务部门最终归档记录为准。
    </p>
    <p class="no-print">如打印窗口未自动打开，请使用浏览器菜单「打印」并选择「另存为 PDF」。</p>
  </main>
</body>
</html>`
}

/**
 * 打开独立打印窗口。返回 false 表示浏览器拦截了弹窗，调用方应提示用户。
 */
export function openTranscript(
  profile: TranscriptProfile,
  courses: TranscriptCourse[],
  options: TranscriptOptions = {},
): boolean {
  if (typeof window === 'undefined') return false
  const printWindow = window.open('', '_blank', 'width=900,height=1000')
  if (!printWindow) return false

  printWindow.document.open()
  printWindow.document.write(buildTranscriptHtml(profile, courses, options))
  printWindow.document.close()
  printWindow.focus()
  setTimeout(() => {
    try {
      printWindow.print()
    } catch {
      // 用户可在打印窗口中手动触发；这里不把异常抛回页面。
    }
  }, 350)
  return true
}
