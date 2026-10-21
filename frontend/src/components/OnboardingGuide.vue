<template>
  <Teleport to="body">
    <Transition name="ob-fade">
      <div v-if="visible" class="ob-overlay">
        <!-- 洞外压暗：几何层给的四块矩形（上/下/左/右），洞本身没有元素 ⇒ 点击天然落到真实控件上。
             点这四块＝暂停并记住当前步，不再等于永久完成（spec §3.3 出口表）。 -->
        <div
          v-for="(r, i) in scrimRects"
          :key="i"
          class="ob-scrim"
          :style="{
            top: r.top + 'px',
            left: r.left + 'px',
            width: r.width + 'px',
            height: r.height + 'px',
          }"
          @click="exit('paused')"
        ></div>
        <!-- 目标描边：锚点解析不到（anchorMiss）时不画，卡片退化为居中态且不阻塞点击 -->
        <div
          v-if="current.anchor && !anchorMiss"
          class="ob-ring"
          :style="ringStyle"
          aria-hidden="true"
        ></div>
        <div
          ref="cardEl"
          class="ob-card"
          :class="{ centered: !current.anchor || anchorMiss }"
          :style="cardStyle"
          :data-placement="placement"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="`ob-title-${current.id}`"
          tabindex="-1"
        >
          <div class="ob-head">
            <span
              v-for="(_, i) in steps"
              :key="i"
              class="ob-step-dot"
              :class="{ active: i === index }"
            ></span>
            <span class="ob-count">{{ index + 1 }} / {{ steps.length }}</span>
          </div>

          <div class="ob-body">
            <h3 :id="`ob-title-${current.id}`" class="ob-title">{{ current.title }}</h3>
            <p class="ob-desc">{{ current.desc }}</p>
          </div>

          <div class="ob-foot">
            <div class="ob-foot-left">
              <button
                class="ob-btn ghost"
                type="button"
                data-exit="skipped"
                @click="exit('skipped')"
              >
                跳过
              </button>
              <button class="ob-btn ghost" type="button" data-exit="paused" @click="exit('paused')">
                稍后再看
              </button>
            </div>
            <div class="ob-foot-right">
              <button v-if="index > 0" class="ob-btn ghost" type="button" @click="prev">
                上一步
              </button>
              <button v-if="!isLast" class="ob-btn primary" type="button" @click="next">
                下一步
              </button>
              <button v-else class="ob-btn primary" type="button" @click="onAnchorClick">
                开始使用
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useFocusTrap } from '@/composables/useFocusTrap'
import { loadSteps, validateSteps } from '@/onboarding/steps/loader'
import { resolveAnchor } from '@/onboarding/anchors'
import { useSpotlightOverlay } from '@/composables/useSpotlightOverlay'
import { readState, writeStatus, migrateV1IfPresent } from '@/onboarding/state'

/**
 * 新用户引导卡片。四个单元各管一件事（spec §3.1），本组件只负责卡片、进度、出口与焦点交接，
 * **不读也不写 localStorage**：存储是 `onboarding/state` 的契约 v2（done / skipped@n / paused@n
 * 三态可区分——改前四条出口往同一个键写 '1'，"完成"与"中途 Esc"在数据上是同一件事）；
 * 找元素只走 `onboarding/anchors` 的注册表（改前用 `.sidebar`／`.topbar` 这类布局类名，
 * 视觉批次一改就静默定位失败）；几何（挖洞、视口感知定位、滚动跟随）只走 `useSpotlightOverlay`。
 *
 * userId／role 由宿主 AppShell 以 props 给出（改前组件自己 useAuthStore()，同一份身份两条路取值）。
 * 「已看过」仍按账号记键（DR-52／D2-9 定案口径不变）：换账号登录会重新看到，同账号退出不打扰；
 * 登出不清这个键。v1 标记由 state.migrateV1IfPresent 迁移成 done，老用户绝不再莫名重弹。
 * 宿主可经 ref 调 show()（用户菜单「新手引导」，从第 1 步）或 resume()（从 paused@n 续看）。
 */

const props = defineProps({
  userId: { type: [Number, String], default: null },
  role: { type: String, default: 'student' },
})
const emit = defineEmits(['done', 'paused', 'skipped'])
const router = useRouter()

