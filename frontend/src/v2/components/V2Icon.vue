<template>
  <svg class="icon" :class="cls" aria-hidden="true">
    <use :href="`#i-${name}`" />
  </svg>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ name: string; cls?: string }>(), { cls: '' })
</script>
