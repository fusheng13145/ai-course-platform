import * as XLSX from 'xlsx'

/**
 * XLSX 导出的惰性入口（FE-11）。
 *
 * <p>两层拆分的原因：动态 `import('xlsx')` 会让 Rollup 对整个 xlsx 包放弃树摇
 * （482KB 全量）；而静态 import 进本模块、再由调用方 `await import('@/utils/xlsxExport')`
 * 动态加载本模块——树摇照常生效（实测 ≈323KB），且该块只在点"导出"时才进内存，
 * 成绩相关路由的首载不再背它。</p>
 *
 * <p>导出列顺序即对象键序。</p>
 */
export interface XlsxSheet {
  name: string
  rows: Record<string, unknown>[]
}

/**
 * 多工作表导出：学情报告需要「概览 / 课程成绩 / 薄弱课程」三张表同时落地，
 * 避免把不同结构的数据硬拼成一张宽表。
 */
export function downloadWorkbook(sheets: XlsxSheet[], filename: string): void {
  const wb = XLSX.utils.book_new()
  for (const sheet of sheets) {
    const ws = XLSX.utils.json_to_sheet(sheet.rows)
    XLSX.utils.book_append_sheet(wb, ws, sheet.name)
  }
  XLSX.writeFile(wb, filename)
}

/**
 * 单工作表导出：调用方传「行对象数组 + 工作表名 + 文件名」。
 */
export function downloadXlsx(
  rows: Record<string, unknown>[],
  sheetName: string,
  filename: string,
): void {
  downloadWorkbook([{ name: sheetName, rows }], filename)
}
