<template>
  <!-- ElConfigProvider（P4-GATE-01，准入门 #3）：EP 弹层计数器起点 2000→2300。
       没有它，EP 弹层 z（2000+ 递增）会盖在自建 modal(2200)/drawer(2250) 之上，
       modal 内的 select 下拉反而被自己的面板压住。2300 起点让 EP 弹层恒在
       原语层之上（modal 内下拉可弹出），又低于引导层 3000——层叠序见
       theme.css z-index 段注释。仅影响 EP 内部计数器，不碰任何自建层。 -->
  <ElConfigProvider :z-index="2300">
    <AppErrorBoundary ref="errorBoundaryRef">
      <router-view />
    </AppErrorBoundary>
  </ElConfigProvider>
</template>

<script setup>
import { ref, onErrorCaptured } from 'vue'
import { ElConfigProvider } from 'element-plus'
import AppErrorBoundary from '@/components/AppErrorBoundary.vue'

const errorBoundaryRef = ref(null)

/**
 * App 级的 errorCaptured 作为最终兜底。
 * 正常情况下，AppErrorBoundary 组件内的 onErrorCaptured 会先捕获子组件错误。
 * 此处作为最终安全网，确保没有任何错误会静默丢失。
 * 返回 false 阻止错误继续向上传播到 window.onerror（避免浏览器默认错误页面闪烁）。
 */
onErrorCaptured((err, instance, info) => {
  console.error('[App ErrorCaptured]', err)
  console.error('[Source]', info)
  console.error('[Component]', instance?.$options?.name || instance?.type?.name || 'Unknown')
  // 若错误边界组件已存在实例，将错误传递给它的 UI 展示
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
