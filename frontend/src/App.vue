<template>
  <AppErrorBoundary>
    <router-view />
  </AppErrorBoundary>
</template>

<script setup>
import AppErrorBoundary from '@/components/AppErrorBoundary.vue'
</script>

<script>
export default {
  /* 捕获子组件抛出的错误，阻止继续向上传播到 window.onerror */
  errorCaptured(err, instance, info) {
    console.error('[App ErrorCaptured]', err)
    console.error('[Source]', info)
    console.error('[Component]', instance?.$options?.name || 'Unknown')
    // 返回 false 阻止错误继续向上冒泡
    return false
  }
}
</script>

<style>
html, body, #app {
  margin: 0;
  padding: 0;
  height: 100%;
  font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Microsoft YaHei", sans-serif;
}
</style>
