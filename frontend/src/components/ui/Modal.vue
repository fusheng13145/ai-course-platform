<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
const emit = defineEmits(['close'])
onMounted(() => { document.addEventListener('keydown', onEsc) })
onBeforeUnmount(() => document.removeEventListener('keydown', onEsc))
function onEsc(e) { if (e.key === 'Escape') close() }
function close() { emit('close') }
function onOverlay(e) { if (e.target === e.currentTarget) close() }
</script>
<template>
  <div class="overlay" @mousedown.self="onOverlay">
    <div class="modal" :class="{ wide: $attrs.wide === '' || $attrs.wide === true, xwide: $attrs.xwide === '' || $attrs.xwide === true, narrow: $attrs.narrow === '' || $attrs.narrow === true }" @click.stop>
      <div class="modal-head">
        <div><div class="modal-title"><slot name="title" /></div><div v-if="$slots.sub" class="modal-sub"><slot name="sub" /></div></div>
        <div style="flex:1"></div>
        <button class="t-icon-btn" @click="close">
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18M6 6l12 12"/></svg>
        </button>
      </div>
      <div class="modal-body"><slot /></div>
      <div v-if="$slots.footer" class="modal-foot"><slot name="footer" /></div>
    </div>
  </div>
</template>
<style scoped>
.overlay { position: fixed; inset: 0; background: rgba(20,26,38,.45); backdrop-filter: blur(3px); display: grid; place-items: center; z-index: var(--z-modal); padding: 24px; }
.modal { background: var(--surface); border-radius: var(--r-l1); box-shadow: var(--sh-modal); width: 640px; max-width: 100%; max-height: calc(100vh - 60px); display: flex; flex-direction: column; overflow: hidden; }
.modal.wide { width: 880px; } .modal.xwide { width: 1060px; } .modal.narrow { width: 460px; }
.modal-head { display: flex; align-items: center; gap: 12px; padding: 16px 22px; border-bottom: 1px solid var(--line); flex-shrink: 0; }
.modal-title { font-size: 15.5px; font-weight: 600; } .modal-sub { font-size: 12px; color: var(--ink-3); margin-top: 1px; }
.modal-body { padding: 20px 22px; overflow-y: auto; flex: 1; background: var(--surface-2); }
.modal-foot { padding: 13px 22px; border-top: 1px solid var(--line); display: flex; justify-content: flex-end; gap: 10px; flex-shrink: 0; background: var(--surface); }
.t-icon-btn { width: 32px; height: 32px; border-radius: 8px; border: none; background: transparent; display: grid; place-items: center; cursor: pointer; color: var(--ink-3); transition: all .15s; flex-shrink: 0; }
.t-icon-btn:hover { background: var(--surface-3); color: var(--ink); }
</style>
