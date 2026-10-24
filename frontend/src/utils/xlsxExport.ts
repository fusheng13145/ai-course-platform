import * as XLSX from 'xlsx'

/**
 * XLSX 导出的惰性入口（FE-11）。
 *
 * <p>两层拆分的原因：动态 `import('xlsx')` 会让 Rollup 对整个 xlsx 包放弃树摇
 * （482KB 全量）；而静态 import 进本模块、再由调用方 `await import('@/utils/xlsxExport')`
 * 动态加载本模块——树摇照常生效（实测 ≈323KB），且该块只在点"导出"时才进内存，
 * 成绩相关路由的首载不再背它。</p>
 *
 * <p>调用方传「行对象数组 + 工作表名 + 文件名」；列顺序即对象键序。</p>
 */
export function downloadXlsx(
  rows: Record<string, unknown>[],
  sheetName: string,
  filename: string,
): void {
  const ws = XLSX.utils.json_to_sheet(rows)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, sheetName)
  XLSX.writeFile(wb, filename)
}
