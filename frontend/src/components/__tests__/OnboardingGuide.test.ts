import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import OnboardingGuide from '../OnboardingGuide.vue'

/**
 * OnboardingGuide 单元测试（按 v2 形状**重签**，11 条用例一条没删）
 * <p>重签的两处口径变化：</p>
 * <ol>
 *   <li>身份改由宿主以 props 给（改前组件自己 useAuthStore()，同一份取值两条路）——
 *       所以这里删掉了 `vi.mock('@/stores/auth')`，"换账号"换成换 userId prop；
 *       「已看过」仍按账号记键（DR-52／D2-9 口径不变）；</li>
 *   <li>出口语义分离：Esc 与点洞外＝`paused@n`（改前四个出口都往 `…:done:v1:<id>` 写 '1'，
 *       "完成"与"中途 Esc"在数据上是同一件事）。存储断言因此读 v2 键与 status。</li>
 * </ol>
 * <p>聚光那三条原来钉的是 `.ob-spotlight` 与 `.ob-overlay.has-target`（改版后模板里
 * 已不存在的残留），换成钉 `.ob-scrim` 四块矩形与 `.ob-ring`。</p>
 * <p>组件 Teleport 到 body，断言一律走 document 查询，不用 wrapper.html()。
 * 宿主用 render 函数而非 template 字符串，避免依赖运行时模板编译器。
 * 定时器只 fake setTimeout：600ms 自动弹是本组件唯一的定时器，rAF 保持真跑（relocate 要冲帧）。</p>
 */

const USER = 7
const v1Key = (id: number) => `ai-course:onboarding:done:v1:${id}`
const v2Key = (id: number) => `ai-course:onboarding:v2:${id}`

type GuideApi = {
  show: () => void
  resume: () => void
  getIndex: () => number
  __key: (e: { key: string }) => void
  __goto: (n: number) => Promise<void>
}

type V2Record = { status: string; atStep: number }

/** afterEach 兜底卸载只要得到 unmount 这一件事，不必带上 VTU 的泛型形状 */
type WrapperLike = { unmount: () => void }

/** 直接读存储原文：断言钉的是"落盘形状"，不复用 state.js 的读法（否则实现错一起绿） */
function v2Raw(id: number): (V2Record & { seenAt?: string }) | null {
  const raw = localStorage.getItem(v2Key(id))
  return raw ? (JSON.parse(raw) as V2Record & { seenAt?: string }) : null
}

/** 落盘形状含 seenAt，比较状态与步号时把它剥掉——seenAt 本身另有两条断言钉 */
function v2State(id: number): V2Record | null {
  const raw = v2Raw(id)
  return raw ? { status: raw.status, atStep: raw.atStep } : null
}

function card() {
  return document.body.querySelector('.ob-card')
}

function scrims() {
  return document.body.querySelectorAll('.ob-scrim')
}

function countLabel() {
  return document.body.querySelector('.ob-count')?.textContent?.trim()
}

/** refresh 是 rAF 延迟版：几何断言前冲两帧才读得到 */
async function frames(n = 2) {
  for (let i = 0; i < n; i++) {
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
  }
}

/** 宿主：用组件 ref 复现 AppShell 的调用方式（props 由 AppShell 传，见其挂载行） */
const mounted: WrapperLike[] = []

async function mountHost(props: { userId?: number; role?: string } = {}) {
  const guide = ref<GuideApi | null>(null)
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div/>' } },
      { path: '/:any(.*)', component: { template: '<div/>' } },
    ],
  })
  const Host = defineComponent({
    setup: () => () =>
      h(OnboardingGuide, {
        ref: guide,
        userId: props.userId ?? USER,
        role: props.role ?? 'student',
      }),
  })
  const wrapper = mount(Host, { attachTo: document.body, global: { plugins: [router] } })
  // 登记下来给 afterEach：某条测试在末尾 unmount 之前转红时，实例会带着 window/document 监听
  // 活到下一条——那条 stale 实例的 Esc 会把状态写进本条的断言里（红话就指不认真缺陷了）
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, guide, router }
}

