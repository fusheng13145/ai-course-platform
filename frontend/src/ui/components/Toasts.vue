<template>
  <div class="toast-stack" aria-live="polite">
    <div v-for="item in toasts" :key="item.id" class="toast">{{ item.message }}</div>
  </div>
</template>

<script setup lang="ts">
import { useUi } from '../composables/ui'

const { toasts } = useUi()
</script>
