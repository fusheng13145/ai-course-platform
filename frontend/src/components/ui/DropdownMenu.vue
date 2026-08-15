<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] }, // [{text, icon?, danger?, onClick?, divider?}]
  x: { type: Number, default: 0 },
  y: { type: Number, default: 0 },
})

const emit = defineEmits(['close'])
const menu = ref(null)

function click(item) {
  emit('close')
  item.onClick?.()
}

function position() {
  if (!menu.value) return
  const r = menu.value.getBoundingClientRect()
  menu.value.style.left = Math.min(props.x, innerWidth - r.width - 10) + 'px'
  menu.value.style.top = Math.min(props.y, innerHeight - r.height - 10) + 'px'
}

onMounted(() => {
  position()
  const handler = (e) => {
    if (menu.value && !menu.value.contains(e.target)) emit('close')
  }
  setTimeout(() => document.addEventListener('mousedown', handler), 0)
  onBeforeUnmount(() => document.removeEventListener('mousedown', handler))
})

const ICONS = {
  eye: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  edit: 'M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z',
  trash: 'M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2',
  check: 'M20 6 9 17l-5-5',
  send: 'm22 2-7 20-4-9-9-4zM22 2 11 13',
  lock: 'M19 11H5a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7a2 2 0 0 0-2-2zM7 11V7a5 5 0 0 1 10 0v4',
  key: 'm21 2-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0 3 3L22 7l-3-3m-3.5 3.5L19 4',
  archive: 'M21 8v13H3V8M1 3h22v5H1zM10 12h4',
  download: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3',
  refresh: 'M3 12a9 9 0 0 1 15.36-6.36L21 8M21 3v5h-5M21 12a9 9 0 0 1-15.36 6.36L3 16M3 21v-5h5',
  bell: 'M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0',
  users:
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2',
  star: 'm12 2 3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z',
}
function iconSvg(name, size = 14) {
  const d = ICONS[name] || ''
  return d
    ? `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="${d}"/></svg>`
    : ''
}
</script>
<template>
  <div ref="menu" class="pop-menu" @click.stop>
    <template v-for="(it, i) in items" :key="i">
      <div v-if="it === '-'" class="menu-divider"></div>
      <div v-else-if="it.label && it.group" class="menu-label">{{ it.label }}</div>
      <div v-else class="menu-item" :class="{ danger: it.danger }" @click="click(it)">
        <!-- 图标 path 取自内部 ICONS 常量表（受控），无需净化 -->
        <!-- eslint-disable-next-line vue/no-v-html -->
        <span v-if="it.icon" class="m-ico" v-html="iconSvg(it.icon)"></span>
        {{ it.text }}
        <span v-if="it.hint" class="m-key">{{ it.hint }}</span>
      </div>
    </template>
  </div>
</template>
<style scoped>
.pop-menu {
  position: fixed;
  min-width: 168px;
  background: var(--surface);
  border-radius: 10px;
  box-shadow: var(--sh-pop);
  border: 1px solid var(--line);
  padding: 5px;
  z-index: var(--z-menu);
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 11px;
  font-size: 13px;
  color: var(--ink-2);
  border-radius: 7px;
  cursor: pointer;
  transition: all 0.12s;
}
.menu-item:hover {
  background: var(--surface-3);
  color: var(--ink);
}
.menu-item.danger {
  color: var(--accent);
}
.menu-item.danger:hover {
  background: var(--accent-soft);
}
.m-ico {
  width: 15px;
  height: 15px;
  display: grid;
  place-items: center;
  color: var(--ink-3);
}
.menu-item.danger .m-ico {
  color: var(--accent);
}
.m-key {
  margin-left: auto;
  font-size: 11px;
  color: var(--ink-4);
}
.menu-divider {
  height: 1px;
  background: var(--line);
  margin: 5px 8px;
}
.menu-label {
  font-size: 10.5px;
  color: var(--ink-4);
  padding: 6px 11px 3px;
  letter-spacing: 0.5px;
}
</style>
