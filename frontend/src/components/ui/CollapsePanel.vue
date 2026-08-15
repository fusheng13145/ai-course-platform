<script setup>
import { ref, watch } from 'vue'
const props = defineProps({
  title: { type: String, default: '' },
  desc: { type: String, default: '' },
  icon: { type: String, default: '' },
  open: { type: Boolean, default: true },
  flush: { type: Boolean, default: false },
})
const isOpen = ref(props.open)
watch(
  () => props.open,
  (v) => (isOpen.value = v),
)
const ICON = { chevronR: 'm9 18 6-6-6-6' }
function ico(d) {
  return `<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="${d}"/></svg>`
}
</script>
<template>
  <div class="l2-panel" :class="{ open: isOpen }">
    <div class="l2-head" @click="isOpen = !isOpen">
      <!-- 内部图标常量（受控），无需净化 -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <span
        class="l2-caret"
        :style="{ transform: isOpen ? 'rotate(90deg)' : '' }"
        v-html="ico(ICON.chevronR)"
      ></span>
      <!-- 内部图标常量（受控），无需净化 -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <span
        v-if="icon"
        style="color: var(--accent); display: grid; place-items: center"
        v-html="ico(ICON[icon] || ICON.chevronR)"
      ></span>
      <div>
        <div class="l2-title">{{ title }}</div>
        <div v-if="desc" class="l2-desc">{{ desc }}</div>
      </div>
      <div class="l2-extra no-fold"><slot name="extra" /></div>
    </div>
    <div ref="bodyRef" class="l2-body">
      <div class="l2-body-inner" :class="{ flush }"><slot /></div>
    </div>
  </div>
</template>
<style scoped>
.l2-panel {
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: var(--r-l2);
  margin-bottom: 12px;
  overflow: hidden;
}
.l2-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
.l2-head:hover {
  background: var(--surface-3);
}
.l2-caret {
  transition: transform 0.25s var(--ease-out);
  color: var(--ink-3);
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.l2-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink);
}
.l2-desc {
  font-size: 12px;
  color: var(--ink-3);
}
.l2-extra {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.l2-body {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.35s var(--ease-out);
}
.l2-panel.open .l2-body {
  max-height: none;
}
.l2-body-inner {
  padding: 14px 16px;
  border-top: 1px dashed var(--line-2);
}
.l2-body-inner.flush {
  padding: 0;
  border-top: 1px solid var(--line);
}
</style>
