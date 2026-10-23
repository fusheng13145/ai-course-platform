import type { Page } from '@playwright/test'

/**
 * 三端新人引导的 e2e 夹具。
 *
 * 【为什么单独一份而不塞进 test-helpers.ts】`test-helpers.ts` 服务的是"把引导关掉再继续测别的页面"
 * 这一类用例，这里服务的是"引导本身"——两套断言要读的字段不一样，混在一起会让关掉引导的那条
 * `dismissOnboarding` 长出第三个用途。
 * 【只用 storageState，不用 loginAs】登录端点按 IP 限流（10 次/60 秒）且 429 不打日志，
 * 三端 ×N 条重登会把失败集合变成随机集合；会话夹具由视觉 config 的 globalSetup 生成。
 */

export const V2_PREFIX = 'ai-course:onboarding:v2'

/** 清成"从没看过"并等引导真的弹出来（自动弹挂在一个 600ms 定时器上，所以必须等） */
export async function resetGuide(page: Page) {
  await page.evaluate(() => {
    Object.keys(localStorage)
      .filter((k) => k.includes('onboarding'))
      .forEach((k) => localStorage.removeItem(k))
  })
  await page.reload()
  await page.waitForSelector('.ob-card', { timeout: 12000 })
  await page.waitForTimeout(400)
}

/**
 * 一帧引导的全部可读形状。
 *
 * 色差一条的参照物是**洞内被露出的那个元素**的有效底色，不是 `document.body`：
 * 暗色 body＝`--bg` #0f1419，0.66 的黑压上去实测只差 16/255，拿它当判据等于写一条永不成立的断言
 * （改前那条"压暗不可见"的 3/255 读数也是同一个坑）。求法：从描边中心 `elementFromPoint` 命中真实元素，
 * 沿祖先链上溯——半透明层逐层合成、撞见不透明层即止；途中撞见渐变（侧栏那条两主题同值的
 * `linear-gradient`）时不继续下潜，因为渐变盖在它下面的一切之上、而计算样式无法把渐变归约成单色，
 * 参照退回 `--surface` 令牌值。参照物本身随读数返回，断言消息里必须写它是谁。
 * **量程：这是计算样式层面的合成，不是渲染像素**（本改造明确不加像素基线）。
 */
export const guideSnapshot = (page: Page) =>
  page.evaluate(() => {
    const parse = (s: string) => {
      const str = String(s || '').trim()
      const m = /rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:[,\s/]+([\d.]+))?\s*\)/.exec(str)
      if (m)
        return [Number(m[1]), Number(m[2]), Number(m[3]), m[4] === undefined ? 1 : Number(m[4])]
      const h = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(str)
      if (h) {
        const hex =
          h[1].length === 3
            ? h[1]
                .split('')
                .map((c) => c + c)
                .join('')
            : h[1]
        return [0, 2, 4].map((i) => parseInt(hex.slice(i, i + 2), 16)).concat(1)
      }
      return null
    }
    const over = (fg: number[], bg: number[]) => {
      const a = fg[3] ?? 1
      return fg.slice(0, 3).map((c, i) => Math.round(c * a + bg[i] * (1 - a)))
    }
    const maxDelta = (a: number[], b: number[]) => Math.max(...a.map((c, i) => Math.abs(c - b[i])))
    const describe = (el: Element | null) => {
      if (!el || !(el instanceof HTMLElement)) return String(el && el.tagName)
      const cls = el.className.trim().split(/\s+/).filter(Boolean).slice(0, 2).join('.')
      return `${el.tagName.toLowerCase()}${el.dataset.onboard ? `[data-onboard=${el.dataset.onboard}]` : ''}${
        cls ? '.' + cls : ''
      }`
    }

    const card = document.querySelector('.ob-card')
    const scrim = document.querySelector('.ob-scrim')
    const ring = document.querySelector('.ob-ring')
    const rootStyle = getComputedStyle(document.documentElement)
    const surfaceToken = parse(rootStyle.getPropertyValue('--surface'))
    const bodyBg = parse(getComputedStyle(document.body).backgroundColor)
    const scrimColor = scrim ? parse(getComputedStyle(scrim).backgroundColor) : null

    let revealed: Element | null = null
    if (ring) {
      const rr = ring.getBoundingClientRect()
      revealed = document.elementFromPoint(rr.left + rr.width / 2, rr.top + rr.height / 2)
    }
    const film: number[][] = []
    let effective: number[] | null = null
    let source = ''
    let node: Element | null = revealed
    while (node && node.nodeType === 1) {
      const st = getComputedStyle(node)
      const c = parse(st.backgroundColor)
      if (/(linear|radial)-gradient/.test(st.backgroundImage || '')) {
        effective = surfaceToken
        source = `渐变层 ${describe(node)} 不可归约为单色 ⇒ 参照退回 --surface`
        break
      }
      if (c && c[3] === 1) {
        effective = film.reduceRight((acc, layer) => over(layer, acc), c.slice(0, 3))
        source = `不透明层 ${describe(node)}`
        break
      }
      if (c && c[3] > 0) film.push(c)
      node = node.parentElement
    }
    if (!effective) {
      effective = surfaceToken
      source = '祖先链全透明 ⇒ 参照退回 --surface'
    }

    const r = card ? card.getBoundingClientRect() : null
    const v2key = Object.keys(localStorage).find((k) => k.startsWith('ai-course:onboarding:v2:'))
    return {
      count: document.querySelector('.ob-count')?.textContent?.trim() ?? '',
      title: document.querySelector('.ob-title')?.textContent?.trim() ?? '',
      placement: (card as HTMLElement | null)?.dataset?.placement ?? '',
      cardFullyVisible:
        !!r &&
        r.top >= 8 &&
        r.left >= 8 &&
        r.bottom <= innerHeight - 8 &&
        r.right <= innerWidth - 8,
      cardRect: r ? { top: r.top, left: r.left, bottom: r.bottom, right: r.right } : null,
      scrimCount: document.querySelectorAll('.ob-scrim').length,
      scrimBg: scrim ? getComputedStyle(scrim).backgroundColor : '',
      themeAttr: document.documentElement.getAttribute('data-theme'),
      revealedElement: describe(revealed),
      /** 命中点常在锚点的子节点上（图标／文字 span），所以另给一个"它属于哪个锚点键"的读数 */
      revealedAnchorKey:
        revealed instanceof Element
          ? (revealed.closest('[data-onboard]')?.getAttribute('data-onboard') ?? null)
          : null,
      effectiveBgSource: source,
      effectiveBg: effective,
      bodyBg: bodyBg ? bodyBg.join(',') : '',
      /** scrim 合成到"洞内那个元素"之上后与该元素的单通道最大差——判据用的就是这一格 */
      deltaOnRevealed:
        scrimColor && effective ? maxDelta(over(scrimColor, effective), effective) : null,
      /** 同一把尺量页面基底：只为把"参照选错会得到什么数"留在读数里，不参与断言 */
      deltaOnBody:
        scrimColor && bodyBg ? maxDelta(over(scrimColor, bodyBg), bodyBg.slice(0, 3)) : null,
      v2: v2key ? localStorage.getItem(v2key) : null,
    }
  })

export const readStatus = (page: Page) =>
  page.evaluate(() => {
    const k = Object.keys(localStorage).find((x) => x.startsWith('ai-course:onboarding:v2:'))
    return k ? JSON.parse(localStorage.getItem(k) as string) : null
  })