const steps = computed(() => {
  const list = loadSteps(props.role)
  validateSteps(list)
  return list
})

const visible = ref(false)
const index = ref(0)
const cardEl = ref(null)
const active = computed(() => visible.value)
const isLast = computed(() => index.value >= steps.value.length - 1)
const current = computed(() => steps.value[index.value])

// 焦点交接（判据 4）：卡片开时锁卡内，Tab 永不逃到背景页——改前这块根本没接线
useFocusTrap(cardEl, active)

const { scrimRects, ringStyle, cardStyle, placement, anchorMiss, refresh, follow } =
  useSpotlightOverlay({
    getTarget: () => (current.value.anchor ? resolveAnchor(current.value.anchor) : null),
    cardRef: cardEl,
    onAnchorMiss: () => {
      // 不静默：锚点解析失败必须留痕（spec §3.2），但卡片仍可用，不能把引导变成一块砖
      console.warn(
        `[onboarding] anchor-miss:${current.value.anchor ?? '(none)'}（第 ${index.value + 1} 步，降级为居中卡片且不阻塞点击）`,
      )
    },
  })

/** Task 2 交出的 refresh 是 rAF 延迟版（R-T2-4 前送本棒），所以冲帧归本组件负责（R-T3-4） */
const flushFrames = (n) =>
  new Promise((resolve) => {
    const step = (left) => (left <= 0 ? resolve() : requestAnimationFrame(() => step(left - 1)))
    step(n)
  })

async function relocate() {
  if (!visible.value) return
  await nextTick()
  refresh()
  await flushFrames(2)
  // 冲帧期间已被关掉／卸载：不要再对消失的目标调 follow()（它内部还有 320ms 定时器）
  if (!visible.value) return
  const el = current.value.anchor ? resolveAnchor(current.value.anchor) : null
  if (el) follow(el)
}

function open(at) {
  visible.value = true
  index.value = Math.min(at, steps.value.length - 1)
  nextTick(() => {
    cardEl.value?.focus?.()
    relocate()
  })
}

const show = () => open(0)
const resume = () => {
  const st = readState(props.userId)
  open(st.status === 'paused' ? st.atStep : 0)
}

function exit(status) {
  writeStatus(props.userId, status, index.value)
  visible.value = false
  emit(status, { atStep: index.value })
}

function next() {
  if (isLast.value) return
  index.value += 1
  relocate()
}
function prev() {
  if (index.value > 0) {
    index.value -= 1
    relocate()
  }
}

/** 动作执行期间为真：激活动作步要 el.click() 真点一下，那次点击不能再被本层当"用户点了洞内"吃回去 */
let consumingAnchorClick = false

/** 中间步骤：点击被引导消费（前进、不导航、不写终态）；激活动作步：先 release 再真执行并结束 */
async function onAnchorClick(event) {
  event?.preventDefault?.()
  event?.stopPropagation?.()
  if (!isLast.value) return next()
  const action = current.value.action
  const el = current.value.anchor ? resolveAnchor(current.value.anchor) : null
  // 顺序是 spec §3.2 的落点：先撤卡片（陷阱随 active 关边沿摘掉）再执行动作，
  // 否则动作打开的弹层与本层 trap 抢焦点（两 trap 冲突）。
  visible.value = false
  consumingAnchorClick = true
  try {
    if (action?.type === 'navigate') await router.push(action.to)
    else if (action?.type === 'click-anchor') el?.click()
  } finally {
    consumingAnchorClick = false
  }
  exit('done')
}

/** 洞内真元素被点 ⇒ 交给引导消费（判据 3 的真实面，不只是测试钩子）。
 *  捕获阶段：必须赶在真实控件自己的导航处理器之前吃掉；
 *  只在当前锚点子树内响应，卡片／洞外压暗块／背景一律不拦。 */
function onDocumentClick(event) {
  if (!visible.value || consumingAnchorClick) return
  const key = current.value?.anchor
  if (!key) return
  const el = resolveAnchor(key)
  const target = event?.target
  if (!el || !(target instanceof Node) || !el.contains(target)) return
  onAnchorClick(event).catch((err) => console.warn('[onboarding] 出口动作执行失败', err))
}

function onKeydown(e) {
  if (!visible.value) return
  if (e.key === 'Escape') exit('paused')
  if (e.key === 'ArrowRight') next()
  if (e.key === 'ArrowLeft') prev()
}

