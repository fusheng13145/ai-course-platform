import { readFileSync } from 'node:fs'

/**
 * 只读 PNG 的 IHDR 拿宽高（B8 的入口；原先这份逻辑只在 `sensitivity.spec.ts` 里有一份私有副本）。
 *
 * 【它不是"第三把解码尺"】它不还原像素、不做比较，只读文件头里那两个 32-bit 整数；
 * 判据仍然只有 `tools/measure-diff.cjs` 那一把。搬到 helper 的理由是**单一来源**：
 * 免检额 = 期望图宽×高×ratio，两个消费方（探针与 `helpers.spec.ts` 的那条断言）各写一份
 * 就会漂，而"漂开"的样子是同一张图在两处算出不同的余量倍数——正是本轮要清的那类读数。
 * 【为什么读的是**期望图**而不是实际图】`coreBundle.js:7903` 用的就是 `expected.width * expected.height`，
 *   且 `:7910` 的判据是 `count > maxDiffPixels`（**没有 ceil、没有取整**）。
 *   ⇒ 基线变长会**稀释**灵敏度（免检额随期望图面积涨），而只有实际页变高、期望图没变时
 *     runner 报 `sizesMismatchError` 直接变红（spec §6.12 末把这两半分开说过）。
 *   ⚠️ 这两行的行号指向的是 `node_modules/playwright-core`（本机 1.63.0，精确安装），
 *     不是我们继续改动的文件，所以按行号引是安全的（计划"引用禁行号"条只禁 `src/**` 与两份规格）。
 */
export function pngSize(file: string): { width: number; height: number } {
  const buf = readFileSync(file)
  if (buf.length < 24 || buf.readUInt32BE(0) !== 0x89504e47) throw new Error(`${file} 不是 PNG`)
  if (buf.toString('ascii', 12, 16) !== 'IHDR') throw new Error(`${file} 的第 0 个块不是 IHDR`)
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) }
}
