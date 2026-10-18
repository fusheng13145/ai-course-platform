import { describe, it, expect } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * v-loading 指令守卫（缺陷 B3）。
 *
 * `v-loading` 是运行时指令，必须绑在一个真实元素根上。`<el-dialog>` 的内容渲染在
 * teleport 的非元素根（Fragment）上，指令无处安放，后果是两重：
 * 1. Vue 运行时告警 `Failed to resolve directive: loading`；
 * 2. 遮罩实际不生效——loading 态在弹窗上静默失效，看起来像"数据慢"而不是"绑定错"。
 *
 * 正确写法（同仓统一口径）：把 `v-loading` 绑到 dialog 的默认插槽内的包裹 `<div>` 上，
 * 见 CourseProctor.vue:88/:132、CctStudentTable.vue:2。
 */

// 覆盖整个 src（弹窗组件不只存在于 views/，layouts 与 components 同样受本守卫约束）
const srcDir = resolve(__dirname, '../..')

function collectVueFiles(dir: string, acc: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const full = resolve(dir, name)
    if (name === '__tests__' || name === 'node_modules') continue
    if (statSync(full).isDirectory()) collectVueFiles(full, acc)
    else if (name.endsWith('.vue')) acc.push(full)
  }
  return acc
}

/** 找出把 v-loading 直接写在 <el-dialog> 开标签上的位置（含多行属性写法）。 */
function findDialogLoadingTags(text: string) {
  const hits: { line: number; tag: string }[] = []
  const tagRe = /<el-dialog\b[^>]*>/gs
  let m: RegExpExecArray | null
  while ((m = tagRe.exec(text)) !== null) {
    if (m[0].includes('v-loading')) {
      hits.push({ line: text.slice(0, m.index).split('\n').length, tag: m[0].slice(0, 80) })
    }
  }
  return hits
}

describe('守卫：v-loading 不得绑在 el-dialog 上', () => {
  it('检测器本身可用（阴性对照：反例必被抓、正例必被放）', () => {
    const bad = '<el-dialog v-model="v" v-loading="l" title="x">\n  <p/>\n</el-dialog>'
    expect(findDialogLoadingTags(bad)).toHaveLength(1)

    // 多行属性写法是 el-dialog 的常见排版，同样必须被抓到
    const badMultiline = '<el-dialog\n  v-model="v"\n  v-loading="l"\n  width="560px"\n>'
    expect(findDialogLoadingTags(badMultiline)).toHaveLength(1)

    const good = '<el-dialog v-model="v" title="x">\n  <div v-loading="l"><p/></div>\n</el-dialog>'
    expect(findDialogLoadingTags(good)).toHaveLength(0)

    // el-dialog 之外的元素上绑 v-loading 不是本守卫的范围
    expect(findDialogLoadingTags('<div v-loading="l"><span/></div>')).toHaveLength(0)
  })

  it('全仓 .vue 无 el-dialog + v-loading 组合', () => {
    const offenders: string[] = []
    for (const file of collectVueFiles(srcDir)) {
      const hits = findDialogLoadingTags(readFileSync(file, 'utf8'))
      if (hits.length) {
        offenders.push(...hits.map((h) => `${file.replace(srcDir, 'src')}:${h.line} ${h.tag}`))
      }
    }
    expect(offenders).toEqual([])
  })
})
