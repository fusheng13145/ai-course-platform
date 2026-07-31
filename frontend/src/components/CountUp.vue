<script setup>
/**
 * 统计数字滚动组件 · 克制动效
 * 进入视口后从 0 缓动到目标值（easeOutCubic），支持小数位 / 前缀 / 后缀；
 * 数据刷新时从当前值平滑过渡到新值。数字使用 --num 等宽数字栈，不跳动。
 * 用法：<CountUp :value="128" suffix="%" :decimals="1" />
 */
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  value: { type: Number, required: true },
  duration: { type: Number, default: 800 },
  decimals: { type: Number, default: 0 },
  prefix: { type: String, default: '' },
  suffix: { type: String, default: '' },
  delay: { type: Number, default: 0 },
})

const display = ref('')
const root = ref(null)
let rafId = null
let observer = null
let current = 0

function format(n) {
  return (
    props.prefix +
    n.toLocaleString('zh-CN', {
      minimumFractionDigits: props.decimals,
      maximumFractionDigits: props.decimals,
    }) +
    props.suffix
  )
}

function animateTo(to) {
  const from = current
  const t0 = performance.now()
  const dur = Math.min(Math.max(props.duration, 200), 1600)
  const tick = (now) => {
    const t = Math.min(1, (now - t0) / dur)
    const eased = 1 - Math.pow(1 - t, 3)
    current = from + (to - from) * eased
    display.value = format(current)
    if (t < 1) rafId = requestAnimationFrame(tick)
    else current = to
  }
  if (rafId) cancelAnimationFrame(rafId)
  rafId = requestAnimationFrame(tick)
}

function dispose() {
  if (observer) { observer.disconnect(); observer = null }
  if (rafId) { cancelAnimationFrame(rafId); rafId = null }
}

onMounted(() => {
  display.value = format(0)
  if (typeof IntersectionObserver === 'undefined') {
    animateTo(props.value)
    return
  }
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) {
        setTimeout(() => animateTo(props.value), props.delay)
        dispose()
      }
    },
    { threshold: 0.3 }
  )
  if (root.value) observer.observe(root.value)
})

onBeforeUnmount(dispose)

watch(
  () => props.value,
  (v) => animateTo(v)
)
</script>

<template>
  <span ref="root" class="count-up">{{ display }}</span>
</template>

<style scoped>
.count-up {
  font-family: var(--num);
  font-variant-numeric: tabular-nums;
}
</style>
