<script setup>
import { ref, computed } from 'vue'
const props = defineProps({
  nodes: { type: Array, required: true },
  selectedKey: { type: String, default: '' },
  depth: { type: Number, default: 0 },
  opsMenu: { type: Function, default: null },
})
const emit = defineEmits(['select'])
const expanded = ref(new Set())
const ICONS = {
  chevronR: 'm9 18 6-6-6-6',
  folder:
    'M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z',
  file: 'M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2zM14 2v6h6',
  more: 'M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM19 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM5 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2z',
}
function ico(d, s = 14) {
  return `<svg viewBox="0 0 24 24" width="${s}" height="${s}" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="${d}"/></svg>`
}
function toggle(key) {
  const s = new Set(expanded.value)
  s.has(key) ? s.delete(key) : s.add(key)
  expanded.value = s
}
function isOpen(key) {
  return expanded.value.has(key)
}
function selectNode(n) {
  emit('select', n)
}
function hasKids(n) {
  return n.children && n.children.length > 0
}
const sel = computed(() => props.selectedKey)
</script>
<template>
  <div class="tree">
    <template v-for="n in nodes" :key="n.key">
      <div class="tree-node">
        <div class="tree-row" :class="{ selected: sel === n.key }" @click.stop="selectNode(n)">
          <!-- 内部图标常量（受控），无需净化 -->
          <!-- eslint-disable-next-line vue/no-v-html -->
          <span
            class="tree-caret"
            :class="{ open: isOpen(n.key), leaf: !hasKids(n) }"
            @click.stop="toggle(n.key)"
            v-html="ico(ICONS.chevronR, 12)"
          ></span>
          <!-- 内部图标常量（受控），无需净化 -->
          <!-- eslint-disable-next-line vue/no-v-html -->
          <span
            class="tree-ico"
            :style="{ color: sel === n.key ? 'var(--accent)' : '' }"
            v-html="ico(n.icon || (hasKids(n) ? 'folder' : 'file'), 14)"
          ></span>
          <span class="tree-label" :title="n.label">{{ n.label }}</span>
          <span v-if="n.count != null" class="tree-count">{{ n.count }}</span>
          <span
            v-if="n.tag"
            class="tag"
            :class="n.tagCls"
            style="font-size: 10px; padding: 0 6px"
            >{{ n.tag }}</span
          >
          <!-- 内部图标常量（受控），无需净化 -->
          <!-- eslint-disable-next-line vue/no-v-html -->
          <span v-if="n.ops || opsMenu" class="tree-extra"
            ><button
              class="t-icon-btn"
              style="width: 22px; height: 22px"
              v-html="ico(ICONS.more, 13)"
            ></button
          ></span>
        </div>
        <div v-if="hasKids(n)" class="tree-children" :class="{ open: isOpen(n.key) }">
          <div class="tree-indent">
            <TreeView
              :nodes="n.children"
              :selected-key="sel"
              :depth="depth + 1"
              :ops-menu="opsMenu"
              @select="emit('select', $event)"
            />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
<style scoped>
.tree {
  font-size: 13px;
  user-select: none;
}
.tree-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 8px;
  border-radius: 7px;
  cursor: pointer;
  color: var(--ink-2);
  transition: background 0.13s;
  position: relative;
}
.tree-row:hover {
  background: var(--surface-3);
}
.tree-row.selected {
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-weight: 500;
}
.tree-caret {
  width: 16px;
  height: 16px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  color: var(--ink-3);
  transition: transform 0.2s var(--ease-out);
  border-radius: 4px;
}
.tree-caret.open {
  transform: rotate(90deg);
}
.tree-caret.leaf {
  visibility: hidden;
}
.tree-ico {
  width: 16px;
  height: 16px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  color: var(--ink-3);
}
.tree-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tree-children {
  overflow: hidden;
  max-height: 0;
  transition: max-height 0.3s var(--ease-out);
}
.tree-children.open {
  max-height: none;
}
.tree-indent {
  margin-left: 15px;
  padding-left: 10px;
  border-left: 1px dashed var(--line-2);
}
.tree-count {
  font-size: 10.5px;
  color: var(--ink-4);
  background: var(--surface-3);
  border-radius: 999px;
  padding: 0 6px;
  line-height: 16px;
}
.tree-row.selected .tree-count {
  background: #fff;
  color: var(--accent);
}
.tree-extra {
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}
.tree-row:hover .tree-extra {
  opacity: 1;
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
}
</style>
