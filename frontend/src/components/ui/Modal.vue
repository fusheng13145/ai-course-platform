<script setup>
import { computed, onMounted, onBeforeUnmount } from 'vue'

/**
 * 可见性通道（Task 7）。三态语义：
 * - `undefined`＝不传：**13 个存量调用点（12 个文件）全是这一类**，可见性由外层 `v-if` 决定，
 *   所以「没传」必须继续等于「可见」。
 * - `true`＝开、`false`＝关，供 Phase 4 的 `v-model="visible"` 写法使用。
 *
 * `default: undefined` 是承重的，不能删：Boolean prop 缺省且**没有** default 时，Vue 会把
 * 「没传」强制成 `false`（vue 3.5.43 `resolvePropValue` 的 shouldCast 分支：
 * runtime-core.esm-bundler.js:5140-5145、同一逻辑在 runtime-core.cjs.js:5117-5122。
 * 我不断言 vitest 实际加载哪份构建——变异 M1（删掉 default）会让 Modal.compat.test.ts 的
 * 8 条立刻转红，那才是本机证明该转换真的发生了的证据）。
 * 否则 open 恒为 false，全部老页面一打开就是空白。同 Drawer.vue:27-32（Task 11 撞出来的同一条）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: undefined },
})
// 双通道：`close` 是 13 个存量调用点唯一在听的，`update:modelValue` 是 v-model 的另一条腿。
const emit = defineEmits(['close', 'update:modelValue'])

// 缺省视为 true——false 才是「关」，undefined 不是。
const open = computed(() => props.modelValue !== false)

onMounted(() => {
  document.addEventListener('keydown', onEsc)
})
onBeforeUnmount(() => document.removeEventListener('keydown', onEsc))
function onEsc(e) {
  if (e.key === 'Escape') close()
}
function close() {
  emit('close')
  // 只有调用点真的在用 v-model（modelValue 显式为 true）才回写，
  // 免得给 13 个只 @close 的老调用点发没人听的事件。
  if (props.modelValue === true) emit('update:modelValue', false)
}
function onOverlay(e) {
  if (e.target === e.currentTarget) close()
}
</script>
<template>
  <div v-if="open" class="overlay" @mousedown.self="onOverlay">
    <div
      class="modal"
      :class="{
        wide: $attrs.wide === '' || $attrs.wide === true,
        xwide: $attrs.xwide === '' || $attrs.xwide === true,
        narrow: $attrs.narrow === '' || $attrs.narrow === true,
      }"
      @click.stop
    >
      <div class="modal-head">
        <div>
          <div class="modal-title"><slot name="title" /></div>
          <div v-if="$slots.sub" class="modal-sub"><slot name="sub" /></div>
        </div>
        <div style="flex: 1"></div>
        <button class="t-icon-btn" @click="close">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M18 6 6 18M6 6l12 12" />
          </svg>
        </button>
      </div>
      <div class="modal-body"><slot /></div>
      <div v-if="$slots.footer" class="modal-foot"><slot name="footer" /></div>
    </div>
  </div>
</template>
<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(20, 26, 38, 0.45);
  backdrop-filter: blur(3px);
  display: grid;
  place-items: center;
  z-index: var(--z-modal);
  padding: 24px;
}
.modal {
  background: var(--surface);
  border-radius: var(--r-l1);
  box-shadow: var(--sh-modal);
  width: 640px;
  max-width: 100%;
  max-height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.modal.wide {
  width: 880px;
}
.modal.xwide {
  width: 1060px;
}
.modal.narrow {
  width: 460px;
}
.modal-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 22px;
  border-bottom: 1px solid var(--line);
  flex-shrink: 0;
}
.modal-title {
  font-size: 15.5px;
  font-weight: 600;
}
.modal-sub {
  font-size: 12px;
  color: var(--ink-3);
  margin-top: 1px;
}
.modal-body {
  padding: 20px 22px;
  overflow-y: auto;
  flex: 1;
  background: var(--surface-2);
}
.modal-foot {
  padding: 13px 22px;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-shrink: 0;
  background: var(--surface);
}
.t-icon-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  display: grid;
  place-items: center;
  cursor: pointer;
  color: var(--ink-3);
  transition: all 0.15s;
  flex-shrink: 0;
}
.t-icon-btn:hover {
  background: var(--surface-3);
  color: var(--ink);
}
</style>
