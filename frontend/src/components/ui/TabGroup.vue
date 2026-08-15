<script setup>
import { ref, watch } from 'vue'
import { sanitizeHtml } from '@/utils/sanitize'

const props = defineProps({
  tabs: { type: Array, required: true }, // [{key, label, icon, badge}]
  active: { type: String, default: '' },
  style: { type: String, default: 'pill' }, // pill | line | seg
})
const emit = defineEmits(['change'])
const cur = ref(props.active)
watch(
  () => props.active,
  (v) => (cur.value = v),
)
function select(key) {
  cur.value = key
  emit('change', key)
}
</script>
<template>
  <div :class="`tabs-${style}`">
    <div
      v-for="t in tabs"
      :key="t.key"
      class="tab-i"
      :class="{ active: cur === t.key }"
      @click="select(t.key)"
    >
      <!-- icon 来自外部 props，先经 sanitizeHtml 清洗 -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <span v-if="t.icon" v-html="sanitizeHtml(t.icon)"></span>
      {{ t.label }}
      <span v-if="t.badge != null" class="tab-badge">{{ t.badge }}</span>
    </div>
  </div>
</template>
<style scoped>
.tabs-line {
  display: flex;
  gap: 2px;
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}
.tabs-line .tab-i {
  padding: 12px 16px;
  font-size: 13.5px;
  color: var(--ink-2);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  user-select: none;
  display: flex;
  align-items: center;
  gap: 7px;
  transition: color 0.15s;
  white-space: nowrap;
}
.tabs-line .tab-i:hover {
  color: var(--ink);
}
.tabs-line .tab-i.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
  font-weight: 600;
}
.tabs-pill {
  display: flex;
  gap: 6px;
  padding: 4px;
  background: var(--surface-3);
  border-radius: 10px;
  width: fit-content;
}
.tabs-pill .tab-i {
  padding: 6px 16px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--ink-2);
  cursor: pointer;
  user-select: none;
  transition: all 0.18s;
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.tabs-pill .tab-i:hover {
  color: var(--ink);
}
.tabs-pill .tab-i.active {
  background: var(--surface);
  color: var(--accent);
  font-weight: 600;
  box-shadow: var(--sh-l2);
}
.tabs-seg {
  display: inline-flex;
  background: var(--surface-3);
  border-radius: 8px;
  padding: 3px;
  gap: 2px;
}
.tabs-seg .tab-i {
  padding: 4px 12px;
  font-size: 12px;
  border-radius: 6px;
  color: var(--ink-3);
  cursor: pointer;
  user-select: none;
  transition: all 0.15s;
  white-space: nowrap;
}
.tabs-seg .tab-i.active {
  background: var(--surface);
  color: var(--ink);
  font-weight: 600;
  box-shadow: var(--sh-l2);
}
.tab-badge {
  font-size: 10px;
  background: var(--surface-3);
  color: var(--ink-3);
  border-radius: 999px;
  padding: 1px 7px;
}
.tab-i.active .tab-badge {
  background: var(--accent-soft);
  color: var(--accent);
}
</style>
