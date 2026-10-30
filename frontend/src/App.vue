<template>
  <AppErrorBoundary ref="errorBoundaryRef">
    <router-view />
  </AppErrorBoundary>
</template>

<script setup>
import { ref, onErrorCaptured } from 'vue'
import AppErrorBoundary from '@/components/AppErrorBoundary.vue'

const errorBoundaryRef = ref(null)

// 最终兜底：AppErrorBoundary 先接，这里保证任何漏网错误都不静默
onErrorCaptured((err, instance, info) => {
  console.error('[App ErrorCaptured]', err)
  console.error('[Source]', info)
  console.error('[Component]', instance?.$options?.name || instance?.type?.name || 'Unknown')
  if (errorBoundaryRef.value && typeof errorBoundaryRef.value.captureError === 'function') {
    errorBoundaryRef.value.captureError(err)
  }
  return false
})
</script>

<style>
html,
body,
#app {
  margin: 0;
  padding: 0;
  height: 100%;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
</style>