let autoTimer = null
onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('click', onDocumentClick, true)
  migrateV1IfPresent(props.userId)
  const st = readState(props.userId)
  if (st.status === 'idle') autoTimer = setTimeout(() => open(0), 600)
  else if (st.status === 'paused') autoTimer = setTimeout(() => resume(), 600)
})
onBeforeUnmount(() => {
  if (autoTimer) clearTimeout(autoTimer)
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('click', onDocumentClick, true)
})

defineExpose({
  show,
  resume,
  getIndex: () => index.value,
  __trapStep: () => {
    // 交给真实陷阱实现代触发 Tab（jsdom 不实现顺序聚焦导航）。
    // 钩子缺失必须抛：写成可选链的话"陷阱被摘掉"这条测试仍绿，判据 4 变成空壳（R-T3-3）。
    const advance = cardEl.value?.__trapAdvance
    if (typeof advance !== 'function') {
      throw new Error('[onboarding] 焦点陷阱未装载：卡片元素上没有 __trapAdvance 钩子')
    }
    advance()
  },
  __key: onKeydown,
  __scrimClick: () => exit('paused'),
  __anchorClick: onAnchorClick,
  __relocate: relocate,
  __goto: async (n) => {
    index.value = n
    await relocate()
  },
})
</script>

<style scoped>
.ob-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-onboarding);
  pointer-events: none;
}
.ob-scrim {
  position: fixed;
  background: var(--onboarding-scrim);
  z-index: var(--z-onboarding);
  pointer-events: auto;
}
.ob-ring {
  position: fixed;
  box-sizing: border-box;
  border: var(--onboarding-ring);
  box-shadow: var(--onboarding-glow, none);
  border-radius: 10px;
  z-index: var(--z-onboarding);
  pointer-events: none;
}
.ob-card {
  position: fixed;
  z-index: calc(var(--z-onboarding) + 1);
  pointer-events: auto;
  width: 320px;
  background: var(--ac-bg-elevated, var(--surface));
  color: var(--ac-text-primary, var(--ink));
  border: 1px solid var(--ac-border, rgba(0, 0, 0, 0.08));
  border-radius: var(--ac-radius-xl, 16px);
  box-shadow: var(--ac-shadow-xl, 0 24px 48px rgba(15, 23, 42, 0.18));
  padding: 18px 18px 14px;
}
.ob-card.centered {
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
.ob-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.ob-step-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--ac-border-strong, rgba(0, 0, 0, 0.18));
  transition: all 0.22s var(--ease-out, ease);
}
.ob-step-dot.active {
  width: 18px;
  border-radius: 4px;
  background: var(--accent, var(--info));
}
.ob-count {
  font-size: 12px;
  color: var(--ac-text-tertiary, var(--ink-3));
}
.ob-body {
  text-align: center;
  padding: 6px 4px 14px;
}
.ob-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--accent-soft, rgba(37, 99, 168, 0.12));
  color: var(--accent, var(--info));
  margin-bottom: 10px;
}
.ob-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
}
.ob-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ac-text-secondary, var(--ink-2));
}
.ob-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ob-foot-right {
  display: flex;
  gap: 8px;
}
/* 左侧两个「退出」出口（跳过／稍后再看）并排，右侧翻页；本仓 style 段只许追加 */
.ob-foot-left {
  display: flex;
  gap: 8px;
}
.ob-btn {
  border: none;
  cursor: pointer;
  border-radius: var(--ac-radius-md, 8px);
  padding: 6px 14px;
  font-size: 13px;
  transition: all 0.18s var(--ease-out, ease);
}
.ob-btn.ghost {
  background: transparent;
  color: var(--ac-text-secondary, var(--ink-2));
}
.ob-btn.ghost:hover {
  background: var(--ac-bg-hover, rgba(0, 0, 0, 0.05));
}
.ob-btn.primary {
  background: var(--accent, var(--info));
  color: var(--ac-on-primary, var(--surface));
}
.ob-btn.primary:hover {
  filter: brightness(1.08);
}
.ob-fade-enter-active,
.ob-fade-leave-active {
  transition: opacity 0.25s var(--ease-out, ease);
}
.ob-fade-enter-from,
.ob-fade-leave-to {
  opacity: 0;
}
</style>
