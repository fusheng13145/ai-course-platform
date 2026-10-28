<template>
  <header class="page-head">
    <div class="page-head-main">
      <p class="page-kicker">
        {{ kicker }}
        <span v-if="source" class="chip" :class="sourceChip.cls">{{ sourceChip.text }}</span>
      </p>
      <h1>{{ title }}</h1>
      <p class="page-sub">{{ sub }}</p>
    </div>
    <div class="page-head-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{ kicker: string; title: string; sub?: string; source?: 'api' | 'demo' | 'mixed' }>(),
  { sub: '', source: undefined },
)

const sourceChip = computed(() => {
  if (props.source === 'api') return { text: '接口数据', cls: 'chip-ok' }
  if (props.source === 'mixed') return { text: '接口 + 示例', cls: 'chip-info' }
  if (props.source === 'demo') return { text: '示例数据', cls: 'chip-warn' }
  return { text: '', cls: '' }
})
</script>
