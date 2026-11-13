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
  defineProps<{ kicker: string; title: string; sub?: string; source?: 'api' }>(),
  { sub: '', source: undefined },
)

const sourceChip = computed(() => {
  // 示例层已整体删除（batch 24）：来源章不再有「样例读数」「接口与样例混排」这类档位。
  if (props.source === 'api') return { text: '接口数据', cls: 'chip-ok' }
  return { text: '', cls: '' }
})
</script>
