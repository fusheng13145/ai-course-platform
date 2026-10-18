import { describe, it, expect } from 'vitest'
import { readdirSync, statSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'

/**
 * D2-7 对话框焦点管理覆盖率守护（第六批收官后新增）。
 *
 * <p>背景：D2-7 键盘可达性分六批推进——前五批接入录入类与多弹层文件，
 * 第六批补齐全部剩余查看类弹层（成绩详情 / 统计 / 记录 / 预览等），
 * 至此全仓含 `<el-dialog` 的组件 100% 接入 `useDialogFocus`。</p>
 *
 * <p>本用例把「覆盖率 100%」从一次性成果变成**硬约束**：任何一个新写的
 * `<el-dialog` 组件若忘记 `ref="dialogRef"` + `useDialogFocus(...)` 接线
 * （或既有接线被误删），构建期即红——与 nav-reachability、Redis 白名单等
 * 同类机械护栏一个口径。</p>
 */

const SRC = join(process.cwd(), 'src')

function collectVueSources(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry === '__tests__') continue
      collectVueSources(full, out)
      continue
    }
    if (entry.endsWith('.vue')) out.push(full)
  }
  return out
}

/** 去掉 HTML 注释：`<el-dialog>` 出现在注释里（如 v-loading 注意事项）不算弹层。 */
const stripComments = (text: string) => text.replace(/<!--[\s\S]*?-->/g, '')

describe('D2-7 对话框焦点管理覆盖率守护', () => {
  it('全仓含 <el-dialog 的组件必须接入 useDialogFocus', () => {
    const seen = collectVueSources(SRC).map((file) => {
      const text = stripComments(readFileSync(file, 'utf8'))
      return { file: relative(SRC, file).replace(/\\/g, '/'), text }
    })

    const dialogFiles = seen.filter((f) => f.text.includes('<el-dialog'))

    // 防止扫描逻辑退化后空转：真实弹层组件数十个，低于下限说明扫描已失效
    expect(
      dialogFiles.length,
      '扫描到的弹层组件数异常偏低——请检查扫描逻辑是否失效',
    ).toBeGreaterThanOrEqual(50)

    const offenders = dialogFiles
      .filter((f) => !f.text.includes('useDialogFocus'))
      .map((f) => f.file)

    expect(
      offenders,
      `以下组件含 <el-dialog 但未接入 useDialogFocus（补 ref="dialogRef" + 原语调用）：\n${offenders.join('\n')}`,
    ).toEqual([])
  })
})
