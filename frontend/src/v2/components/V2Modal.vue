<template>
  <Teleport to="body">
    <div v-if="open" class="modal-layer v2-modal-layer" @click.self="closeOnBackdrop && close()">
      <section
        ref="panel"
        class="v2-modal-panel"
        tabindex="-1"
        :style="{ '--v2-modal-w': width }"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
      >
        <header class="v2-modal-head">
          <div>
            <p class="card-kicker">{{ kicker }}</p>
            <h2>{{ title }}</h2>
          </div>
          <button class="icon-btn" aria-label="关闭" @click="close">
            <V2Icon name="x" />
          </button>
        </header>
        <div class="v2-modal-body">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="v2-modal-foot">
          <slot name="footer" />
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import V2Icon from './V2Icon.vue'
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
    kicker?: string
    width?: string
    closeOnBackdrop?: boolean
  }>(),
  { kicker: 'RECORD / DIALOG', width: '720px', closeOnBackdrop: true },
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

onMounted(() => {
  window.addEventListener('keydown', onKey)
  releaseFocusTrap = trapFocus(panel.value)
  lockViewportScroll()
  announce(`对话框已打开：${props.title}`)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  releaseFocusTrap?.()
  releaseFocusTrap = null
  unlockViewportScroll()
  announce('对话框已关闭')
})
</script>

<style scoped>
.v2-modal-panel {
  width: min(var(--v2-modal-w, 720px), 100%);
  max-height: 92vh;
  overflow-y: auto;
  background: var(--surface);
  border: 1px solid var(--line-strong);
  border-radius: var(--r-lg);
  box-shadow: var(--sh-pop);
  animation: v2-popIn 0.22s var(--ease) both;
}
.v2-modal-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--line);
}
.v2-modal-head h2 {
  margin: 4px 0 0;
  font-family: 'Noto Serif SC', Georgia, serif;
  font-size: 19px;
  font-weight: 600;
}
.v2-modal-body {
  padding: 18px;
}
.v2-modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 18px;
  border-top: 1px solid var(--line);
  background: var(--surface-2);
}
</style>
