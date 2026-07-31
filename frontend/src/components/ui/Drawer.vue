<script setup>
const emit = defineEmits(['close'])
function onOverlay(e) { if (e.target === e.currentTarget) emit('close') }
</script>
<template>
  <div class="drawer-overlay" @mousedown.self="onOverlay">
    <div class="drawer" :class="{ wide: $attrs.wide === '' || $attrs.wide === true }">
      <div class="drawer-head">
        <div><div class="modal-title"><slot name="title" /></div><div v-if="$slots.sub" class="modal-sub"><slot name="sub" /></div></div>
        <div style="flex:1"></div>
        <button class="t-icon-btn" @click="emit('close')">
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18M6 6l12 12"/></svg>
        </button>
      </div>
      <div class="drawer-body"><slot /></div>
      <div v-if="$slots.footer" class="drawer-foot"><slot name="footer" /></div>
    </div>
  </div>
</template>
<style scoped>
.drawer-overlay { position: fixed; inset: 0; background: rgba(20,26,38,.38); z-index: var(--z-drawer); }
.drawer { position: fixed; top: 0; right: 0; bottom: 0; width: 480px; max-width: 94vw; background: var(--surface); box-shadow: var(--sh-drawer); z-index: calc(var(--z-drawer) + 1); display: flex; flex-direction: column; }
.drawer.wide { width: 620px; }
.drawer-head { padding: 16px 22px; border-bottom: 1px solid var(--line); display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.drawer-body { flex: 1; overflow-y: auto; padding: 18px 22px; background: var(--surface-2); }
.drawer-foot { padding: 13px 22px; border-top: 1px solid var(--line); display: flex; justify-content: flex-end; gap: 10px; background: var(--surface); }
.modal-title { font-size: 15.5px; font-weight: 600; } .modal-sub { font-size: 12px; color: var(--ink-3); margin-top: 1px; }
.t-icon-btn { width: 32px; height: 32px; border-radius: 8px; border: none; background: transparent; display: grid; place-items: center; cursor: pointer; color: var(--ink-3); transition: all .15s; }
.t-icon-btn:hover { background: var(--surface-3); color: var(--ink); }
</style>