/** 向 window 派发按键（组件监听的是 window，不是 document） */
function press(key: string) {
  window.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }))
}

function markV2(id: number, status: string, atStep: number) {
  localStorage.setItem(
    v2Key(id),
    JSON.stringify({ status, atStep, seenAt: '2026-10-21T00:00:00.000Z' }),
  )
}

describe('OnboardingGuide 重开入口与出口语义', () => {
  beforeEach(() => {
    localStorage.clear()
    document.body.replaceChildren()
    // 只 fake setTimeout：600ms 自动弹出与 320ms 滚动补拍是本组件仅有的定时器
    vi.useFakeTimers({ toFake: ['setTimeout'] })
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  afterEach(() => {
    // 兜底卸载：某条测试在末尾那句 unmount 之前转红时，实例会带着 window/document 监听活到下一条
    while (mounted.length) mounted.pop()!.unmount()
    vi.restoreAllMocks()
    vi.useRealTimers()
    localStorage.clear()
    document.body.replaceChildren()
  })

  it('按账号记录：换账号登录必须重新看到引导', async () => {
    markV2(7, 'done', 4) // 账号 7 已看完

    const { wrapper } = await mountHost({ userId: 8 }) // 同一浏览器换成账号 8
    vi.advanceTimersByTime(600)
    await flushPromises()

    expect(card(), '新账号不应被旧账号的标记压掉').not.toBeNull()
    expect(countLabel()).toBe('1 / 5')
    // 旧账号的记录不受影响（未互相覆盖）
    expect(v2State(7)).toEqual({ status: 'done', atStep: 4 })
    wrapper.unmount()
  })

  it('未看过：挂载后 600ms 自动弹出（原行为不回归）', async () => {
    const { wrapper } = await mountHost()
    expect(card()).toBeNull()

    vi.advanceTimersByTime(600)
    await flushPromises()
    expect(card()).not.toBeNull()
    expect(countLabel()).toBe('1 / 5')
    wrapper.unmount()
  })

  it('负控制：done 账号挂载后不自动弹出', async () => {
    markV2(USER, 'done', 4)
    const { wrapper } = await mountHost()
    vi.advanceTimersByTime(5000)
    await flushPromises()
    expect(card()).toBeNull()
    wrapper.unmount()
  })

  it('负控制：skipped 账号同样不弹（改前 skipped 与 done 写的是同一个 "1"）', async () => {
    markV2(USER, 'skipped', 1)
    const { wrapper } = await mountHost()
    vi.advanceTimersByTime(5000)
    await flushPromises()
    expect(card(), '跳过＝这次不打扰，但不是"已看完"').toBeNull()
    wrapper.unmount()
  })

  it('已看过时仍可通过组件 ref 主动唤起（defineExpose show）', async () => {
    markV2(USER, 'done', 4)
    const { wrapper, guide } = await mountHost()
    expect(guide.value?.show, '需 defineExpose({ show }) 供 AppShell 菜单唤起').toBeTypeOf(
      'function',
    )

    guide.value!.show()
    await flushPromises()
    expect(card()).not.toBeNull()
    expect(countLabel()).toBe('1 / 5')
    wrapper.unmount()
  })

  it('重开后 Esc 仍能关闭，且写 paused 不写 done（改前 Esc＝永久完成）', async () => {
    markV2(USER, 'done', 4)
    const { wrapper, guide } = await mountHost()
    guide.value!.show()
    await flushPromises()
    expect(card()).not.toBeNull()

    press('Escape')
    await flushPromises()
    expect(card()).toBeNull()
    expect(v2State(USER)).toEqual({ status: 'paused', atStep: 0 })
    expect(localStorage.getItem(v1Key(USER)), 'v1 键不再被任何出口写').toBeNull()
    wrapper.unmount()
  })

  it('重开后 → 走到第 2 步；再次重开必须回到第 1 步', async () => {
    markV2(USER, 'done', 4)
    const { wrapper, guide } = await mountHost()
    guide.value!.show()
    await flushPromises()

    press('ArrowRight')
    await flushPromises()
    await frames() // refresh 是 rAF 延迟版：不等一帧就读不到降级后的 centered
    expect(countLabel()).toBe('2 / 5')
    // 锚点元素在隔离环境里不存在，解析失败应降级为居中卡片而不是报错
    expect(card()!.className).toContain('centered')

    guide.value!.show()
    await flushPromises()
    expect(countLabel()).toBe('1 / 5')
    wrapper.unmount()
  })

  it('点洞外压暗块＝暂停并写 paused@n（改前点遮罩＝skip＝写 done）', async () => {
    const { wrapper, guide } = await mountHost()
    const anchor = document.createElement('div')
    anchor.setAttribute('data-onboard', 'nav:ai-assistant')
    document.body.appendChild(anchor)
    guide.value!.show()
    await flushPromises()
    await guide.value!.__goto(1)
    expect(scrims().length, '洞外四块矩形必须在场，否则那条 click 打不到任何东西').toBe(4)

    ;(scrims()[0] as HTMLElement).click()
    await flushPromises()
    expect(card()).toBeNull()
    expect(v2State(USER)).toEqual({ status: 'paused', atStep: 1 })
    expect(localStorage.getItem(v1Key(USER))).toBeNull()
    wrapper.unmount()
  })

  it('负控制：未弹出时按键不渲染卡片、也不写任何状态', async () => {
    const { wrapper } = await mountHost()
    press('Escape')
    press('ArrowRight')
    await flushPromises()
    expect(card()).toBeNull()
    expect(v2Raw(USER), '关闭态的 Esc 不许把 idle 写成 paused').toBeNull()
    wrapper.unmount()
  })

  it('paused@2 之后重新挂载（刷新）：从第 3 步续弹，不从头开始', async () => {
    markV2(USER, 'paused', 2)
    const { wrapper } = await mountHost()
    vi.advanceTimersByTime(600)
    await flushPromises()
    expect(card(), 'paused 的含义就是下次接着看').not.toBeNull()
    expect(countLabel()).toBe('3 / 5')
    wrapper.unmount()
  })

  it('resume() 从第 n 步续、show() 从第 1 步（菜单重看两个入口不同）', async () => {
    markV2(USER, 'paused', 3)
    const { wrapper, guide } = await mountHost()

    guide.value!.resume()
    await flushPromises()
    expect(countLabel()).toBe('4 / 5')

    guide.value!.show()
    await flushPromises()
    expect(countLabel()).toBe('1 / 5')
    wrapper.unmount()
  })

  it('v1 迁移：只有老标记的账号不弹，v1 键保留作回滚证据、v2 写成 done', async () => {
    localStorage.setItem(v1Key(USER), '1')
    const { wrapper } = await mountHost()
    vi.advanceTimersByTime(5000)
    await flushPromises()

    expect(card(), '老用户绝不再莫名重弹').toBeNull()
    expect(v2State(USER)).toEqual({ status: 'done', atStep: 0 })
    // 落盘形状钉满 {status, atStep, seenAt}：seenAt 是这条记录唯一的时间面
    expect(v2Raw(USER)!.seenAt, 'v2 记录必须带 seenAt').toBeTypeOf('string')
    expect(localStorage.getItem(v1Key(USER)), '两键并存：v1 不删，留作回滚证据').toBe('1')
    wrapper.unmount()
  })
})

/**
 * 聚光几何接线契约（原「聚光高亮」三条，按现行 DOM 形状重签）：
 * <p>改前模板里是 `.ob-spotlight`（一块 `0 0 0 9999px` 的巨大阴影）＋ `.ob-overlay.has-target`
 * 透明套；现在是四块 `.ob-scrim` 矩形拼洞 ＋ 一块 `.ob-ring` 描边，锚点解析不到时两者都不渲染、
 * 卡片退化为居中态。断言因此钉 rect 元素与 centered，不再钉那两个已不存在的类名。</p>
 */
describe('OnboardingGuide 挖洞与描边', () => {
  beforeEach(() => {
    localStorage.clear()
    document.body.replaceChildren()
    markV2(USER, 'done', 4) // 不自动弹：下面每条都用 show()/__goto 手动走位
    vi.useFakeTimers({ toFake: ['setTimeout'] })
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  afterEach(() => {
    while (mounted.length) mounted.pop()!.unmount()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    vi.useRealTimers()
    localStorage.clear()
    document.body.replaceChildren()
  })

  it('锚点在场时渲染四块压暗矩形与描边，卡片不再是居中态（真挖洞）', async () => {
    const anchor = document.createElement('aside')
    anchor.setAttribute('data-onboard', 'nav:ai-assistant')
    document.body.appendChild(anchor)

    const { wrapper, guide } = await mountHost()
    guide.value!.show()
    await flushPromises()
    await guide.value!.__goto(1)
    await frames()

    expect(scrims()).toHaveLength(4)
    expect(document.body.querySelector('.ob-ring')).not.toBeNull()
    expect(card()!.className).not.toContain('centered')
    expect(card()!.getAttribute('data-placement')).not.toBe('centered')
    wrapper.unmount()
  })

  it('负控制：无锚点步骤不渲染压暗矩形与描边（回退居中卡片）', async () => {
    const { wrapper, guide } = await mountHost()
    guide.value!.show()
    await flushPromises()
    await frames()

    expect(scrims()).toHaveLength(0)
    expect(document.body.querySelector('.ob-ring')).toBeNull()
    expect(card()!.className).toContain('centered')
    wrapper.unmount()
  })

  it('切回无锚点步骤时压暗矩形与描边一起消失（第 2 步 → 第 1 步不留残影）', async () => {
    const anchor = document.createElement('aside')
    anchor.setAttribute('data-onboard', 'nav:ai-assistant')
    document.body.appendChild(anchor)

    const { wrapper, guide } = await mountHost()
    guide.value!.show()
    await flushPromises()
    await guide.value!.__goto(1)
    await frames()
    expect(scrims()).toHaveLength(4)

    press('ArrowLeft') // 回到第 1 步（无锚点）
    await flushPromises()
    await frames()

    expect(scrims()).toHaveLength(0)
    expect(document.body.querySelector('.ob-ring')).toBeNull()
    expect(card()!.className).toContain('centered')
    wrapper.unmount()
  })

  // 浏览器实测（330 宽）逼出来的一条：几何层"五个候选位都放不下"时返回 null，
  // 组合层据此把 placement 写成 centered，而 centered 类原先只绑"无锚点／锚点解析失败"——
  // 于是卡片既没有 top/left 也没有居中类，实测落在覆盖层的静态位置（左上角，压住顶栏）。
  // 锚点明明在场（四块压暗矩形与描边都在），所以这条不是锚点解析失败的降级，是"没有可放位"的降级。
  it('锚点在场但没有任何候选位放得下（视口窄于卡片）⇒ 卡片退化为居中态，不是左上角', async () => {
    const anchor = document.createElement('aside')
    anchor.setAttribute('data-onboard', 'nav:ai-assistant')
    document.body.appendChild(anchor)
    // 320 宽的卡片在 330 视口里两侧各只剩 5px：五个候选全不满足 8px 安全边距
    vi.stubGlobal('innerWidth', 330)

    const { wrapper, guide } = await mountHost()
    guide.value!.show()
    await flushPromises()
    await guide.value!.__goto(1)
    await frames()

    expect(scrims(), '锚点在场：压暗块必须照画（否则这条就退化成上一条的空读数）').toHaveLength(4)
    expect(card()!.getAttribute('data-placement')).toBe('centered')
    expect(
      card()!.className,
      'placement 已是 centered 而类没跟上 ⇒ 卡片会掉在 (0,0) 压住顶栏（330 宽实测形状）',
    ).toContain('centered')
    wrapper.unmount()
  })
})
