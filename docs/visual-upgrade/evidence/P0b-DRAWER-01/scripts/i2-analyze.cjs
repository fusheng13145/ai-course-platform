const { execFileSync } = require('child_process')
const fs = require('fs')
const path = require('path')
const FE = 'C:/Users/ASUS/Desktop/大创赛/ai-course-platform/frontend'
const { chromium } = require(path.join(FE, 'node_modules/@playwright/test'))

function styleBlock(src) {
  return src.match(/<style scoped>([\s\S]*?)<\/style>/)[1]
}
// 去注释 + 去空行后的「声明级」对比：证明前后两版 CSS 只差我改的那几条
function decls(css) {
  return css
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
}
const before = decls(styleBlock(execFileSync('git', ['-C', path.dirname(FE), 'show', '999862c:frontend/src/components/ui/Drawer.vue'], { encoding: 'utf8' })))
const after = decls(styleBlock(fs.readFileSync(path.join(FE, 'src/components/ui/Drawer.vue'), 'utf8')))
const onlyAfter = after.filter((l) => !before.includes(l))
const onlyBefore = before.filter((l) => !after.includes(l))
console.log('CSS 声明级差异（已去注释）:')
console.log('  新增: ' + JSON.stringify(onlyAfter))
console.log('  删除: ' + JSON.stringify(onlyBefore))

const OUT = path.join(__dirname, 'evidence')
const a = fs.readFileSync(path.join(OUT, 'drawer-before-element.png')).toString('base64')
const b = fs.readFileSync(path.join(OUT, 'drawer-after-element.png')).toString('base64')

;(async () => {
  const browser = await chromium.launch()
  const p = await browser.newPage()
  const r = await p.evaluate(
    async ([sa, sb]) => {
      const load = (s) =>
        new Promise((res, rej) => {
          const i = new Image()
          i.onload = () => res(i)
          i.onerror = rej
          i.src = 'data:image/png;base64,' + s
        })
      const [ia, ib] = await Promise.all([load(sa), load(sb)])
      const w = ia.width
      const h = ia.height
      const grab = (img) => {
        const c = document.createElement('canvas')
        c.width = w
        c.height = h
        const x = c.getContext('2d', { willReadFrequently: true })
        x.drawImage(img, 0, 0)
        return x.getImageData(0, 0, w, h).data
      }
      const da = grab(ia)
      const db = grab(ib)
      const pts = []
      for (let y = 0; y < h; y++) {
        for (let x = 0; x < w; x++) {
          const i = (y * w + x) * 4
          if (da[i] !== db[i] || da[i + 1] !== db[i + 1] || da[i + 2] !== db[i + 2]) pts.push([x, y])
        }
      }
      // 按 8px 网格聚合，看清差异到底落在哪几块
      const grid = {}
      for (const [x, y] of pts) {
        const k = Math.floor(x / 8) * 8 + ',' + Math.floor(y / 8) * 8
        grid[k] = (grid[k] || 0) + 1
      }
      return { w, h, total: pts.length, grid, sample: pts.slice(0, 12) }
    },
    [a, b],
  )
  await browser.close()
  console.log('\n元素图差异: ' + r.total + ' px 于 ' + r.w + 'x' + r.h)
  console.log('8px 网格分布 (x,y -> 个数):')
  Object.entries(r.grid)
    .sort((x, y) => y[1] - x[1])
    .forEach(([k, v]) => console.log('   ' + k + ' -> ' + v))
})()
