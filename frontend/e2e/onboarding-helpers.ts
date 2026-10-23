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
 * 视口判定的亚像素容差（px）。只用来吸收 `getBoundingClientRect` 的浮点取整
 * （`bottom` 会算出 667.0000000001 这种值），**不是**给几何实现留的缓冲——
 * 缓冲（四边各 8px）是设计目标，见 `guideSnapshot()` 的 `cardBufferOk` 只读读数。
 */
export const VIEWPORT_EPS = 0.5

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
  // EPS 走参数传入：`page.evaluate` 的回调在浏览器里执行，读不到 Node 侧的模块级常量
  page.evaluate((eps: number) => {
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
      /**
       * 判据 1 的判决：**整卡完全可见**。判据原文见 spec §4-1「四档视口下每一步卡片整卡完全可见」，
       * 里面没有任何像素缓冲。
       *
       * 【改前为什么是假阴性（R-07）】原实现要求四边各留 **8px**（`r.top >= 8`、`r.bottom <= innerHeight - 8`），
       * 而 8px 是几何实现自己的**设计目标缓冲**，不是判据。于是 375 档"卡底离视口边 7.83px"
       * 这种**完整可见**的状态被判成 false——判据没错，判决函数把它读错了。
       * 现在按原文判：矩形落在视口内即成立，`EPS = 0.5` 只吸收亚像素取整（`bottom` 可能算出
       * 667.0000001 这类值）。贴边余量不被容忍度盖掉——它另有 `cardMarginPx` / `cardBufferOk`
       * 两个**只读读数**，8px 目标继续可见。
       */
      cardFullyVisible:
        !!r &&
        r.top >= -eps &&
        r.left >= -eps &&
        r.bottom <= innerHeight + eps &&
        r.right <= innerWidth + eps,
      cardRect: r ? { top: r.top, left: r.left, bottom: r.bottom, right: r.right } : null,
      /** 四边最小余量 px（两位小数）：**只读读数、不参与断言**。8px 是设计目标，不是判据 */
      cardMarginPx: r
        ? Number(Math.min(r.top, r.left, innerHeight - r.bottom, innerWidth - r.right).toFixed(2))
        : null,
      /** 设计目标（四边各 ≥8px 缓冲）是否满足：**只读读数、不参与断言**；false 不代表判据未达成 */
      cardBufferOk: r
        ? Math.min(r.top, r.left, innerHeight - r.bottom, innerWidth - r.right) >= 8
        : false,
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
  }, VIEWPORT_EPS)

export const readStatus = (page: Page) =>
  page.evaluate(() => {
    const k = Object.keys(localStorage).find((x) => x.startsWith('ai-course:onboarding:v2:'))
    return k ? JSON.parse(localStorage.getItem(k) as string) : null
  })

/* -------------------------------------------------------------------------- *
 * 判据 4 的真实键盘读数（路线图 R-06）
 * -------------------------------------------------------------------------- *
 * `focusProbe` 只回答一个问题：**此刻** `document.activeElement` 落在引导卡的哪里。
 * 判据 4 的断言就建在它的 `inCard` 一格上（`card.contains(activeElement)`），其余字段
 * 只为把读数写进 evidence、让"看起来绿了"和"环真的在动"分得开。
 *
 * 【为什么用 `tabIndex >= 0` 而不是抄一份 `DIALOG_FOCUSABLE_SELECTOR`】
 * 顺序聚焦导航里"谁算可聚焦"由**浏览器**决定，不是由焦点陷阱那份选择器决定。把陷阱的
 * 选择器抄进 e2e 当量尺，就等于用被测口径去量被测对象——它写错时读数会跟着一起错。
 * 所以这里用的是平台自己的判定：元素 `tabIndex >= 0`（`<div>` 无 tabindex 时是 -1，天然排除；
 * `tabindex="-1"` 的卡片根同样落选）且 `getClientRects().length > 0`（收掉 display:none、
 * 未渲染、脱离文档的元素）。`disabled` 另判一次——`tabIndex` 仍报 0。
 *
 * 【为什么是 `closest` 的等价物而不是 `.closest('[data-onboarding-card]')`】
 * 组件没有那个属性（`OnboardingGuide.vue` 的卡片根只有 `class="ob-card"`＋`role="dialog"`），
 * 造一个就得改 `src/**`。所以根节点取 `.ob-card`——它正是模板里包住全部可聚焦控件的那个节点，
 * `contains()` 判的是"在卡子树内"，含卡片根本身（打开时焦点先交给卡片）。
 * -------------------------------------------------------------------------- */

