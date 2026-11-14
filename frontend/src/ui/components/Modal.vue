<template>
  <Teleport to="body">
    <div v-if="open" class="modal-layer modal-layer" @click.self="closeOnBackdrop && close()">
      <section
        ref="panel"
        class="modal-panel"
        tabindex="-1"
        :style="{ '--modal-w': width }"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
      >
        <header class="modal-head">
          <div>
            <h2>{{ title }}</h2>
          </div>
          <button class="icon-btn" aria-label="关闭" @click="close">
            <Icon name="x" />
          </button>
        </header>
        <div class="modal-body">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="modal-foot">
          <slot name="footer" />
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Icon from './Icon.vue'
import {
  announce,
  lockViewportScroll,
  trapFocus,
  unlockViewportScroll,
} from '../composables/useOverlayA11y'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    width?: string
    closeOnBackdrop?: boolean
  }>(),
  { width: '720px', closeOnBackdrop: true },
)

const emit = defineEmits<{ (event: 'update:open', value: boolean): void; (event: 'close'): void }>()

function close() {
  emit('update:open', false)
  emit('close')
}

function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) close()
}

const panel = ref<HTMLElement | null>(null)
let releaseFocusTrap: (() => void) | null = null
// 滚动锁必须与 open 绑定，而不是与组件挂载绑定：
// 之前的实现是 onMounted 里无条件 lock、onBeforeUnmount 才 unlock，
// 于是「挂载了但从未打开」的 Modal（页面上很常见）会把 body 的 overflow 永久钉成 hidden，
// 整页再也滚不动——而且因为引用计数一直 ≥1，任何后开的弹层关掉都解不开。
let scrollLocked = false

function syncOverlay(open: boolean) {
  if (open === scrollLocked) return
  scrollLocked = open
  if (open) {
    releaseFocusTrap = trapFocus(panel.value)
    lockViewportScroll()
    announce(`对话框已打开：${props.title}`)
  } else {
    releaseFocusTrap?.()
    releaseFocusTrap = null
    unlockViewportScroll()
    announce('对话框已关闭')
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKey)
  // 首屏即 open 时（少数场景）也要正确加锁
  syncOverlay(props.open)
})

watch(
  () => props.open,
  (value) => syncOverlay(value),
  // post：等面板真正挂进 DOM 再抓焦点，pre 阶段 panel 还是 null，焦点陷阱会落空
  { flush: 'post' },
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  releaseFocusTrap?.()
  releaseFocusTrap = null
  if (scrollLocked) {
    unlockViewportScroll()
    scrollLocked = false
    // 调用方常用 v-if 直接卸载弹层（父组件把 open 置 false 的同时就不渲染了），
    // 这条路走不到 watch，关闭播报与解锁都得在这里补
    announce('对话框已关闭')
  }
})
</script>

<style scoped>
.modal-panel {
  display: flex;
  flex-direction: column;
  width: min(var(--modal-w, 720px), 100%);
  max-height: 92vh;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--line-strong);
  border-radius: var(--r-lg);
  box-shadow: var(--sh-pop);
  animation: popIn 0.22s var(--ease) both;
}
.modal-head {
  display: flex;
  flex: none;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--line);
}
.modal-head h2 {
  margin: 4px 0 0;
  font-family: 'Noto Serif SC', Georgia, serif;
  font-size: 19px;
  font-weight: 600;
}
.modal-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
}
.modal-foot {
  display: flex;
  flex: none;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 20px;
  border-top: 1px solid var(--line);
  background: var(--surface-2);
}
</style>
