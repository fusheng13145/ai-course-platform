/**
 * 成绩项排序（学业档案 / 课程成绩 tab 共用）。
 *
 * <p>排序键是「提交时间」。原先这两个页面都不排序，顺序取决于后端 {@code getGrades}
 * 的返回次序（其 SQL 无 ORDER BY），课程顺序甚至来自 {@code Set<Long>} 的 hash 遍历，
 * 换索引或加数据就会变。这里把排序显式化，并由页面提供正序 / 倒序切换。</p>
 *
 * <p>无提交时间（未交卷、未出分、脏数据）的成绩项一律沉底：无论正序还是倒序都排在
 * 有时间的项之后，避免空值把「最新成绩」挤到列表中间。</p>
 */
export type GradeTimeOrder = 'desc' | 'asc'

/** 参与排序的最小结构；两个页面的成绩项都有 key 与 submittedAt */
export interface GradeTimeSortable {
  key: string
  submittedAt: string
}

/** 解析 "YYYY-MM-DD HH:mm" / ISO 字符串；缺失或非法返回 NaN */
export function gradeTimeValue(text?: string | null): number {
  const raw = String(text ?? '').trim()
  if (!raw || raw === '—') return Number.NaN
  const normalized = raw.includes('T') ? raw : raw.replace(' ', 'T')
  const parsed = Date.parse(normalized)
  return Number.isFinite(parsed) ? parsed : Number.NaN
}

/**
 * 按提交时间稳定排序。
 * @param rows 原始成绩项（不修改入参）
 * @param order 'desc' 最新优先 / 'asc' 最早优先
 */
export function sortGradeItems<T extends GradeTimeSortable>(
  rows: readonly T[],
  order: GradeTimeOrder,
): T[] {
  const direction = order === 'asc' ? 1 : -1
  return [...rows].sort((a, b) => {
    const timeA = gradeTimeValue(a.submittedAt)
    const timeB = gradeTimeValue(b.submittedAt)
    const missingA = Number.isNaN(timeA)
    const missingB = Number.isNaN(timeB)
    // 无时间的沉底，且不随正/倒序翻转
    if (missingA && missingB) return a.key.localeCompare(b.key)
    if (missingA) return 1
    if (missingB) return -1
    if (timeA !== timeB) return (timeA - timeB) * direction
    // 同一时间戳（批量导入常见）：固定按 key 升序，不随方向翻转——
    // 否则切到倒序时，同一分钟内的多条作业会整体反过来，反直觉
    return a.key.localeCompare(b.key)
  })
}

/** 排序切换按钮的文案 */
export function gradeOrderLabel(order: GradeTimeOrder): string {
  return order === 'desc' ? '时间 ↓ 最新优先' : '时间 ↑ 最早优先'
}

/** 正/倒序互换 */
export function toggleGradeOrder(order: GradeTimeOrder): GradeTimeOrder {
  return order === 'desc' ? 'asc' : 'desc'
}
