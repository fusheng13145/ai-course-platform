/** ltr 阴影取反的前后对比：量面板右侧 40px 带内的平均亮度（越暗=投影越强）。 */
const { execFileSync } = require('child_process')
const fs = require('fs')
const path = require('path')
const FE = 'C:/Users/ASUS/Desktop/大创赛/ai-course-platform/frontend'
const { chromium } = require(path.join(FE, 'node_modules/@playwright/test'))

const styleBlock = (src) => src.match(/<style scoped>([\s\S]*?)<\/style>/)[1]
const beforeCss = styleBlock(
  execFileSync('git', ['-C', path.dirname(FE), 'show', '999862c:frontend/src/components/ui/Drawer.vue'], {
    encoding: 'utf8',
  }),
)
const afterCss = styleBlock(fs.readFileSync(path.join(FE, 'src/components/ui/Drawer.vue'), 'utf8'))
const theme = fs.readFileSync(path.join(FE, 'src/styles/theme.css'), 'utf8')
const rootBlock = theme.slice(theme.indexOf(':root'), theme.indexOf('\n}', theme.indexOf(':root')) + 2)

const html = (css) => `<!doctype html><html><head><meta charset="utf-8"><style>
${rootBlock}
html,body{margin:0;height:900px;background:var(--bg);font-family:system-ui,sans-serif}
${css}
</style></head><body>
<div class="drawer-overlay"></div>
<div class="drawer ltr" role="dialog">
  <div class="drawer-head"><div class="modal-title">明细</div></div>
  <div class="drawer-body"><p>内容占位</p></div>
  <div class="drawer-foot"><button>关闭</button></div>
</div>
</body></html>`

;(async () => {
  const browser = await chromium.launch()
  const out = {}
  for (const [tag, css] of [
    ['before', beforeCss],
    ['after', afterCss],
  ]) {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
    await page.setContent(html(css), { waitUntil: 'load' })
    const buf = await page.screenshot()
    fs.writeFileSync(path.join(__dirname, 'evidence', `ltr-${tag}-page.png`), buf)
    const p = await browser.newPage()
    out[tag] = await p.evaluate(
      async (s) => {
        const img = await new Promise((res, rej) => {
          const i = new Image()
          i.onload = () => res(i)
          i.onerror = rej
          i.src = 'data:image/png;base64,' + s
        })
        const c = document.createElement('canvas')
        c.width = img.width
        c.height = img.height
        const x = c.getContext('2d', { willReadFrequently: true })
        x.drawImage(img, 0, 0)
        const d = x.getImageData(0, 0, img.width, img.height).data
        const lumAt = (px, py) => {
          const i = (py * img.width + px) * 4
          return 0.2126 * d[i] + 0.7152 * d[i + 1] + 0.0722 * d[i + 2]
        }
        // 面板宽 480（ltr 贴左）→ 右侧 8..48px 带内、y=300..700 的平均亮度；再看左侧带（被裁到屏外，应恒等于背景）
        const band = (x0, x1) => {
          let s = 0
          let n = 0
          for (let px = x0; px < x1; px += 2)
            for (let py = 300; py < 700; py += 10) {
              s += lumAt(px, py)
              n++
            }
          return +(s / n).toFixed(2)
        }
        return { rightBandLum: band(488, 528), bgLumReference: band(1000, 1040), panelLeftX0Band: band(2, 40) }
      },
      buf.toString('base64'),
    )
    await p.close()
    await page.close()
  }
  await browser.close()
  console.log(JSON.stringify(out, null, 1))
  console.log(
    'rightBandLum 越小=面板右接缝的投影越强。--sh-drawer 的 x=-18px 是给右侧抽屉写的，' +
      'ltr 取反成 +18px 之后，左贴边抽屉的右接缝才该压出阴影。',
  )
})()
