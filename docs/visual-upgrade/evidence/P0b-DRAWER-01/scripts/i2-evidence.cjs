/**
 * Task 11 评审 I-2 的视觉证据：`.drawer` 圆角缺 overflow:hidden 的前后对比。
 * 全部产物写在仓库外（%LOCALAPPDATA%/Temp/t11fix/evidence），不碰 frontend/e2e/**。
 * 两个变体用的是同一份 DOM、同一段 CSS，唯一差别就是 .drawer 里那一行 overflow:hidden。
 */
const { execFileSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const FE = 'C:/Users/ASUS/Desktop/大创赛/ai-course-platform/frontend'
const { chromium } = require(path.join(FE, 'node_modules/@playwright/test'))
const REPO = path.dirname(FE)
const OUT = path.join(__dirname, 'evidence')
fs.mkdirSync(OUT, { recursive: true })

function styleBlock(src) {
  const m = src.match(/<style scoped>([\s\S]*?)<\/style>/)
  if (!m) throw new Error('没找到 <style scoped> 块')
  return m[1]
}
const beforeSrc = execFileSync('git', ['-C', REPO, 'show', '999862c:frontend/src/components/ui/Drawer.vue'], {
  encoding: 'utf8',
})
const afterSrc = fs.readFileSync(path.join(FE, 'src/components/ui/Drawer.vue'), 'utf8')
const beforeCss = styleBlock(beforeSrc)
const afterCss = styleBlock(afterSrc)

// 两条 CSS 的差别必须只有 overflow:hidden，否则对比就不是单变量。
const diffLines = afterCss
  .split('\n')
  .filter((l, i) => beforeCss.split('\n')[i] !== l)
  .filter((l) => l.trim() && !l.trim().startsWith('/*') && !l.trim().startsWith('*'))
console.log('CSS 差异行（去掉注释后）: ' + JSON.stringify(diffLines.map((d) => d.trim())))

const theme = fs.readFileSync(path.join(FE, 'src/styles/theme.css'), 'utf8')
const rootBlock = theme.slice(theme.indexOf(':root'), theme.indexOf('\n}', theme.indexOf(':root')) + 2)
if (!/--r-l1/.test(rootBlock)) throw new Error('theme.css 的 :root 块里没取到 --r-l1')

const html = (css) => `<!doctype html><html lang="zh"><head><meta charset="utf-8"><style>
${rootBlock}
*{box-sizing:border-box}
html,body{margin:0;height:900px}
body{background:var(--bg);color:var(--ink);font-family:system-ui,sans-serif;font-size:13px}
.l1-card{margin:24px;background:var(--surface);border:1px solid var(--line);border-radius:var(--r-l1);padding:22px}
.btn{border:1px solid var(--line);background:var(--surface);border-radius:8px;padding:8px 16px}
.btn-primary{background:var(--accent);border-color:var(--accent);color:#fff}
.input{border:1px solid var(--line);border-radius:8px;padding:8px;width:100%;background:var(--surface)}
${css}
</style></head><body>
<div class="l1-card">
  <div style="font-size:15px;font-weight:600">知识库文档</div>
  <p>背景卡片：用来确认抽屉下圆角外侧露出的是遮罩而不是面板自身的矩形底。</p>
  <p>第二行占位内容，制造可比对的页面像素。</p>
</div>
<div class="drawer-overlay">
  <div class="drawer rtl wide" role="dialog">
    <div class="drawer-head">
      <div>
        <div class="modal-title">发布通知</div>
        <div class="modal-sub">选择接收对象后由系统批量写入通知</div>
      </div>
      <div style="flex:1"></div>
      <button class="t-icon-btn" aria-label="关闭">✕</button>
    </div>
    <div class="drawer-body">
      <div class="field"><label>通知标题</label><input class="input" value="期末考试安排"></div>
      <div class="field"><label>正文内容</label><input class="input" value="占位正文"></div>
    </div>
    <div class="drawer-foot">
      <button class="btn btn-plain">取消</button>
      <button class="btn btn-primary">确认发布</button>
    </div>
  </div>
</div>
</body></html>`

async function diffImages(browser, a, b) {
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
      const w = Math.min(ia.width, ib.width)
      const h = Math.min(ia.height, ib.height)
      const grab = (img) => {
        const c = document.createElement('canvas')
        c.width = w
        c.height = h
        const x = c.getContext('2d', { willReadFrequently: true })
        x.drawImage(img, 0, 0, w, h)
        return x.getImageData(0, 0, w, h).data
      }
      const da = grab(ia)
      const db = grab(ib)
      let count = 0
      let minX = w
      let minY = h
      let maxX = -1
      let maxY = -1
      for (let y = 0; y < h; y++) {
        for (let x = 0; x < w; x++) {
          const i = (y * w + x) * 4
          if (da[i] !== db[i] || da[i + 1] !== db[i + 1] || da[i + 2] !== db[i + 2]) {
            count++
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
          }
        }
      }
      const px = (d, x, y) => {
        const i = (y * w + x) * 4
        return [d[i], d[i + 1], d[i + 2]]
      }
      return {
        w,
        h,
        count,
        bbox: [minX, minY, maxX, maxY],
        corner: {
          beforeBL: px(da, 2, h - 2),
          afterBL: px(db, 2, h - 2),
          beforeBR: px(da, w - 3, h - 2),
          afterBR: px(db, w - 3, h - 2),
          beforeTL: px(da, 2, 2),
          afterTL: px(db, 2, 2),
          midLeftEdge_before: px(da, 2, Math.floor(h / 2)),
          midLeftEdge_after: px(db, 2, Math.floor(h / 2)),
        },
      }
    },
    [a.toString('base64'), b.toString('base64')],
  )
  await p.close()
  return r
}

;(async () => {
  const browser = await chromium.launch()
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const shots = {}
  for (const [tag, css] of [
    ['before', beforeCss],
    ['after', afterCss],
  ]) {
    await page.setContent(html(css), { waitUntil: 'load' })
    await page.evaluate(() => document.fonts.ready)
    shots[tag + '_el'] = await page.locator('.drawer').screenshot()
    shots[tag + '_page'] = await page.screenshot()
    // 记录面板在页面里的位置，好把元素级 bbox 换算到整页坐标
    shots[tag + '_box'] = await page.locator('.drawer').boundingBox()
    fs.writeFileSync(path.join(OUT, `drawer-${tag}-element.png`), shots[tag + '_el'])
    fs.writeFileSync(path.join(OUT, `drawer-${tag}-page-1440x900.png`), shots[tag + '_page'])
  }

  const elDiff = await diffImages(browser, shots.before_el, shots.after_el)
  const pageDiff = await diffImages(browser, shots.before_page, shots.after_page)
  await browser.close()

  const report = {
    cssDiffLines: diffLines.map((d) => d.trim()),
    panelBoxInPage: shots.before_box,
    panelBoxInPageAfter: shots.after_box,
    elementLevel: elDiff,
    pageLevel1440x900: pageDiff,
    allowanceAtRatio00005: Math.round(1440 * 900 * 0.0005),
    pageDiffRatio: pageDiff.count / (1440 * 900),
    cornerProbeNote:
      'corner = 面板元素图里 (2,H-2) 等位置的实际像素 RGB；BL=左下、BR=右下、TL=左上',
  }
  fs.writeFileSync(path.join(OUT, 'i2-evidence.json'), JSON.stringify(report, null, 2))
  console.log(JSON.stringify(report, null, 2))
})()
