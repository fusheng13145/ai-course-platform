import { ref, computed, onBeforeUnmount } from 'vue'
import { computeCardPlacement, computeScrimRects } from '@/onboarding/geometry'

const FALLBACK = { width: 320, height: 245 }
const PAD = 6

/**
 * 只负责几何：不认锚点键、不读存储、不渲染卡片。
 * getTarget() 由调用方给（Task 3 里它接 anchors.resolveAnchor）。
 */
export function useSpotlightOverlay({ getTarget, cardRef, onAnchorMiss }) {
  const rects = ref([])
  const ring = ref(null)
  const cardStyle = ref({})
  const placement = ref('centered')
  const anchorMiss = ref(false)
  let raf = 0

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
    if (raf) return
    raf = requestAnimationFrame(refresh)
  }

  /** 改前只读 rect、从不滚动 ⇒ 目标在视口外时聚光框跑到屏外 */
  function follow(el) {
    el?.scrollIntoView?.({ block: 'center', inline: 'center', behavior: 'smooth' })
    window.setTimeout(schedule, 320)
  }

  window.addEventListener('resize', schedule)
  window.addEventListener('scroll', schedule, true)
  onBeforeUnmount(() => {
    if (raf) cancelAnimationFrame(raf)
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

  return { scrimRects: computed(() => rects.value), ringStyle, cardStyle, placement, anchorMiss, refresh: schedule, follow }
}