/** `focusProbe` 的返回形状（跨 `page.evaluate` 的序列化结果） */
export interface FocusProbe {
  /** 引导卡在不在场：不在场时后面所有格子都是空读数 */
  cardPresent: boolean
  /** 判据 4 唯一被断言的那一格：activeElement 在 `.ob-card` 子树内 */
  inCard: boolean
  /** 焦点是不是还停在卡片根上（打开瞬间的交接位；`tabindex="-1"` 故不在可聚焦环里） */
  atCardRoot: boolean
  tag: string | null
  label: string | null
  /** 可读标识：`button[data-exit=skipped].ob-btn.ghost` 这一形 */
  path: string | null
  /** activeElement 在卡内可聚焦环上的下标；卡片根与"不在环上"都读 -1，靠 atCardRoot/inCard 区分 */
  ringIndex: number
  ringSize: number
  ring: string[]
  /** 逃出卡片时落在哪个元素上——红的时候这一格才是要看的 */
  outsidePath: string | null
}

export const focusProbe = (page: Page) =>
  page.evaluate((): FocusProbe => {
    const card = document.querySelector<HTMLElement>('.ob-card')
    const active = document.activeElement as HTMLElement | null

    const clsOf = (n: HTMLElement) =>
      (n.getAttribute('class') ?? '').trim().split(/\s+/).filter(Boolean).slice(0, 2).join('.')
    const labelOf = (n: HTMLElement) => {
      const text = (n.textContent ?? '').replace(/\s+/g, ' ').trim()
      if (text) return text.slice(0, 16)
      return n.getAttribute('aria-label') ?? n.getAttribute('data-exit') ?? ''
    }
    const pathOf = (n: HTMLElement) => {
      const exit = n.getAttribute('data-exit')
      const cls = clsOf(n)
      return `${n.tagName.toLowerCase()}${exit ? `[data-exit=${exit}]` : ''}${cls ? `.${cls}` : ''}`
    }

    if (!card) {
      return {
        cardPresent: false,
        inCard: false,
        atCardRoot: false,
        tag: active ? active.tagName.toLowerCase() : null,
        label: active ? labelOf(active) : null,
        path: active ? pathOf(active) : null,
        ringIndex: -1,
        ringSize: 0,
        ring: [],
        outsidePath: active ? pathOf(active) : null,
      }
    }

    // 浏览器口径的顺序聚焦环：tabIndex >= 0 且真的渲染出盒子，DOM 序即 Tab 序
    // （卡片内这几个控件都没有正 tabindex，所以"DOM 序"与"Tab 序"是同一个序列）
    const ring = Array.from(card.querySelectorAll<HTMLElement>('*')).filter(
      (n) => n.tabIndex >= 0 && !(n as HTMLButtonElement).disabled && n.getClientRects().length > 0,
    )
    const inCard = !!active && card.contains(active)

    return {
      cardPresent: true,
      inCard,
      atCardRoot: active === card,
      tag: active ? active.tagName.toLowerCase() : null,
      label: active ? labelOf(active) : null,
      path: active ? pathOf(active) : null,
      ringIndex: active ? ring.indexOf(active) : -1,
      ringSize: ring.length,
      ring: ring.map(labelOf),
      outsidePath: inCard || !active ? null : pathOf(active),
    }
  })
