/** 取记录的数值分数；null / undefined / 非数值 / NaN 一律视为「未批阅」→ null */
function toScore(value) {
  return typeof value === 'number' && !Number.isNaN(value) ? value : null
}

function studentKeyOf(record) {
  const id = record?.studentId ?? record?.userId
  return id === undefined || id === null || id === '' ? null : String(id)
}

function meanScore(scores) {
  const valid = scores.filter((score) => typeof score === 'number' && !Number.isNaN(score))
  if (valid.length === 0) return null
  return Math.round((valid.reduce((sum, score) => sum + score, 0) / valid.length) * 10) / 10
}

export function gradeLevelOf(score) {
  if (score == null) return ''
  if (score >= 90) return 'A'
  if (score >= 80) return 'B'
  if (score >= 70) return 'C'
  if (score >= 60) return 'D'
  return 'F'
}

/**
 * 兼容旧数据口径的纯函数回归点；教师成绩页改为使用课程成绩方案报表，不再用此结果代替正式总评。
 * <p>从 useTeacherGrades 拆出的纯函数域（FE-14）：会话成绩与作业提交按学生合并为成绩行，
 * 无任何 Vue 依赖，回归面由 useTeacherGrades.test.ts 的 mergeGradeRecords 用例组钉住。</p>
 */
export function mergeGradeRecords(sessionData = [], submitData = []) {
  const map = new Map()
  const standalone = []
  const pickName = (record) => record.studentName || record.userName || '未知'
  const pickNo = (record) => record.studentNo || record.userNo || '-'

  function touch(row, record) {
    if (row.studentName === '未知') row.studentName = pickName(record)
    if (row.studentNo === '-') row.studentNo = pickNo(record)
    if (!row.courseName) row.courseName = record.courseName || ''
    if (!row.submitTime) row.submitTime = record.submitTime || record.completedAt || ''
  }

  function newRow(key, record) {
    return {
      id: key,
      studentNo: pickNo(record),
      studentName: pickName(record),
      examScores: [],
      homeworkScores: [],
      examScore: null,
      homeworkScore: null,
      totalScore: null,
      gradeLevel: '',
      needsGrading: false,
      submitTime: record.submitTime || record.completedAt || record.submittedAt || '',
      details: [],
      courseName: record.courseName || '',
    }
  }

  function finalize(row) {
    row.examScore = meanScore(row.examScores)
    row.homeworkScore = meanScore(row.homeworkScores)
    row.totalScore = meanScore([...row.examScores, ...row.homeworkScores])
    row.gradeLevel = gradeLevelOf(row.totalScore)
    return row
  }

  for (const session of Array.isArray(sessionData) ? sessionData : []) {
    const key = studentKeyOf(session)
    const score = toScore(session.score)
    if (!key) {
      const row = finalize(newRow(null, session))
      row.examScores = score === null ? [] : [score]
      row.examScore = score
      row.totalScore = score
      row.gradeLevel = gradeLevelOf(score)
      row.needsGrading = !session.isGraded && session.status !== 'graded'
      standalone.push(row)
      continue
    }
    if (!map.has(key)) map.set(key, newRow(key, session))
    const row = map.get(key)
    if (score !== null) row.examScores.push(score)
    row.needsGrading = row.needsGrading || (!session.isGraded && session.status !== 'graded')
    touch(row, session)
  }

  for (const submission of Array.isArray(submitData) ? submitData : []) {
    const key = studentKeyOf(submission)
    const score = toScore(submission.score)
    const needsGrading = submission.status !== 'graded' && submission.status !== 'reviewed'
    if (!key) {
      const row = finalize(newRow(null, submission))
      row.homeworkScores = score === null ? [] : [score]
      row.homeworkScore = score
      row.totalScore = score
      row.gradeLevel = gradeLevelOf(score)
      row.needsGrading = needsGrading
      standalone.push(row)
      continue
    }
    if (!map.has(key)) map.set(key, newRow(key, submission))
    const row = map.get(key)
    if (score !== null) row.homeworkScores.push(score)
    row.needsGrading = row.needsGrading || needsGrading
    touch(row, submission)
  }

  return [...Array.from(map.values()).map(finalize), ...standalone]
}
