const fs = require('fs')
const path = require('path')
const FE = 'C:/Users/ASUS/Desktop/大创赛/ai-course-platform/frontend'
const { chromium } = require(path.join(FE, 'node_modules/@playwright/test'))
const OUT = path.join(__dirname, 'evidence')

;(async () => {
  const browser = await chromium.launch()
  const p = await browser.newPage()
  const res = await p.evaluate(
    async ([sa, sb]) => {
      const load = (s) =>
        new Promise((res, rej) => {
          const i = new Image()
          i.onload = () => res(i)
          i.onerror = rej
          i.src = 'data:image/png;base64,' + s
        })
      const stat = async (s) => {
        const img = await load(s)
        const c = document.createElement('canvas')
        c.width = img.width
        c.height = img.height
        const x = c.getContext('2d', { willReadFrequently: true })
        x.drawImage(img, 0, 0)
        const d = x.getImageData(0, 0, img.width, img.height).data
        const at = (px, py) => {
          const i = (py * img.width + px) * 4
          return [d[i], d[i + 1], d[i + 2]]
        }
        const white = (rgb) => rgb[0] >= 250 && rgb[1] >= 250 && rgb[2] >= 250
        const H = img.height
        const W = img.width
        // 左下角 16x16 角盒里有多少个像素还是「面板/页脚白」
        const cornerBox = (x0, y0) => {
          let n = 0
          for (let y = y0; y < y0 + 16; y++)
            for (let x = x0; x < x0 + 16; x++) if (white(at(x, y))) n++
          return n
        }
        return {
          size: [W, H],
          bottomLeftWhiteIn16Box: cornerBox(0, H - 16),
          topLeftWhiteIn16Box: cornerBox(0, 0),
          rightEdgeWhiteIn16Box: cornerBox(W - 16, H - 16),
          blOuterPixel: at(1, H - 2),
          tlOuterPixel: at(1, 1),
        }
      }
      return { before: await stat(sa), after: await stat(sb) }
    },
    [
      fs.readFileSync(path.join(OUT, 'drawer-before-element.png')).toString('base64'),
      fs.readFileSync(path.join(OUT, 'drawer-after-element.png')).toString('base64'),
    ],
  )
  await browser.close()
  console.log(JSON.stringify(res, null, 1))
  console.log(
    '\n参考：16px 圆角把左下角 16x16=256 的方角盒裁成 1/4 圆盘 ≈ 201 px，' +
      '所以「面板白像素」应从 256（直角）掉到 ≈201，差出的 ~55 px 就是被裁掉的直角三角。',
  )
})()
