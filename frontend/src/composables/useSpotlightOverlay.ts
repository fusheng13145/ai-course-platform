import { ref, computed, onBeforeUnmount, type Ref } from 'vue'
import { computeCardPlacement, computeScrimRects } from '@/onboarding/geometry'

const FALLBACK = { width: 320, height: 245 }
const PAD = 6

interface Rect {
  top: number
  left: number
  width: number
  height: number
}

/**
 * 只负责几何：不认锚点键、不读存储、不渲染卡片。
 * getTarget() 由调用方给，调用侧负责把它接到锚点解析。
 */
export function useSpotlightOverlay({
  getTarget,
  cardRef,
  onAnchorMiss,
}: {
  getTarget: () => Element | null
  cardRef: Ref<HTMLElement | null>
  onAnchorMiss?: () => void
}) {
  const rects = ref<Rect[]>([])
  const ring = ref<Rect | null>(null)
  const cardStyle = ref<Record<string, string>>({})
  const placement = ref('centered')
  const anchorMiss = ref(false)
  let raf = 0
  let scrollTimer = 0
  let disposed = false

  function refresh() {
    raf = 0
    const el = getTarget()
    anchorMiss.value = !el
    if (!el) {
      rects.value = []
      ring.value = null
      cardStyle.value = {}
      placement.value = 'centered'
      onAnchorMiss?.()
      return
    }
    const vp = { width: window.innerWidth, height: window.innerHeight }
    const r = el.getBoundingClientRect()
    const targetRect = { top: r.top, left: r.left, width: r.width, height: r.height }
    rects.value = computeScrimRects(targetRect, vp, PAD)
    ring.value = {
      top: targetRect.top - PAD,
      left: targetRect.left - PAD,
      width: targetRect.width + PAD * 2,
      height: targetRect.height + PAD * 2,
    }
    const cardSize = cardRef.value
      ? {
          width: cardRef.value.offsetWidth || FALLBACK.width,
          height: cardRef.value.offsetHeight || FALLBACK.height,
        }
      : FALLBACK
    const box = computeCardPlacement({ targetRect, cardSize, viewport: vp })
    if (box) {
      cardStyle.value = { top: `${box.top}px`, left: `${box.left}px` }
      placement.value = box.placement
    } else {
      cardStyle.value = {}
      placement.value = 'centered'
    }
  }

  function schedule() {
    if (disposed || raf) return
    raf = requestAnimationFrame(refresh)
  }

  /** 改前只读 rect、从不滚动 ⇒ 目标在视口外时聚光框跑到屏外 */
  function follow(el: Element | null | undefined) {
    if (disposed) return
    el?.scrollIntoView?.({ block: 'center', inline: 'center', behavior: 'smooth' })
    window.clearTimeout(scrollTimer)
    scrollTimer = window.setTimeout(schedule, 320)
  }

  window.addEventListener('resize', schedule)
  window.addEventListener('scroll', schedule, true)
  onBeforeUnmount(() => {
    disposed = true
    if (raf) cancelAnimationFrame(raf)
    raf = 0
    window.clearTimeout(scrollTimer)
    window.removeEventListener('resize', schedule)
    window.removeEventListener('scroll', schedule, true)
  })

  const ringStyle = computed(() =>
    ring.value
      ? {
          top: `${ring.value.top}px`,
          left: `${ring.value.left}px`,
          width: `${ring.value.width}px`,
          height: `${ring.value.height}px`,
        }
      : {},
  )

  return {
    scrimRects: computed(() => rects.value),
    ringStyle,
    cardStyle,
    placement,
    anchorMiss,
    refresh: schedule,
    follow,
  }
}
