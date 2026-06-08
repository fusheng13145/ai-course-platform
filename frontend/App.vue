<template>
  <router-view />
</template>
<script setup>
import { useTheme } from '@/composables/useTheme'
const { theme } = useTheme()
</script>
<style lang="scss">
#app {
  font-family: var(--fz-font-sans, "Helvetica Neue", Helvetica, "PingFang SC", "Microsoft YaHei", sans-serif);
  -webkit-font-smoothing: antialiased;
  height: 100vh;
  overflow: hidden;
}
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { height: 100%; overflow: hidden; }
</style>
